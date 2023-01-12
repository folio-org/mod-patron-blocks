package org.folio.rest.handlers;

import static org.folio.util.LogUtil.asJson;
import static org.folio.util.PostgresUtils.getPostgresClient;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.domain.Event;
import org.folio.domain.EventType;
import org.folio.repository.UserSummaryRepository;
import org.folio.rest.jaxrs.model.UserSummary;
import org.folio.rest.persist.PostgresClient;
import org.folio.service.EventService;
import org.folio.service.UserSummaryService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class EventHandler<E extends Event> {
  protected static final Logger log = LogManager.getLogger(EventHandler.class);
  protected final UserSummaryRepository userSummaryRepository;
  protected final EventService eventService;
  protected final UserSummaryService userSummaryService;

  public EventHandler(Map<String, String> okapiHeaders, Vertx vertx) {
    PostgresClient postgresClient = getPostgresClient(okapiHeaders, vertx);
    userSummaryRepository = new UserSummaryRepository(postgresClient);
    eventService = new EventService(postgresClient);
    userSummaryService = new UserSummaryService(postgresClient);
  }

  public EventHandler(PostgresClient postgresClient) {
    userSummaryRepository = new UserSummaryRepository(postgresClient);
    eventService = new EventService(postgresClient);
    userSummaryService = new UserSummaryService(postgresClient);
  }

  public Future<String> handle(E event) {
    log.debug("handle:: parameters event: {}", () -> asJson(event));
    return eventService.save(event)
      .compose(eventId -> updateUserSummary(event))
      .onComplete(result -> logResult(result, event));
  }

  public Future<String> handleSkippingUserSummaryUpdate(E event) {
    log.debug("handleSkippingUserSummaryUpdate:: parameters event: {}",
      () -> asJson(event));
    return eventService.save(event)
      .onComplete(result -> logResult(result, event));
  }

  private Future<String> updateUserSummary(E event) {
    log.debug("updateUserSummary:: parameters event: {}", () -> asJson(event));
    return getUserSummary(event)
      .compose(userSummary -> userSummaryService.updateUserSummaryWithEvent(userSummary, event));
  }

  protected Future<UserSummary> getUserSummary(E event) {
    log.debug("getUserSummary:: parameters event: {}", () -> asJson(event));
    return userSummaryRepository.findByUserIdOrBuildNew(event.getUserId());
  }

  private void logResult(AsyncResult<String> result, E event) {
    String eventType = EventType.getNameByEvent(event);
    if (result.failed()) {
      log.warn("logResult: Failed to process event {} with payload: {}", () -> eventType,
        () -> asJson(event));
    } else {
      String userSummaryId = result.result();
      log.info("logResult: Event {} processed successfully. Affected user summary: {}",
        eventType, userSummaryId);
    }
  }
}
