package org.folio.rest.handlers;

import static java.lang.String.format;
import static org.folio.util.LogUtil.asJson;

import java.util.Map;

import org.folio.exception.EntityNotFoundException;
import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class FeeFineBalanceChangedEventHandler extends EventHandler<FeeFineBalanceChangedEvent> {

  public FeeFineBalanceChangedEventHandler(Map<String, String> okapiHeaders, Vertx vertx) {
    super(okapiHeaders, vertx);
  }

  public FeeFineBalanceChangedEventHandler(PostgresClient postgresClient) {
    super(postgresClient);
  }

  @Override
  public Future<String> handle(FeeFineBalanceChangedEvent event) {
    log.debug("handle:: parameters event: {}", () -> asJson(event));
    return resolveUserId(event)
      .map(userId -> withResolvedUserId(event, userId))
      .compose(eventWithUserId -> eventService.save(eventWithUserId)
        .compose(eventId -> userSummaryService.updateUserSummaryWithEventUsingLock(
          eventWithUserId.getUserId(), eventWithUserId)))
      .onComplete(result -> logResult(result, event));
  }

  private FeeFineBalanceChangedEvent withResolvedUserId(FeeFineBalanceChangedEvent event,
    String resolvedUserId) {

    if (event.getUserId() == null) {
      event.setUserId(resolvedUserId);
    }

    return event;
  }

  private Future<String> resolveUserId(FeeFineBalanceChangedEvent event) {
    log.debug("resolveUserId:: parameters event: {}", () -> asJson(event));
    return event.getUserId() != null
      ? Future.succeededFuture(event.getUserId())
      : findUserIdByFeeFineIdOrFail(event.getFeeFineId())
        .onSuccess(r -> log.info("resolveUserId:: result found"));
  }

  private Future<String> findUserIdByFeeFineIdOrFail(String feeFineId) {
    log.debug("findUserIdByFeeFineIdOrFail:: parameters feeFineId: {}", feeFineId);
    return userSummaryRepository.findByFeeFineId(feeFineId)
      .map(summary -> summary.orElseThrow(() -> new EntityNotFoundException(
        format("User summary with fee/fine %s was not found, event is ignored", feeFineId))))
      .map(summary -> summary.getUserId());
  }

}
