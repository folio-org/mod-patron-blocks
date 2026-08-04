package org.folio.rest.handlers;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.domain.Event;
import org.folio.domain.EventType;
import org.folio.domain.event.DomainEvent;
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

  protected final PostgresClient postgresClient;
  protected final UserSummaryRepository userSummaryRepository;
  protected final EventService eventService;
  protected final UserSummaryService userSummaryService;

  public EventHandler(Map<String, String> okapiHeaders, Vertx vertx) {
    this(tenantId(okapiHeaders), vertx);
  }

  public EventHandler(String tenantId, Vertx vertx) {
    this(PostgresClient.getInstance(vertx, tenantId));
  }

  public EventHandler(PostgresClient postgresClient) {
    this.postgresClient = postgresClient;
    this.userSummaryRepository = new UserSummaryRepository(postgresClient);
    this.eventService = new EventService(postgresClient);
    this.userSummaryService = new UserSummaryService(postgresClient);
  }

  public Future<String> handle(DomainEvent<E> event) {
    log.info("handle:: {}", event);
    return handle(event.getData());
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

  private Future<String> updateUserSummary(E event) {
    return getUserSummary(event)
      .compose(userSummary -> userSummaryService.updateUserSummaryWithEvent(userSummary, event));
  }

  protected Future<UserSummary> getUserSummary(E event) {
    return userSummaryRepository.findByUserIdOrBuildNew(event.getUserId());
  }

  private void logResult(AsyncResult<String> result, E event) {
    String eventType = EventType.getNameByEvent(event);
    if (result.failed()) {
      log.warn("logResult: Failed to process event {}", eventType);
    } else {
      String userSummaryId = result.result();
      log.info("logResult: Event {} processed successfully. Affected user summary: {}",
        eventType, userSummaryId);
    }
  }

}
