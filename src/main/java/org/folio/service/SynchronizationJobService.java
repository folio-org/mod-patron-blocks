package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.domain.SynchronizationStatus.DONE;
import static org.folio.domain.SynchronizationStatus.FAILED;
import static org.folio.domain.SynchronizationStatus.IN_PROGRESS;
import static org.folio.rest.jaxrs.model.SynchronizationJob.Scope.FULL;
import static org.folio.rest.jaxrs.model.SynchronizationJob.Scope.USER;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.domain.SynchronizationStatus;
import org.folio.exception.EntityNotFoundException;
import org.folio.exception.UserIdNotFoundException;
import org.folio.repository.SynchronizationJobRepository;
import org.folio.rest.jaxrs.model.SynchronizationJob;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class SynchronizationJobService {

  private static final Logger log = LogManager.getLogger(SynchronizationJobService.class);

  private final SynchronizationJobRepository syncRepository;
  private final LoanEventsGenerationService loanEventsGenerationService;
  private final FeesFinesEventsGenerationService feesFinesEventsGenerationService;
  private final EventService eventService;
  private final String tenantId;

  public SynchronizationJobService(Map<String, String> okapiHeaders, Vertx vertx) {
    this.tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient postgresClient = PostgresClient.getInstance(vertx, tenantId);
    this.syncRepository = new SynchronizationJobRepository(postgresClient);
    this.eventService = new EventService(postgresClient);
    this.loanEventsGenerationService = new LoanEventsGenerationService(
      okapiHeaders, vertx, syncRepository);
    this.feesFinesEventsGenerationService = new FeesFinesEventsGenerationService(
      okapiHeaders, vertx, syncRepository);
  }

  public Future<SynchronizationJob> createSynchronizationJob(SynchronizationJob request) {
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
        .withStatus(entity.getStatus()));
  }

  public Future<SynchronizationJob> getSynchronizationJob(String syncRequestId) {
    return syncRepository.get(syncRequestId)
      .compose(optionalSyncResponse -> {
        if (optionalSyncResponse.isPresent()) {
          return succeededFuture(optionalSyncResponse.get());
        }
        return failedFuture(new EntityNotFoundException(
          "This synchronization request does not exist"));
      });
  }

  public Future<SynchronizationJob> runSynchronization() {
     return syncRepository.getJobsByStatus(IN_PROGRESS)
       .compose(this::doSynchronization);
  }

  private Future<SynchronizationJob> doSynchronization(
    List<SynchronizationJob> inProgressSynchronizationJobs) {

    if (!inProgressSynchronizationJobs.isEmpty()) {
      log.debug("Synchronization is in-progress now");
      return succeededFuture();
    }

    return syncRepository.getTheOldestSyncRequest(tenantId)
      .compose(this::doSynchronization);
  }

  private Future<SynchronizationJob> doSynchronization(SynchronizationJob synchronizationJob) {
    return updateJobStatus(synchronizationJob, IN_PROGRESS)
      .compose(syncJob -> cleanExistingEvents(syncJob, tenantId))
      .compose(loanEventsGenerationService::generateEvents)
      .compose(feesFinesEventsGenerationService::generateEvents)
      .compose(job -> updateJobStatus(job, DONE))
      .recover(t -> updateJobAsFailed(synchronizationJob, t.getLocalizedMessage()));
  }

  private Future<SynchronizationJob> cleanExistingEvents(SynchronizationJob syncJob,
    String tenantId) {

    return (syncJob.getScope() == FULL
      ? eventService.removeAllEvents(tenantId)
      : eventService.removeAllEventsForUser(tenantId, syncJob.getUserId()))
      .map(syncJob);
  }

  private Future<SynchronizationJob> updateJobAsFailed(SynchronizationJob syncJob,
    String errorMessage) {

    syncJob.getErrors().add(errorMessage);

    return updateJobStatus(syncJob, FAILED);
  }

  private Future<SynchronizationJob> updateJobStatus(SynchronizationJob job,
    SynchronizationStatus syncStatus) {

    return syncRepository.update(job.withStatus(syncStatus.getValue()))
      .onSuccess(r -> log.info("Synchronization job status updated: " + syncStatus.getValue()))
      .onFailure(t -> log.error("Failed to update synchronization job status: " + t.getMessage()))
      .map(job);
  }
}
