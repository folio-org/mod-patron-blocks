package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.util.function.Predicate.not;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;
import static org.folio.util.LogUtil.asJson;
import static org.folio.util.LogUtil.headersAsString;
import static org.folio.util.LogUtil.loggingResponseHandler;
import static org.folio.util.UuidUtil.isUuid;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exception.EntityNotFoundException;
import org.folio.exception.UserIdNotFoundException;
import org.folio.rest.jaxrs.model.AutomatedPatronBlocksBatchRequest;
import org.folio.rest.jaxrs.model.SynchronizationJob;
import org.folio.rest.jaxrs.resource.AutomatedPatronBlocks;
import org.folio.service.PatronBlocksService;
import org.folio.service.SynchronizationJobService;
import org.folio.util.UuidUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class AutomatedPatronBlocksAPI implements AutomatedPatronBlocks {

  private static final Logger log = LogManager.getLogger(AutomatedPatronBlocksAPI.class);
  private static final String INVALID_USER_IDS_ERROR_MESSAGE = "User IDs must match UUID format";

  @Override
  public void getAutomatedPatronBlocksByUserId(String userId,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    log.debug("getAutomatedPatronBlocksByUserId:: parameters userId: {}, okapiHeaders: {}",
      () -> userId, () -> headersAsString(okapiHeaders));

    if (!isUuid(userId)) {
      log.debug("getAutomatedPatronBlocksByUserId:: User ID {} is not a UUID", userId);
      asyncResultHandler.handle(succeededFuture(GetAutomatedPatronBlocksByUserIdResponse
        .respond400WithTextPlain(format("Invalid user UUID: \"%s\"", userId))));
      return;
    }

    Handler<AsyncResult<Response>> loggingResponseHandler =
      loggingResponseHandler("getAutomatedPatronBlocksByUserId", asyncResultHandler, log);

    new PatronBlocksService(okapiHeaders, vertxContext.owner())
      .getBlocksForUser(userId)
      .onSuccess(blocks -> loggingResponseHandler.handle(succeededFuture(
        GetAutomatedPatronBlocksByUserIdResponse.respond200WithApplicationJson(blocks))))
      .onFailure(failure -> loggingResponseHandler.handle(succeededFuture(
        GetAutomatedPatronBlocksByUserIdResponse.respond500WithTextPlain(
          failure.getLocalizedMessage()))));
  }

  @Override
  public void postAutomatedPatronBlocksBatch(AutomatedPatronBlocksBatchRequest entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    log.debug("postAutomatedPatronBlocksBatch:: parameters entity: {}, okapiHeaders: {}",
      () -> asJson(entity), () -> headersAsString(okapiHeaders));

    Handler<AsyncResult<Response>> loggingResponseHandler =
      loggingResponseHandler("postAutomatedPatronBlocksBatch", asyncResultHandler, log);

    // RMB does not validate UUID format for collection elements, so validate manually
    if (entity.getUserIds() != null) {
      Set<String> invalidUserIds = entity.getUserIds()
        .stream()
        .filter(not(UuidUtil::isUuid))
        .collect(Collectors.toSet());

      if (!invalidUserIds.isEmpty()) {
        log.warn("postAutomatedPatronBlocksBatch:: {}: {}", INVALID_USER_IDS_ERROR_MESSAGE, invalidUserIds);
        loggingResponseHandler.handle(succeededFuture(
          PostAutomatedPatronBlocksBatchResponse.respond422WithApplicationJson(
            createValidationErrorMessage("userIds", invalidUserIds.toString(), INVALID_USER_IDS_ERROR_MESSAGE))));
        return;
      }
    }

    loggingResponseHandler.handle(succeededFuture(
      PostAutomatedPatronBlocksBatchResponse.respond501WithTextPlain("Not implemented")));
  }

  @Override
  public void postAutomatedPatronBlocksSynchronizationJob(SynchronizationJob request,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    log.debug("postAutomatedPatronBlocksSynchronizationJob:: parameters request: {}, okapiHeaders: {}",
      () -> asJson(request), () -> headersAsString(okapiHeaders));

    Handler<AsyncResult<Response>> loggingResponseHandler =
      loggingResponseHandler("postAutomatedPatronBlocksSynchronizationJob", asyncResultHandler, log);

    new SynchronizationJobService(okapiHeaders, vertxContext.owner())
      .createSynchronizationJob(request)
      .onSuccess(response -> loggingResponseHandler.handle(succeededFuture(
        PostAutomatedPatronBlocksSynchronizationJobResponse
          .respond201WithApplicationJson(response))))
      .onFailure(throwable -> {
        String errorMessage = throwable.getLocalizedMessage();
        log.warn("postAutomatedPatronBlocksSynchronizationJob:: Failed to create synchronization job",
          throwable);
        if (throwable instanceof UserIdNotFoundException) {
          asyncResultHandler.handle(succeededFuture(
            PostAutomatedPatronBlocksSynchronizationJobResponse.respond422WithTextPlain(
              errorMessage)));
        } else {
          asyncResultHandler.handle(succeededFuture(
            PostAutomatedPatronBlocksSynchronizationJobResponse
              .respond500WithTextPlain(errorMessage)));
        }
      });
  }

  @Override
  public void getAutomatedPatronBlocksSynchronizationJobBySyncJobId(String syncRequestId,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    log.debug("getAutomatedPatronBlocksSynchronizationJobBySyncJobId:: " +
        "parameters syncRequestId: {}, okapiHeaders: {}", () -> syncRequestId,
      () -> headersAsString(okapiHeaders));

    Handler<AsyncResult<Response>> loggingResponseHandler =
      loggingResponseHandler("getAutomatedPatronBlocksSynchronizationJobBySyncJobId",
        asyncResultHandler, log);

    new SynchronizationJobService(okapiHeaders, vertxContext.owner())
      .getSynchronizationJob(syncRequestId)
      .onSuccess(response ->
        loggingResponseHandler.handle(succeededFuture(
            GetAutomatedPatronBlocksSynchronizationJobBySyncJobIdResponse
              .respond200WithApplicationJson(response))))
      .onFailure(throwable -> {
        String errorMessage = throwable.getLocalizedMessage();
        log.warn("getAutomatedPatronBlocksSynchronizationJobBySyncJobId:: Failed to create synchronization job",
          throwable);
        if (throwable instanceof EntityNotFoundException) {
          loggingResponseHandler.handle(succeededFuture(
            GetAutomatedPatronBlocksSynchronizationJobBySyncJobIdResponse
              .respond404WithTextPlain(errorMessage)));
        } else {
          loggingResponseHandler.handle(succeededFuture(
            GetAutomatedPatronBlocksSynchronizationJobBySyncJobIdResponse
              .respond500WithTextPlain(errorMessage)));
        }
      });
  }

  @Override
  public void postAutomatedPatronBlocksSynchronizationStart(Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    loggingResponseHandler("postAutomatedPatronBlocksSynchronizationStart", asyncResultHandler, log)
      .handle(succeededFuture(PostAutomatedPatronBlocksSynchronizationStartResponse.respond202()));

    vertxContext.owner().executeBlocking(() ->
      new SynchronizationJobService(okapiHeaders, vertxContext.owner())
        .runSynchronization()
        .onComplete(response -> {
          if (response.failed()) {
            log.warn("postAutomatedPatronBlocksSynchronizationStart:: synchronization failed",
              response.cause());
          } else {
            log.info("postAutomatedPatronBlocksSynchronizationStart:: synchronization completed");
          }
        })
    );
  }
}
