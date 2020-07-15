package org.folio.rest.handlers;

import static org.folio.repository.UserSummaryRepository.USER_SUMMARY_TABLE_NAME;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.UserSummary;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ItemCheckedOutEventHandlerTest extends EventHandlerTestBase {
  private static final ItemCheckedOutEventHandler eventHandler =
    new ItemCheckedOutEventHandler(postgresClient);

  @Before
  public void beforeEach(TestContext context) {
    super.resetMocks();
    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
  }

  @Test
  public void userSummaryShouldBeCreatedWhenDoesNotExist(TestContext context) {
    String userId = randomId();
    String loanId = randomId();
    DateTime dueDate = DateTime.now();

    ItemCheckedOutEvent event = new ItemCheckedOutEvent()
      .withUserId(userId)
      .withLoanId(loanId)
      .withDueDate(dueDate.toDate());

    String summaryId = waitFor(eventHandler.handle(event));

    UserSummary userSummaryToCompare = new UserSummary()
      .withUserId(userId)
      .withOpenLoans(Collections.singletonList(new OpenLoan()
        .withLoanId(loanId)
        .withRecall(false)
        .withItemLost(false)
        .withDueDate(dueDate.toDate())));

    checkUserSummary(summaryId, userSummaryToCompare, context);
  }

  @Test
  public void shouldAddOpenLoanWhenUserSummaryExists(TestContext context) {
    String userId = randomId();
    String loanId = randomId();
    DateTime dueDate = DateTime.now();

    List<OpenLoan> existingOpenLoans = new ArrayList<>();
    existingOpenLoans.add(new OpenLoan()
      .withLoanId(randomId())
      .withDueDate(dueDate.toDate())
      .withRecall(false)
      .withItemLost(false));

    UserSummary existingUserSummary = new UserSummary()
      .withUserId(userId)
      .withOpenLoans(existingOpenLoans);

    waitFor(userSummaryRepository.save(existingUserSummary));

    ItemCheckedOutEvent event = new ItemCheckedOutEvent()
      .withUserId(userId)
      .withLoanId(loanId)
      .withDueDate(dueDate.toDate());

    String summaryId = waitFor(eventHandler.handle(event));

    existingOpenLoans.add(new OpenLoan()
      .withLoanId(loanId)
      .withRecall(false)
      .withItemLost(false)
      .withDueDate(dueDate.toDate()));

    checkUserSummary(summaryId, existingUserSummary, context);
  }

  @Test
  public void shouldFailWhenOpenLoanWithTheSameLoanIdExists(TestContext context) {
    String userId = randomId();
    String loanId = randomId();
    DateTime dueDate = DateTime.now();

    List<OpenLoan> existingOpenLoans = new ArrayList<>();
    existingOpenLoans.add(new OpenLoan()
      .withLoanId(loanId)
      .withDueDate(dueDate.toDate())
      .withRecall(false));

    UserSummary existingUserSummary = new UserSummary()
      .withUserId(userId)
      .withOpenLoans(existingOpenLoans);

    waitFor(userSummaryRepository.save(existingUserSummary));

    ItemCheckedOutEvent event = new ItemCheckedOutEvent()
      .withUserId(userId)
      .withLoanId(loanId)
      .withDueDate(dueDate.toDate());

    context.assertNull(waitFor(eventHandler.handle(event)));
  }
}
