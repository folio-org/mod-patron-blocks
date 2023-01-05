package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.domain.SynchronizationStatus.DONE;
import static org.folio.domain.SynchronizationStatus.FAILED;
import static org.folio.domain.SynchronizationStatus.IN_PROGRESS;
import static org.folio.rest.jaxrs.model.SynchronizationJob.Scope.FULL;
import static org.folio.rest.jaxrs.model.SynchronizationJob.Scope.USER;
import static org.folio.util.LogHelper.logAsJson;
import static org.folio.util.LogHelper.logList;

import java.util.*;
import java.util.stream.Collectors;

import io.vertx.core.CompositeFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.domain.SynchronizationStatus;
import org.folio.exception.EntityNotFoundException;
import org.folio.exception.UserIdNotFoundException;
import org.folio.repository.SynchronizationJobRepository;
import org.folio.repository.UserSummaryRepository;
import org.folio.rest.jaxrs.model.SynchronizationJob;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class SynchronizationJobService {

  private static final Logger log = LogManager.getLogger(SynchronizationJobService.class);

  private final UserSummaryRepository userSummaryRepository;
  private final UserSummaryService userSummaryService;
  private final SynchronizationJobRepository syncRepository;
  private final LoanEventsGenerationService loanEventsGenerationService;
  private final FeesFinesEventsGenerationService feesFinesEventsGenerationService;
  private final EventService eventService;
  private final String tenantId;

  public SynchronizationJobService(Map<String, String> okapiHeaders, Vertx vertx) {
    this.tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient postgresClient = PostgresClient.getInstance(vertx, tenantId);
    this.syncRepository = new SynchronizationJobRepository(postgresClient);
    this.userSummaryRepository = new UserSummaryRepository(postgresClient);
    this.userSummaryService = new UserSummaryService(postgresClient);
    this.eventService = new EventService(postgresClient);
    this.loanEventsGenerationService = new LoanEventsGenerationService(
      okapiHeaders, vertx, syncRepository);
    this.feesFinesEventsGenerationService = new FeesFinesEventsGenerationService(
      okapiHeaders, vertx, syncRepository);
  }

  public Future<SynchronizationJob> createSynchronizationJob(SynchronizationJob request) {
    log.debug("createSynchronizationJob:: parameters request: {}", () -> logAsJson(request));
    if (USER == request.getScope() && request.getUserId() == null) {
      return failedFuture(new UserIdNotFoundException(
        "UserId is required for synchronization job with scope: USER"));
    }

    String syncRecordId = UUID.randomUUID().toString();
    SynchronizationJob entity = new SynchronizationJob()
      .withId(syncRecordId)
      .withStatus(SynchronizationStatus.OPEN.getValue())
      .withScope(request.getScope())
      .withUserId(request.getUserId())
      .withTotalNumberOfLoans(0)
      .withTotalNumberOfFeesFines(0)
      .withNumberOfProcessedLoans(0)
      .withNumberOfProcessedFeesFines(0);

    return syncRepository.save(entity)
      .map(id -> new SynchronizationJob()
        .withId(id)
        .withScope(entity.getScope())
        .withStatus(entity.getStatus()))
      .onSuccess(result -> log.info("createSynchronizationJob:: result: {}",
        () -> logAsJson(result)));
  }

  public Future<SynchronizationJob> getSynchronizationJob(String syncRequestId) {
    log.debug("getSynchronizationJob:: parameters syncRequestId: {}", syncRequestId);
    return syncRepository.get(syncRequestId)
      .compose(optionalSyncResponse -> optionalSyncResponse.map(Future::succeededFuture)
        .orElseGet(() -> failedFuture(new EntityNotFoundException(
          "This synchronization request does not exist"))))
      .onSuccess(result -> log.info("getSynchronizationJob:: result: {}",
        () -> logAsJson(result)));
  }

  public Future<SynchronizationJob> runSynchronization() {
    log.debug("runSynchronization:: no parameters");
    return syncRepository.getJobsByStatus(IN_PROGRESS)
      .compose(this::doSynchronization)
      .onSuccess(result -> log.info("runSynchronization:: result: {}", () -> logAsJson(result)));
  }

  private Future<SynchronizationJob> doSynchronization(
    List<SynchronizationJob> inProgressSynchronizationJobs) {

    log.debug("doSynchronization:: parameters inProgressSynchronizationJobs: {}",
      () -> logList(inProgressSynchronizationJobs));

    if (!inProgressSynchronizationJobs.isEmpty()) {
      log.debug("doSynchronization:: Synchronization is in-progress now");
      return succeededFuture();
    }

    return syncRepository.getTheOldestSyncRequest(tenantId)
      .compose(this::doSynchronization)
      .onSuccess(result -> log.info("doSynchronization:: result: {}", () -> logAsJson(result)));
  }

  private Future<SynchronizationJob> doSynchronization(SynchronizationJob synchronizationJob) {
    log.debug("doSynchronization:: parameters synchronizationJob: {}",
      logAsJson(synchronizationJob));

    return updateJobStatus(synchronizationJob, IN_PROGRESS)
      .compose(syncJob -> cleanExistingEvents(syncJob, tenantId))
      .compose(loanEventsGenerationService::generateEvents)
      .compose(feesFinesEventsGenerationService::generateEvents)
      .compose(this::deleteUserSummaries)
      .compose(this::rebuildUserSummaries)
      .compose(job -> updateJobStatus(job, DONE))
      .recover(t -> updateJobAsFailed(synchronizationJob, t.getLocalizedMessage()))
      .onSuccess(result -> log.info("doSynchronization:: result: {}", () -> logAsJson(result)));
  }

  private Future<SynchronizationJob> deleteUserSummaries(SynchronizationJob job) {
    log.debug("deleteUserSummaries:: parameters job: {}", () -> logAsJson(job));
    if (job.getScope() == FULL) {
      log.debug("deleteUserSummaries:: scope: {}", FULL);
      return userSummaryRepository.removeAll(tenantId)
        .map(job);
    }
    else if (job.getScope() == USER) {
      log.debug("deleteUserSummaries:: scope: {}", USER);
      return userSummaryRepository.deleteByUserId(job.getUserId())
        .map(job);
    }
    else {
      log.debug("deleteUserSummaries:: scope: unknown");
      return succeededFuture(job);
    }
  }

  private Future<SynchronizationJob> rebuildUserSummaries(SynchronizationJob job) {
    log.debug("rebuildUserSummaries:: parameters job: {}", () -> logAsJson(job));
    Set<String> userIds = new HashSet<>();
    userIds.addAll(loanEventsGenerationService.getUserIds());
    userIds.addAll(feesFinesEventsGenerationService.getUserIds());

    return CompositeFuture.all(userIds.stream()
      .filter(Objects::nonNull)
      .map(userSummaryService::rebuild)
      .collect(Collectors.toList()))
      .map(job)
      .onSuccess(result -> log.info("rebuildUserSummaries:: result: {}",
        () -> logAsJson(result)));

  }

  private Future<SynchronizationJob> cleanExistingEvents(SynchronizationJob syncJob,
    String tenantId) {

    log.debug("cleanExistingEvents:: parameters syncJob: {}, tenantId: {}",
      () -> logAsJson(syncJob), () -> tenantId);
    return (syncJob.getScope() == FULL
      ? eventService.removeAllEvents(tenantId)
      : eventService.removeAllEventsForUser(tenantId, syncJob.getUserId()))
      .map(syncJob)
      .onSuccess(result -> log.info("cleanExistingEvents:: result: {}", () -> logAsJson(result)));
  }

  private Future<SynchronizationJob> updateJobAsFailed(SynchronizationJob syncJob,
    String errorMessage) {

    log.debug("updateJobAsFailed:: parameters syncJob: {}, errorMessage: {}",
      () -> logAsJson(syncJob), () -> errorMessage);
    syncJob.getErrors().add(errorMessage);

    return updateJobStatus(syncJob, FAILED);
  }

  private Future<SynchronizationJob> updateJobStatus(SynchronizationJob job,
    SynchronizationStatus syncStatus) {

    log.debug("updateJobStatus:: parameters job: {}, syncStatus: {}", () -> logAsJson(job),
      () -> syncStatus);
    return syncRepository.update(job.withStatus(syncStatus.getValue()))
      .onSuccess(r -> log.info("Synchronization job status updated: {}", syncStatus::getValue))
      .onFailure(t -> log.error("Failed to update synchronization job status", t))
      .map(job)
      .onSuccess(result -> log.info("updateJobStatus:: result: {}", () -> logAsJson(result)));
  }
}
