package org.folio.rest.handlers;

import static java.lang.String.format;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.folio.domain.Event;
import org.folio.exception.EntityNotFoundException;
import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.jaxrs.model.UserSummary;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.service.UserSummaryService;

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
    return eventService.save(event)
      .compose(eventId -> getUserSummary(event))
      .compose(userSummary -> userSummaryService.processEvent(userSummary, event))
      .onComplete(result -> logResult(result, event));
  }

  private Future<UserSummary> getUserSummary(FeeFineBalanceChangedEvent event) {
    return event.getUserId() != null
      ? userSummaryRepository.findByUserIdOrBuildNew(event.getUserId())
      : findSummaryByFeeFineIdOrFail(event.getFeeFineId());
  }

  private Future<UserSummary> findSummaryByFeeFineIdOrFail(String feeFineId) {
    return userSummaryRepository.findByFeeFineId(feeFineId)
      .map(summary -> summary.orElseThrow(() -> new EntityNotFoundException(
        format("User summary with fee/fine %s was not found, event is ignored", feeFineId))));
  }

}
