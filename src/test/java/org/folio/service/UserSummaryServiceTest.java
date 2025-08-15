package org.folio.service;

import static org.folio.repository.UserSummaryRepository.USER_SUMMARY_TABLE_NAME;
import static org.folio.rest.utils.EntityBuilder.buildFeeFineBalanceChangedEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemAgedToLostEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.folio.rest.utils.EntityBuilder.buildLoanDueDateChangedEvent;
import static org.joda.time.DateTime.now;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.util.Date;

import org.folio.repository.UserSummaryRepository;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.jaxrs.model.ItemAgedToLostEvent;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.LoanDueDateChangedEvent;
import org.folio.rest.jaxrs.model.UserSummary;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class UserSummaryServiceTest extends TestBase {
  private final UserSummaryService userSummaryService =
    new UserSummaryService(postgresClient);

  protected final UserSummaryRepository userSummaryRepository =
    new UserSummaryRepository(postgresClient);

  @Before
  public void beforeEach() {
    super.resetMocks();
    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
  }

  @Test
  public void shouldAddEvent(TestContext context) {
    final String userId = randomId();
    waitFor(userSummaryRepository.save(createUserSummary(randomId(), userId)));

    UserSummary userSummary = waitFor(userSummaryService.getByUserId(userId));

    final String feeFineId = randomId();
    FeeFineBalanceChangedEvent feeFineBalanceChangedEvent = buildFeeFineBalanceChangedEvent(
      userId, randomId(), feeFineId, randomId(), new BigDecimal("3.33"));

    userSummaryService.updateUserSummaryWithEvent(userSummary, feeFineBalanceChangedEvent);

    await().until(() ->
        waitFor(userSummaryService.getByUserId(userId))
        .getOpenFeesFines().stream()
        .anyMatch(openFeeFine -> openFeeFine.getFeeFineId().equals(feeFineId)));
  }

  @Test
  public void loanDueDateChangedEventShouldSetItemLostToFalse(TestContext context) {
    String userId = randomId();
    String loanId = randomId();
    Date dueDate = now().plusHours(1).toDate();

    waitFor(userSummaryRepository.save(createUserSummary(randomId(), userId)));
    UserSummary userSummary = waitFor(userSummaryService.getByUserId(userId));

    ItemCheckedOutEvent itemCheckedOutEvent = buildItemCheckedOutEvent(userId, loanId, dueDate);
    userSummaryService.updateUserSummaryWithEvent(userSummary, itemCheckedOutEvent);

    ItemAgedToLostEvent itemAgedToLostEvent = buildItemAgedToLostEvent(userId, loanId);
    waitFor(userSummaryService.updateUserSummaryWithEvent(userSummary, itemAgedToLostEvent));

    UserSummary updatedUserSummary = waitFor(userSummaryService.getByUserId(userId));
    context.assertTrue(updatedUserSummary.getOpenLoans().stream()
      .anyMatch(openLoan -> openLoan.getItemLost().equals(true)));

    LoanDueDateChangedEvent loanDueDateChangedEvent = buildLoanDueDateChangedEvent(userId, loanId, now().plusHours(2).toDate(), false);
    waitFor(userSummaryService.updateUserSummaryWithEvent(userSummary, loanDueDateChangedEvent));
    updatedUserSummary = waitFor(userSummaryService.getByUserId(userId));

    context.assertTrue(updatedUserSummary.getOpenLoans().stream()
      .anyMatch(loan -> loan.getLoanId().equals(loanId) && loan.getItemLost().equals(false)));
  }

  @Test
  public void shouldDeleteFeeFineAfterRetryingInCaseOfOptimisticLockingError(
    TestContext context) {
    final String userId = randomId();

    String summaryId = randomId();
    UserSummary userSummaryToSave = createUserSummary(summaryId, randomId());
    waitFor(userSummaryRepository.save(userSummaryToSave));

    waitFor(userSummaryRepository.get(summaryId)).ifPresent(userSummary -> {
      final String feeFineId = randomId();
      final String loanId = randomId();
      final String feeFineTypeId = randomId();
      BigDecimal balance = new BigDecimal("3.33");
      FeeFineBalanceChangedEvent feeFineBalanceChangedEvent = buildFeeFineBalanceChangedEvent(
        userId, loanId, feeFineId, feeFineTypeId, balance);
      waitFor(userSummaryService.updateUserSummaryWithEvent(userSummary, feeFineBalanceChangedEvent));
      waitFor(userSummaryRepository.get(summaryId)).ifPresent(userSummaryAfterFirstUpdate -> {
        userSummaryAfterFirstUpdate.setVersion(1);
        BigDecimal newBalance = new BigDecimal("7.77");
        feeFineBalanceChangedEvent.setBalance(newBalance);
        waitFor(
          userSummaryService.updateUserSummaryWithEvent(userSummaryAfterFirstUpdate, feeFineBalanceChangedEvent));
        waitFor(userSummaryRepository.get(summaryId)).ifPresent(
          userSummaryAfterSecondUpdate -> context.assertTrue(
            userSummaryAfterSecondUpdate.getOpenFeesFines().get(0).getBalance().equals(newBalance)));
      });
    });
  }

  @Test
  public void shouldUpdateNewUserSummaryIfUserSummaryDidNotExist(TestContext context) {
    final String userId = randomId();
    final String summaryId = randomId();
    FeeFineBalanceChangedEvent feeFineBalanceChangedEvent1 = buildFeeFineBalanceChangedEvent(
      userId, randomId(), randomId(), randomId(), new BigDecimal("3.33"));
    FeeFineBalanceChangedEvent feeFineBalanceChangedEvent2 = buildFeeFineBalanceChangedEvent(
      userId, randomId(), randomId(), randomId(), new BigDecimal("7.77"));

    UserSummary userSummary = createUserSummary(summaryId, userId);
    waitFor(userSummaryService.updateUserSummaryWithEvent(userSummary, feeFineBalanceChangedEvent1));
    waitFor(userSummaryService.updateUserSummaryWithEvent(userSummary, feeFineBalanceChangedEvent2));
    waitFor(userSummaryRepository.get(summaryId)).ifPresent(userSummaryAfterBothUpdates ->
      context.assertTrue(userSummaryAfterBothUpdates.getOpenFeesFines().stream()
        .allMatch(openFeeFine -> openFeeFine.getFeeFineId()
          .equals(feeFineBalanceChangedEvent1.getFeeFineId()) || openFeeFine.getFeeFineId()
          .equals(feeFineBalanceChangedEvent2.getFeeFineId()))
      ));
  }

  private UserSummary createUserSummary(String id, String userId) {
    return new UserSummary()
      .withId(id)
      .withUserId(userId);
  }
}
