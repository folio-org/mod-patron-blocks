package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static org.folio.util.UuidUtil.isUuid;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exception.EntityNotFoundException;
import org.folio.exception.UserIdNotFoundException;
import org.folio.rest.jaxrs.model.SynchronizationJob;
import org.folio.rest.jaxrs.resource.AutomatedPatronBlocks;
import org.folio.service.PatronBlocksService;
import org.folio.service.SynchronizationJobService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class AutomatedPatronBlocksAPI implements AutomatedPatronBlocks {

  private static final Logger log = LogManager.getLogger(AutomatedPatronBlocksAPI.class);

  @Override
  public void getAutomatedPatronBlocksByUserId(String userId,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    if (!isUuid(userId)) {
      asyncResultHandler.handle(succeededFuture(GetAutomatedPatronBlocksByUserIdResponse
        .respond400WithTextPlain(format("Invalid user UUID: \"%s\"", userId))));
      return;
    }

    new PatronBlocksService(okapiHeaders, vertxContext.owner())
      .getBlocksForUser(userId)
      .onSuccess(blocks -> asyncResultHandler.handle(succeededFuture(
        GetAutomatedPatronBlocksByUserIdResponse.respond200WithApplicationJson(blocks))))
      .onFailure(failure -> asyncResultHandler.handle(succeededFuture(
        GetAutomatedPatronBlocksByUserIdResponse.respond500WithTextPlain(
          failure.getLocalizedMessage()))));
  }

  @Override
  public void postAutomatedPatronBlocksSynchronizationJob(SynchronizationJob request,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new SynchronizationJobService(okapiHeaders, vertxContext.owner())
      .createSynchronizationJob(request)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(
        PostAutomatedPatronBlocksSynchronizationJobResponse
          .respond201WithApplicationJson(response))))
      .onFailure(throwable -> {
        String errorMessage = throwable.getLocalizedMessage();
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

    new SynchronizationJobService(okapiHeaders, vertxContext.owner())
      .getSynchronizationJob(syncRequestId)
      .onSuccess(response ->
          asyncResultHandler.handle(succeededFuture(
            GetAutomatedPatronBlocksSynchronizationJobBySyncJobIdResponse
              .respond200WithApplicationJson(response))))
      .onFailure(throwable -> {
        String errorMessage = throwable.getLocalizedMessage();
        if (throwable instanceof EntityNotFoundException) {
          asyncResultHandler.handle(succeededFuture(
            GetAutomatedPatronBlocksSynchronizationJobBySyncJobIdResponse
              .respond404WithTextPlain(errorMessage)));
        } else {
          GetAutomatedPatronBlocksSynchronizationJobBySyncJobIdResponse
            .respond500WithTextPlain(errorMessage);
        }
      });
  }

  @Override
  public void postAutomatedPatronBlocksSynchronizationStart(Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(succeededFuture(
      PostAutomatedPatronBlocksSynchronizationStartResponse.respond202()));

    vertxContext.owner().executeBlocking(promise ->
      new SynchronizationJobService(okapiHeaders, vertxContext.owner())
        .runSynchronization()
        .onComplete(v -> promise.complete()),
      response -> {
        if (response.failed()) {
          log.error("Synchronization error processing");
        } else {
          log.info("Synchronization has been completed");
        }
    });
  }
}
