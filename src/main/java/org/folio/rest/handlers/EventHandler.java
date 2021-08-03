package org.folio.rest.handlers;

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
import io.vertx.core.json.Json;

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
    return eventService.save(event)
      .compose(eventId -> updateUserSummary(event))
      .onComplete(result -> logResult(result, event));
  }

  public Future<String> handleSkippingUserSummaryUpdate(E event) {
    return eventService.save(event)
      .onComplete(result -> logResult(result, event));
  }

  public Future<String> updateUserSummary(E event) {
    return getUserSummary(event)
      .compose(userSummary -> userSummaryService.updateUserSummaryWithEvent(userSummary, event));
  }

  protected Future<UserSummary> getUserSummary(E event) {
    return userSummaryRepository.findByUserIdOrBuildNew(event.getUserId());
  }

  protected void logResult(AsyncResult<String> result, E event) {
    String eventType = EventType.getNameByEvent(event);
    if (result.failed()) {
      String eventJson = Json.encodePrettily(event);
      log.error("Failed to process event {} with payload:\n{}", eventType, eventJson);
    } else {
      String userSummaryId = result.result();
      log.info("Event {} processed successfully. Affected user summary: {}",
        eventType, userSummaryId);
    }
  }
}
