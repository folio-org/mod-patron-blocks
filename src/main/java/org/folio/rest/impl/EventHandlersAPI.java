package org.folio.rest.impl;


import java.lang.invoke.MethodHandles;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.domain.Event;
import org.folio.domain.EventType;
import org.folio.rest.handlers.ItemAgedToLostEventHandler;
import org.folio.rest.handlers.ItemCheckedInEventHandler;
import org.folio.rest.handlers.ItemCheckedOutEventHandler;
import org.folio.rest.handlers.ItemClaimedReturnedEventHandler;
import org.folio.rest.handlers.LoanDueDateChangedEventHandler;
import org.folio.rest.handlers.ItemDeclaredLostEventHandler;
import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.jaxrs.model.ItemAgedToLostEvent;
import org.folio.rest.jaxrs.model.ItemCheckedInEvent;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.ItemClaimedReturnedEvent;
import org.folio.rest.jaxrs.model.ItemDeclaredLostEvent;
import org.folio.rest.jaxrs.model.LoanDueDateChangedEvent;
import org.folio.rest.jaxrs.resource.AutomatedPatronBlocksHandlers;
import org.folio.rest.handlers.FeeFineBalanceChangedEventHandler;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class EventHandlersAPI implements AutomatedPatronBlocksHandlers {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public void postAutomatedPatronBlocksHandlersFeeFineBalanceChanged(
    FeeFineBalanceChangedEvent event, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      PostAutomatedPatronBlocksHandlersFeeFineBalanceChangedResponse.respond204()));

    logEventReceived(event);

    new FeeFineBalanceChangedEventHandler(okapiHeaders, vertxContext.owner())
      .handle(event);
  }

  @Override
  public void postAutomatedPatronBlocksHandlersItemCheckedOut(ItemCheckedOutEvent event,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      PostAutomatedPatronBlocksHandlersItemCheckedOutResponse.respond204()));

    logEventReceived(event);

    new ItemCheckedOutEventHandler(okapiHeaders, vertxContext.owner()).handle(event);
  }

  @Override
  public void postAutomatedPatronBlocksHandlersItemCheckedIn(ItemCheckedInEvent event,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      PostAutomatedPatronBlocksHandlersItemCheckedInResponse.respond204()));

    logEventReceived(event);

    new ItemCheckedInEventHandler(okapiHeaders, vertxContext.owner())
      .handle(event);
  }

  @Override
  public void postAutomatedPatronBlocksHandlersItemDeclaredLost(ItemDeclaredLostEvent event,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      PostAutomatedPatronBlocksHandlersItemDeclaredLostResponse.respond204()));

    logEventReceived(event);

    new ItemDeclaredLostEventHandler(okapiHeaders, vertxContext.owner()).handle(event);
  }

  @Override
  public void postAutomatedPatronBlocksHandlersItemAgedToLost(ItemAgedToLostEvent event,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      PostAutomatedPatronBlocksHandlersItemAgedToLostResponse.respond204()));

    logEventReceived(event);

    new ItemAgedToLostEventHandler(okapiHeaders, vertxContext.owner()).handle(event);
  }

  @Override
  public void postAutomatedPatronBlocksHandlersItemClaimedReturned(
    ItemClaimedReturnedEvent event, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      PostAutomatedPatronBlocksHandlersItemClaimedReturnedResponse.respond204()));

    logEventReceived(event);

    new ItemClaimedReturnedEventHandler(okapiHeaders, vertxContext.owner())
      .handle(event);
  }

  @Override
  public void postAutomatedPatronBlocksHandlersLoanDueDateChanged(LoanDueDateChangedEvent event,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      PostAutomatedPatronBlocksHandlersLoanDueDateChangedResponse.respond204()));

    logEventReceived(event);

    new LoanDueDateChangedEventHandler(okapiHeaders, vertxContext.owner())
      .handle(event);
  }

  private static void logEventReceived(Event event) {
    log.info("Received {} event with payload:\n{}",
      EventType.getNameByEvent(event),
      Json.encodePrettily(event));
  }
}
