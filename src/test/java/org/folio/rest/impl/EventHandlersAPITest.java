package org.folio.rest.impl;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.folio.repository.UserSummaryRepository.USER_SUMMARY_TABLE_NAME;
import static org.folio.rest.utils.EntityBuilder.buildFeeFineBalanceChangedEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemAgedToLostEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedInEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemClaimedReturnedEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemDeclaredLostEvent;
import static org.folio.rest.utils.EntityBuilder.buildLoanDueDateChangedEvent;
import static org.folio.rest.utils.EventUtils.sendEvent;
import static org.folio.rest.utils.EventUtils.sendEventAndVerifyValidationFailure;
import static org.junit.Assert.assertFalse;

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
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class EventHandlersAPITest extends TestBase {
  public static final String USER_ID = randomId();
  public static final String INVALID_USER_ID = USER_ID + "xyz";

  private final UserSummaryRepository userSummaryRepository =
    new UserSummaryRepository(postgresClient);

  @After
  public void afterEach() {
    super.resetMocks();
    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
  }

  @Test
  public void feeFineBalanceChangedEventProcessedSuccessfully() {
    sendEventAndVerifyThatUserSummaryWasCreated(createFeeFineBalanceChangedEvent());
  }

  @Test
  public void shouldNotCreateUserSummary() {
    assertFalse(getUserSummary().isPresent());
    sendEvent(createItemCheckedInEvent(), SC_NO_CONTENT);
    sendEvent(createItemClaimedReturnedEvent(), SC_NO_CONTENT);
    sendEvent(createItemDeclaredLostEvent(), SC_NO_CONTENT);
    sendEvent(createItemAgedToLostEvent(), SC_NO_CONTENT);
    sendEvent(createLoanDueDateChangedEvent(), SC_NO_CONTENT);
    assertFalse(getUserSummary().isPresent());
  }

  @Test
  public void feeFineBalanceChangedEventValidationFails() {
    sendEventAndVerifyValidationFailure(
      createFeeFineBalanceChangedEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  public void itemCheckedInEventProcessedSuccessfully() {
    sendEvent(createItemCheckedInEvent(), SC_NO_CONTENT);
  }

  @Test
  public void itemCheckedInEventValidationFails() {
    sendEventAndVerifyValidationFailure(
      createItemCheckedInEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  public void itemCheckedOutEventProcessedSuccessfully() {
    sendEventAndVerifyThatUserSummaryWasCreated(createItemCheckedOutEvent());
  }

  @Test
  public void itemCheckedOutEventValidationFails() {
    sendEventAndVerifyValidationFailure(
      createItemCheckedOutEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  public void loanDueDateChangedEventProcessedSuccessfully() {
    sendEvent(createLoanDueDateChangedEvent(), SC_NO_CONTENT);
  }

  @Test
  public void loanDueDateChangedEventValidationFails() {
    sendEventAndVerifyValidationFailure(
      createLoanDueDateChangedEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  public void itemDeclaredLostEventProcessedSuccessfully() {
    sendEvent(createItemDeclaredLostEvent(), SC_NO_CONTENT);
  }

  @Test
  public void itemDeclaredLostEventValidationFails() {
    sendEventAndVerifyValidationFailure(
      createItemDeclaredLostEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  public void itemAgedToLostEventProcessedSuccessfully() {
    sendEvent(createItemAgedToLostEvent(), SC_NO_CONTENT);
  }

  @Test
  public void itemAgedToLostEventValidationFails() {
    sendEventAndVerifyValidationFailure(
      createItemAgedToLostEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  public void itemClaimedReturnedEventProcessedSuccessfully() {
    sendEvent(createItemClaimedReturnedEvent(), SC_NO_CONTENT);
  }

  @Test
  public void itemClaimedReturnedEventValidationFails() {
    sendEventAndVerifyValidationFailure(
      createItemClaimedReturnedEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  public void eventHandlingFailsWhenEventJsonIsInvalid() {
    sendEvent("not json", FeeFineBalanceChangedEvent.class, SC_BAD_REQUEST);
  }

  @Test
  public void loanDueDateChangedEventWithMissingRequiredDueDateProperty() {
    sendEventAndVerifyValidationFailure(
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

    sendEvent(event, SC_NO_CONTENT);

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> getUserSummary().isPresent());
  }

  private Optional<UserSummary> getUserSummary() {
    return waitFor(userSummaryRepository.getByUserId(USER_ID));
  }
}
