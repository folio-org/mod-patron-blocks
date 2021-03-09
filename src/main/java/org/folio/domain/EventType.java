package org.folio.domain;

import java.util.HashMap;
import java.util.Map;

import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.jaxrs.model.ItemAgedToLostEvent;
import org.folio.rest.jaxrs.model.ItemCheckedInEvent;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.ItemClaimedReturnedEvent;
import org.folio.rest.jaxrs.model.ItemDeclaredLostEvent;
import org.folio.rest.jaxrs.model.LoanDueDateChangedEvent;

public enum EventType {
  FEE_FINE_BALANCE_CHANGED(FeeFineBalanceChangedEvent.class),
  ITEM_CHECKED_OUT(ItemCheckedOutEvent.class),
  ITEM_CHECKED_IN(ItemCheckedInEvent.class),
  ITEM_DECLARED_LOST(ItemDeclaredLostEvent.class),
  ITEM_AGED_TO_LOST(ItemAgedToLostEvent.class),
  ITEM_CLAIMED_RETURNED(ItemClaimedReturnedEvent.class),
  LOAN_DUE_DATE_CHANGED(LoanDueDateChangedEvent.class),
  UNKNOWN(null);

  private static final Map<Class<? extends Event>, EventType> eventToType;
  static {
    eventToType = new HashMap<>(values().length);
    for (EventType eventType : values()) {
      eventToType.put(eventType.getEventClass(), eventType);
    }
  }

  private final Class<? extends Event> eventClass;

  EventType(Class<? extends Event> eventClass) {
    this.eventClass = eventClass;
  }

  public Class<? extends Event> getEventClass() {
    return eventClass;
  }

  public static EventType getByEvent(Object event) {
    return eventToType.getOrDefault(event.getClass(), UNKNOWN);
  }

  public static String getNameByEvent(Event event) {
    return getByEvent(event).name();
  }

}
