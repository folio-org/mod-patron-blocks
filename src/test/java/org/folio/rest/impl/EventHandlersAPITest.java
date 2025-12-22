package org.folio.rest.impl;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.folio.rest.utils.EntityBuilder.buildFeeFineBalanceChangedEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemAgedToLostEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedInEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemClaimedReturnedEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemDeclaredLostEvent;
import static org.folio.rest.utils.EntityBuilder.buildLoanDueDateChangedEvent;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;

import org.awaitility.Awaitility;
import org.folio.domain.Event;
import org.folio.repository.UserSummaryRepository;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.jaxrs.model.ItemAgedToLostEvent;
import org.folio.rest.jaxrs.model.ItemCheckedInEvent;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.ItemClaimedReturnedEvent;
import org.folio.rest.jaxrs.model.ItemDeclaredLostEvent;
import org.folio.rest.jaxrs.model.LoanDueDateChangedEvent;
import org.folio.rest.jaxrs.model.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EventHandlersAPITest extends TestBase {
  public static final String USER_ID = randomId();
  public static final String INVALID_USER_ID = USER_ID + "xyz";

  private UserSummaryRepository userSummaryRepository;

  @BeforeEach
  void beforeEach() {
    super.resetMocks();

    userSummaryRepository = new UserSummaryRepository(postgresClient);

    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
  }

  @Test
  void feeFineBalanceChangedEventProcessedSuccessfully() {
    sendEventAndVerifyThatUserSummaryWasCreated(createFeeFineBalanceChangedEvent());
  }

  @Test
  void shouldNotCreateUserSummary() {
    assertFalse(getUserSummary().isPresent());
    eventClient.sendEvent(createItemCheckedInEvent());
    eventClient.sendEvent(createItemClaimedReturnedEvent());
    eventClient.sendEvent(createItemDeclaredLostEvent());
    eventClient.sendEvent(createItemAgedToLostEvent());
    eventClient.sendEvent(createLoanDueDateChangedEvent());
    assertFalse(getUserSummary().isPresent());
  }

  @Test
  void feeFineBalanceChangedEventValidationFails() {
    eventClient.sendEventAndVerifyValidationFailure(
      createFeeFineBalanceChangedEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void itemCheckedInEventProcessedSuccessfully() {
    eventClient.sendEvent(createItemCheckedInEvent());
  }

  @Test
  void itemCheckedInEventValidationFails() {
    eventClient.sendEventAndVerifyValidationFailure(
      createItemCheckedInEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void itemCheckedOutEventProcessedSuccessfully() {
    sendEventAndVerifyThatUserSummaryWasCreated(createItemCheckedOutEvent());
  }

  @Test
  void itemCheckedOutEventValidationFails() {
    eventClient.sendEventAndVerifyValidationFailure(
      createItemCheckedOutEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void loanDueDateChangedEventProcessedSuccessfully() {
    eventClient.sendEvent(createLoanDueDateChangedEvent());
  }

  @Test
  void loanDueDateChangedEventValidationFails() {
    eventClient.sendEventAndVerifyValidationFailure(
      createLoanDueDateChangedEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void itemDeclaredLostEventProcessedSuccessfully() {
    eventClient.sendEvent(createItemDeclaredLostEvent());
  }

  @Test
  void itemDeclaredLostEventValidationFails() {
    eventClient.sendEventAndVerifyValidationFailure(
      createItemDeclaredLostEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void itemAgedToLostEventProcessedSuccessfully() {
    eventClient.sendEvent(createItemAgedToLostEvent());
  }

  @Test
  void itemAgedToLostEventValidationFails() {
    eventClient.sendEventAndVerifyValidationFailure(
      createItemAgedToLostEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void itemClaimedReturnedEventProcessedSuccessfully() {
    eventClient.sendEvent(createItemClaimedReturnedEvent());
  }

  @Test
  void itemClaimedReturnedEventValidationFails() {
    eventClient.sendEventAndVerifyValidationFailure(
      createItemClaimedReturnedEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void eventHandlingFailsWhenEventJsonIsInvalid() {
    eventClient.sendEvent("not json", FeeFineBalanceChangedEvent.class, SC_BAD_REQUEST);
  }

  @Test
  void loanDueDateChangedEventWithMissingRequiredDueDateProperty() {
    eventClient.sendEventAndVerifyValidationFailure(
      createLoanDueDateChangedEvent().withDueDate(null));
  }

  private static FeeFineBalanceChangedEvent createFeeFineBalanceChangedEvent() {
    return buildFeeFineBalanceChangedEvent(
      USER_ID, randomId(), randomId(), randomId(), BigDecimal.TEN);
  }

  private static ItemCheckedInEvent createItemCheckedInEvent() {
    return buildItemCheckedInEvent(USER_ID, randomId(), new Date());
  }

  private static ItemCheckedOutEvent createItemCheckedOutEvent() {
    return buildItemCheckedOutEvent(USER_ID, randomId(), new Date());
  }

  private static LoanDueDateChangedEvent createLoanDueDateChangedEvent() {
    return buildLoanDueDateChangedEvent(USER_ID, randomId(), new Date(), false);
  }

  private static ItemDeclaredLostEvent createItemDeclaredLostEvent() {
    return buildItemDeclaredLostEvent(USER_ID, randomId());
  }

  private static ItemAgedToLostEvent createItemAgedToLostEvent() {
    return buildItemAgedToLostEvent(USER_ID, randomId());
  }

  private static ItemClaimedReturnedEvent createItemClaimedReturnedEvent() {
    return buildItemClaimedReturnedEvent(USER_ID, randomId());
  }

  private void sendEventAndVerifyThatUserSummaryWasCreated(Event event) {
    assertFalse(getUserSummary().isPresent());

    eventClient.sendEvent(event);

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> getUserSummary().isPresent());
  }

  private Optional<UserSummary> getUserSummary() {
    return waitFor(userSummaryRepository.getByUserId(USER_ID));
  }
}
