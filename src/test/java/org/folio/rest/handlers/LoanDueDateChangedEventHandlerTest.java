package org.folio.rest.handlers;

import static java.util.Collections.singletonList;
import static org.folio.repository.UserSummaryRepository.USER_SUMMARY_TABLE_NAME;
import static org.folio.rest.utils.EntityBuilder.buildDefaultMetadata;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.folio.rest.utils.EntityBuilder.buildLoanDueDateChangedEvent;

import java.util.Date;
import java.util.Optional;

import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.LoanDueDateChangedEvent;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.UserSummary;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class LoanDueDateChangedEventHandlerTest extends EventHandlerTestBase {

  private static final EventHandler<LoanDueDateChangedEvent> loanDueDateChangedEventHandler =
    new EventHandler<>(postgresClient);

  private static final EventHandler<ItemCheckedOutEvent> itemCheckedOutEventHandler =
    new EventHandler<>(postgresClient);

  @Before
  public void beforeEach(TestContext context) {
    super.resetMocks();
    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
  }

  @Test
  public void createNewSummaryIfDoesNotExist(TestContext context) {
    String userId = randomId();

    Optional<UserSummary> summaryBeforeEvent = waitFor(userSummaryRepository.getByUserId(userId));
    context.assertFalse(summaryBeforeEvent.isPresent());

    LoanDueDateChangedEvent event = new LoanDueDateChangedEvent()
      .withUserId(userId)
      .withLoanId(randomId())
      .withDueDate(new Date())
      .withDueDateChangedByRecall(false)
      .withMetadata(buildDefaultMetadata());

    String summaryId = waitFor(loanDueDateChangedEventHandler.handle(event));

    UserSummary expectedSummary = new UserSummary()
      .withId(summaryId)
      .withUserId(userId)
      .withOpenLoans(singletonList(new OpenLoan()
        .withLoanId(event.getLoanId())
        .withDueDate(event.getDueDate())
        .withRecall(event.getDueDateChangedByRecall())));

    checkUserSummary(summaryId, expectedSummary, context);
  }

  @Test
  public void createNewLoanInExistingSummaryIfItDoesNotExist(TestContext context) {
    String userId = randomId();
    String loanId = randomId();
    Date dueDate = new Date();
    boolean dueDateChangedByRecall = true;

    String updatedSummaryId = waitFor(loanDueDateChangedEventHandler.handle(
      buildLoanDueDateChangedEvent(userId, loanId, dueDate, dueDateChangedByRecall)));

    UserSummary expectedSummary = new UserSummary()
      .withId(updatedSummaryId)
      .withUserId(userId)
      .withOpenLoans(singletonList(new OpenLoan()
        .withLoanId(loanId)
        .withDueDate(dueDate)
        .withRecall(dueDateChangedByRecall)));

    checkUserSummary(updatedSummaryId, expectedSummary, context);
  }

  @Test
  public void existingLoanIsUpdated(TestContext context) {
    String userId = randomId();
    String loanId = randomId();

    waitFor(itemCheckedOutEventHandler.handle(
      buildItemCheckedOutEvent(userId, loanId, new Date())));

    UserSummary summaryBeforeEvent = waitFor(userSummaryRepository.getByUserId(userId)
      .map(Optional::get));

    Date newDueDate = new DateTime(summaryBeforeEvent.getOpenLoans().get(0).getDueDate())
      .plusDays(1)
      .toDate();

    LoanDueDateChangedEvent event = buildLoanDueDateChangedEvent(userId, loanId, newDueDate, true);

    String updatedSummaryId = waitFor(loanDueDateChangedEventHandler.handle(event));
    context.assertEquals(summaryBeforeEvent.getId(), updatedSummaryId);

    UserSummary expectedUserSummary = summaryBeforeEvent
      .withOpenLoans(singletonList(new OpenLoan()
          .withLoanId(loanId)
          .withDueDate(event.getDueDate())
          .withRecall(true)));

    checkUserSummary(updatedSummaryId, expectedUserSummary, context);
  }
}
