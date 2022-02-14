package org.folio.rest.utils;

import static java.util.Map.entry;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.junit.Assert.fail;

import java.util.Map;

import org.folio.domain.Event;
import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.jaxrs.model.ItemAgedToLostEvent;
import org.folio.rest.jaxrs.model.ItemCheckedInEvent;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.ItemClaimedReturnedEvent;
import org.folio.rest.jaxrs.model.ItemDeclaredLostEvent;
import org.folio.rest.jaxrs.model.LoanDueDateChangedEvent;

import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonObject;

public class EventUtils {
  private static final String EVENT_HANDLERS_ROOT_URL = "/automated-patron-blocks/handlers/";

  private static final String FEE_FINE_BALANCE_CHANGED_HANDLER_URL =
    EVENT_HANDLERS_ROOT_URL + "fee-fine-balance-changed";
  private static final String ITEM_CHECKED_OUT_HANDLER_URL =
    EVENT_HANDLERS_ROOT_URL + "item-checked-out";
  private static final String ITEM_CHECKED_IN_HANDLER_URL =
    EVENT_HANDLERS_ROOT_URL + "item-checked-in";
  private static final String ITEM_DECLARED_LOST_HANDLER_URL =
    EVENT_HANDLERS_ROOT_URL + "item-declared-lost";
  private static final String ITEM_AGED_TO_LOST_HANDLER_URL =
    EVENT_HANDLERS_ROOT_URL + "item-aged-to-lost";
  private static final String ITEM_CLAIMED_RETURNED_HANDLER_URL =
    EVENT_HANDLERS_ROOT_URL + "item-claimed-returned";
  private static final String LOAN_DUE_DATE_CHANGED_HANDLER_URL =
    EVENT_HANDLERS_ROOT_URL + "loan-due-date-changed";

  private static OkapiClient okapiClient;

  private static final Map<Class<? extends Event>, String> eventTypeToHandlerUrl =
    Map.ofEntries(
      entry(FeeFineBalanceChangedEvent.class, FEE_FINE_BALANCE_CHANGED_HANDLER_URL),
      entry(ItemCheckedOutEvent.class, ITEM_CHECKED_OUT_HANDLER_URL),
      entry(ItemCheckedInEvent.class, ITEM_CHECKED_IN_HANDLER_URL),
      entry(ItemDeclaredLostEvent.class, ITEM_DECLARED_LOST_HANDLER_URL),
      entry(ItemAgedToLostEvent.class, ITEM_AGED_TO_LOST_HANDLER_URL),
      entry(ItemClaimedReturnedEvent.class, ITEM_CLAIMED_RETURNED_HANDLER_URL),
      entry(LoanDueDateChangedEvent.class, LOAN_DUE_DATE_CHANGED_HANDLER_URL)
    );

  public static ValidatableResponse sendEvent(Event event, int expectedStatus) {
    String eventPayload = JsonObject.mapFrom(event).encodePrettily();
    return sendEvent(eventPayload, getHandlerUrlForEventType(event.getClass()), expectedStatus);
  }

  private static ValidatableResponse sendEvent(String eventPayload, String handlerUrl,
    int expectedStatus) {

    if(okapiClient == null) {
      fail("Failed to send event, okapi client is null");
    }

    return okapiClient.post(handlerUrl, eventPayload)
      .then()
      .statusCode(expectedStatus);
  }

  public static ValidatableResponse sendEvent(String eventPayload, Class eventType,
    int expectedStatus) {

    return sendEvent(eventPayload, getHandlerUrlForEventType(eventType), expectedStatus);
  }

  public static ValidatableResponse sendEventAndVerifyValidationFailure(Event event) {
    return sendEvent(event, SC_UNPROCESSABLE_ENTITY);
  }

  private static String getHandlerUrlForEventType(Class eventType) {
    final String eventHandlerUrl = eventTypeToHandlerUrl.get(eventType);

    if (eventHandlerUrl == null) {
      fail("Failed to resolve handler URL for event of type " + eventType.getSimpleName());
    }

    return eventHandlerUrl;
  }

  public static void setOkapiClient(OkapiClient okapiClient) {
    EventUtils.okapiClient = okapiClient;
  }
}
