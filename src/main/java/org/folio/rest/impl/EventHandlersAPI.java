package org.folio.rest.impl;


import static org.folio.util.LogUtil.asJson;
import static org.folio.util.LogUtil.headersAsString;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.domain.Event;
import org.folio.domain.EventType;
import org.folio.rest.handlers.EventHandler;
import org.folio.rest.handlers.FeeFineBalanceChangedEventHandler;
import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.jaxrs.model.ItemAgedToLostEvent;
import org.folio.rest.jaxrs.model.ItemCheckedInEvent;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.ItemClaimedReturnedEvent;
import org.folio.rest.jaxrs.model.ItemDeclaredLostEvent;
import org.folio.rest.jaxrs.model.LoanClosedEvent;
import org.folio.rest.jaxrs.model.LoanDueDateChangedEvent;
import org.folio.rest.jaxrs.resource.AutomatedPatronBlocksHandlers;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public class EventHandlersAPI implements AutomatedPatronBlocksHandlers {
  private static final Logger log = LogManager.getLogger(EventHandlersAPI.class);

  @Override
  public void postAutomatedPatronBlocksHandlersFeeFineBalanceChanged(
    FeeFineBalanceChangedEvent event, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    logEventReceived("postAutomatedPatronBlocksHandlersFeeFineBalanceChanged", event, okapiHeaders);

    asyncResultHandler.handle(Future.succeededFuture(
      PostAutomatedPatronBlocksHandlersFeeFineBalanceChangedResponse.respond204()));

    new FeeFineBalanceChangedEventHandler(okapiHeaders, vertxContext.owner())
      .handle(event);
  }

  @Override
  public void postAutomatedPatronBlocksHandlersItemCheckedOut(ItemCheckedOutEvent event,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    logEventReceived("postAutomatedPatronBlocksHandlersItemCheckedOut", event, okapiHeaders);

    asyncResultHandler.handle(Future.succeededFuture(
      PostAutomatedPatronBlocksHandlersItemCheckedOutResponse.respond204()));

    new EventHandler<ItemCheckedOutEvent>(okapiHeaders, vertxContext.owner()).handle(event);
  }

  @Override
  public void postAutomatedPatronBlocksHandlersItemCheckedIn(ItemCheckedInEvent event,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    logEventReceived("postAutomatedPatronBlocksHandlersItemCheckedIn", event, okapiHeaders);

    asyncResultHandler.handle(Future.succeededFuture(
      PostAutomatedPatronBlocksHandlersItemCheckedInResponse.respond204()));

    new EventHandler<ItemCheckedInEvent>(okapiHeaders, vertxContext.owner())
      .handle(event);
  }

  @Override
  public void postAutomatedPatronBlocksHandlersItemDeclaredLost(ItemDeclaredLostEvent event,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    logEventReceived("postAutomatedPatronBlocksHandlersItemDeclaredLost", event, okapiHeaders);

    asyncResultHandler.handle(Future.succeededFuture(
      PostAutomatedPatronBlocksHandlersItemDeclaredLostResponse.respond204()));

    new EventHandler<ItemDeclaredLostEvent>(okapiHeaders, vertxContext.owner())
      .handle(event);
  }

  @Override
  public void postAutomatedPatronBlocksHandlersItemAgedToLost(ItemAgedToLostEvent event,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    logEventReceived("postAutomatedPatronBlocksHandlersItemAgedToLost", event, okapiHeaders);

    asyncResultHandler.handle(Future.succeededFuture(
      PostAutomatedPatronBlocksHandlersItemAgedToLostResponse.respond204()));

    new EventHandler<ItemAgedToLostEvent>(okapiHeaders, vertxContext.owner()).handle(event);
  }

  @Override
  public void postAutomatedPatronBlocksHandlersItemClaimedReturned(
    ItemClaimedReturnedEvent event, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    logEventReceived("postAutomatedPatronBlocksHandlersItemClaimedReturned", event, okapiHeaders);

    asyncResultHandler.handle(Future.succeededFuture(
      PostAutomatedPatronBlocksHandlersItemClaimedReturnedResponse.respond204()));

    new EventHandler<ItemClaimedReturnedEvent>(okapiHeaders, vertxContext.owner())
      .handle(event);
  }

  @Override
  public void postAutomatedPatronBlocksHandlersLoanDueDateChanged(LoanDueDateChangedEvent event,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    logEventReceived("postAutomatedPatronBlocksHandlersLoanDueDateChanged", event, okapiHeaders);

    asyncResultHandler.handle(Future.succeededFuture(
      PostAutomatedPatronBlocksHandlersLoanDueDateChangedResponse.respond204()));

    new EventHandler<LoanDueDateChangedEvent>(okapiHeaders, vertxContext.owner())
      .handle(event);
  }

  @Override
  public void postAutomatedPatronBlocksHandlersLoanClosed(LoanClosedEvent event,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    logEventReceived("postAutomatedPatronBlocksHandlersLoanClosed", event, okapiHeaders);

    asyncResultHandler.handle(Future.succeededFuture(
      PostAutomatedPatronBlocksHandlersLoanClosedResponse.respond204()));

    new EventHandler<LoanClosedEvent>(okapiHeaders, vertxContext.owner())
      .handle(event);
  }

  private static void logEventReceived(String methodName, Event event,
    Map<String, String> okapiHeaders) {

    log.info("{}:: parameters : {} event {}, okapiHeaders: {}", () -> methodName,
      () -> EventType.getNameByEvent(event), () -> asJson(event),
      () -> headersAsString(okapiHeaders));
  }
}
