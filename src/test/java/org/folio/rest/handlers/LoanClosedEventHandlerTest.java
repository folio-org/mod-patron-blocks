package org.folio.rest.handlers;

import static org.folio.repository.UserSummaryRepository.USER_SUMMARY_TABLE_NAME;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.folio.rest.utils.EntityBuilder.buildLoanClosedEvent;

import java.util.Optional;

import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.LoanClosedEvent;
import org.folio.rest.jaxrs.model.UserSummary;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class LoanClosedEventHandlerTest extends EventHandlerTestBase {
  private static final EventHandler<LoanClosedEvent> loanClosedEventHandler =
    new EventHandler<>(postgresClient);

  private static final EventHandler<ItemCheckedOutEvent> itemCheckedOutEventHandler =
    new EventHandler<>(postgresClient);

  @Before
  public void beforeEach(TestContext context) {
    super.resetMocks();
    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
  }

  @Test
  public void loanShouldBeRemovedFromUserSummary(TestContext context) {
    String userId = randomId();
    String loan1Id = randomId();
    String loan2Id = randomId();
    DateTime dueDate1 = DateTime.now();
    DateTime dueDate2 = DateTime.now();

    waitFor(itemCheckedOutEventHandler.handle(buildItemCheckedOutEvent(userId, loan1Id,
      dueDate1.toDate())));
    waitFor(itemCheckedOutEventHandler.handle(buildItemCheckedOutEvent(userId, loan2Id,
      dueDate2.toDate())));

    UserSummary initialUserSummary = waitFor(userSummaryRepository.getByUserId(userId)
      .map(Optional::get));

    context.assertNotNull(initialUserSummary);

    String handledSummaryId = waitFor(loanClosedEventHandler.handle(
      buildLoanClosedEvent(userId, loan2Id)));

    context.assertEquals(initialUserSummary.getId(), handledSummaryId);

    initialUserSummary.getOpenLoans().removeIf(openLoan -> openLoan.getLoanId().equals(loan2Id));

    checkUserSummary(handledSummaryId, initialUserSummary, context);
  }
}
