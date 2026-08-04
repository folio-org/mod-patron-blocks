package org.folio.verticle;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.folio.domain.event.FolioKafkaTopic.FEE_FINE_BALANCE_CHANGED;
import static org.folio.domain.event.FolioKafkaTopic.ITEM_AGED_TO_LOST;
import static org.folio.domain.event.FolioKafkaTopic.ITEM_CHECKED_IN;
import static org.folio.domain.event.FolioKafkaTopic.ITEM_CHECKED_OUT;
import static org.folio.domain.event.FolioKafkaTopic.ITEM_CLAIMED_RETURNED;
import static org.folio.domain.event.FolioKafkaTopic.ITEM_DECLARED_LOST;
import static org.folio.domain.event.FolioKafkaTopic.LOAN_DUE_DATE_CHANGED;
import static org.folio.rest.utils.EntityBuilder.buildFeeFineBalanceChangedEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemAgedToLostEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedInEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemClaimedReturnedEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemDeclaredLostEvent;
import static org.folio.rest.utils.EntityBuilder.buildLoanDueDateChangedEvent;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

import org.folio.domain.Event;
import org.folio.domain.event.FolioKafkaTopic;
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
import org.folio.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class EventConsumerVerticleTest extends TestBase {

  private static final String USER_ID = randomId();
  private static final String INVALID_USER_ID = USER_ID + "xyz";
  private final UserSummaryRepository userSummaryRepository = new UserSummaryRepository(postgresClient);

  @BeforeEach
  void beforeEach() {
    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
  }

  @Test
  void allTopicsAreCreated() {
    kafkaHelper.verifyTopicsExist(Arrays.asList(FolioKafkaTopic.values()), TEST_TENANT);
  }

  @Test
  void feeFineBalanceChangedEventProcessedSuccessfully() {
    publishEventAndVerifyThatUserSummaryWasCreated(FEE_FINE_BALANCE_CHANGED, createFeeFineBalanceChangedEvent());
  }

  @Test
  void shouldNotCreateUserSummary() {
    assertFalse(getUserSummary().isPresent());

    kafkaHelper.publishEventAndWaitUntilConsumed(ITEM_CHECKED_IN, TEST_TENANT, createItemCheckedInEvent());
    kafkaHelper.publishEventAndWaitUntilConsumed(ITEM_CLAIMED_RETURNED, TEST_TENANT, createItemClaimedReturnedEvent());
    kafkaHelper.publishEventAndWaitUntilConsumed(ITEM_DECLARED_LOST, TEST_TENANT, createItemDeclaredLostEvent());
    kafkaHelper.publishEventAndWaitUntilConsumed(ITEM_AGED_TO_LOST, TEST_TENANT, createItemAgedToLostEvent());
    kafkaHelper.publishEventAndWaitUntilConsumed(LOAN_DUE_DATE_CHANGED, TEST_TENANT, createLoanDueDateChangedEvent());

    assertFalse(getUserSummary().isPresent());
  }

  @Test
  void feeFineBalanceChangedEventValidationFails() {
    kafkaHelper.publishEventAndWaitUntilConsumed(FEE_FINE_BALANCE_CHANGED, TEST_TENANT,
      createFeeFineBalanceChangedEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void itemCheckedInEventProcessedSuccessfully() {
    kafkaHelper.publishEventAndWaitUntilConsumed(ITEM_CHECKED_IN, TEST_TENANT,
      createItemCheckedInEvent());
  }

  @Test
  void itemCheckedInEventValidationFails() {
    kafkaHelper.publishEventAndWaitUntilConsumed(ITEM_CHECKED_IN, TEST_TENANT,
      createItemCheckedInEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void itemCheckedOutEventProcessedSuccessfully() {
    kafkaHelper.publishEventAndWaitUntilConsumed(ITEM_CHECKED_OUT, TEST_TENANT,
      createItemCheckedOutEvent());
  }

  @Test
  void itemCheckedOutEventValidationFails() {
    kafkaHelper.publishEventAndWaitUntilConsumed(ITEM_CHECKED_OUT, TEST_TENANT,
      createItemCheckedOutEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void loanDueDateChangedEventProcessedSuccessfully() {
    kafkaHelper.publishEventAndWaitUntilConsumed(LOAN_DUE_DATE_CHANGED, TEST_TENANT,
      createLoanDueDateChangedEvent());
  }

  @Test
  void loanDueDateChangedEventValidationFails() {
    kafkaHelper.publishEventAndWaitUntilConsumed(LOAN_DUE_DATE_CHANGED, TEST_TENANT,
      createLoanDueDateChangedEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void itemDeclaredLostEventProcessedSuccessfully() {
    kafkaHelper.publishEventAndWaitUntilConsumed(ITEM_DECLARED_LOST, TEST_TENANT,
      createItemDeclaredLostEvent());
  }

  @Test
  void itemDeclaredLostEventValidationFails() {
    kafkaHelper.publishEventAndWaitUntilConsumed(ITEM_DECLARED_LOST, TEST_TENANT,
      createItemDeclaredLostEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void itemAgedToLostEventProcessedSuccessfully() {
    kafkaHelper.publishEventAndWaitUntilConsumed(ITEM_AGED_TO_LOST, TEST_TENANT,
      createItemAgedToLostEvent());
  }

  @Test
  void itemAgedToLostEventValidationFails() {
    kafkaHelper.publishEventAndWaitUntilConsumed(ITEM_AGED_TO_LOST, TEST_TENANT,
      createItemAgedToLostEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void itemClaimedReturnedEventProcessedSuccessfully() {
    kafkaHelper.publishEventAndWaitUntilConsumed(ITEM_CLAIMED_RETURNED, TEST_TENANT,
      createItemClaimedReturnedEvent());
  }

  @Test
  void itemClaimedReturnedEventValidationFails() {
    kafkaHelper.publishEventAndWaitUntilConsumed(ITEM_CLAIMED_RETURNED, TEST_TENANT,
      createItemClaimedReturnedEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void eventHandlingFailsWhenEventJsonIsInvalid() {
    eventClient.sendEvent("not json", FeeFineBalanceChangedEvent.class, SC_BAD_REQUEST);
  }

  @Test
  void loanDueDateChangedEventWithMissingRequiredDueDateProperty() {
    kafkaHelper.publishEventAndWaitUntilConsumed(LOAN_DUE_DATE_CHANGED, TEST_TENANT,
      createLoanDueDateChangedEvent().withUserId(INVALID_USER_ID));
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

  private void publishEventAndVerifyThatUserSummaryWasCreated(FolioKafkaTopic topic, Event event) {
    assertFalse(getUserSummary().isPresent());
    kafkaHelper.publishEventAndWaitUntilConsumed(topic, TEST_TENANT, event);
    Wait.waitFor(() -> getUserSummary().isPresent());
  }

  private Optional<UserSummary> getUserSummary() {
    return waitFor(userSummaryRepository.getByUserId(USER_ID));
  }

}
