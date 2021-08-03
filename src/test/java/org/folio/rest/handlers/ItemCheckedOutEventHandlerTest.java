package org.folio.rest.handlers;

import static org.folio.repository.UserSummaryRepository.USER_SUMMARY_TABLE_NAME;
import static org.folio.rest.utils.EntityBuilder.buildDefaultMetadata;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;

import java.util.Collections;
import java.util.Optional;

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
  private static final EventHandler<ItemCheckedOutEvent> itemCheckedOutEventHandler =
    new EventHandler<>(postgresClient);

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
      .withDueDate(dueDate.toDate())
      .withMetadata(buildDefaultMetadata());

    String summaryId = waitFor(itemCheckedOutEventHandler.handle(event));

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

    waitFor(itemCheckedOutEventHandler.handle(
      buildItemCheckedOutEvent(userId, randomId(), dueDate.toDate())));

    UserSummary expectedUserSummary = waitFor(userSummaryRepository.getByUserId(userId)
      .map(Optional::get));

    String summaryId = waitFor(itemCheckedOutEventHandler.handle(
      buildItemCheckedOutEvent(userId, loanId, dueDate.toDate())));

    expectedUserSummary.getOpenLoans().add(new OpenLoan()
      .withLoanId(loanId)
      .withRecall(false)
      .withItemLost(false)
      .withDueDate(dueDate.toDate()));

    checkUserSummary(summaryId, expectedUserSummary, context);
  }

  @Test
  public void shouldNotChangeWhenOpenLoanWithTheSameLoanIdExists(TestContext context) {
    String userId = randomId();
    String loanId = randomId();
    DateTime dueDate = DateTime.now();

    waitFor(itemCheckedOutEventHandler.handle(
      buildItemCheckedOutEvent(userId, loanId, dueDate.toDate())));

    UserSummary initialUserSummary = waitFor(userSummaryRepository.getByUserId(userId)
      .map(Optional::get));

    waitFor(itemCheckedOutEventHandler.handle(
      buildItemCheckedOutEvent(userId, loanId, dueDate.toDate())));

    UserSummary updatedUserSummary = waitFor(userSummaryRepository.getByUserId(userId)
      .map(Optional::get));

    context.assertEquals(initialUserSummary.getId(), updatedUserSummary.getId());

    checkUserSummary(updatedUserSummary.getId(), initialUserSummary, context);
  }
}
