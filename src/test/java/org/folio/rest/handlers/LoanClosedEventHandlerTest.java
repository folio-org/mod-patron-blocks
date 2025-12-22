package org.folio.rest.handlers;

import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.folio.rest.utils.EntityBuilder.buildLoanClosedEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Optional;

import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.LoanClosedEvent;
import org.folio.rest.jaxrs.model.UserSummary;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class LoanClosedEventHandlerTest extends EventHandlerTestBase {
  private EventHandler<LoanClosedEvent> loanClosedEventHandler;
  private EventHandler<ItemCheckedOutEvent> itemCheckedOutEventHandler;

  @BeforeEach
  void beforeEach() {
    super.resetMocks();

    initUserSummaryRepository();

    loanClosedEventHandler = new EventHandler<>(postgresClient);
    itemCheckedOutEventHandler = new EventHandler<>(postgresClient);

    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
  }

  @Test
  void loanShouldBeRemovedFromUserSummary(VertxTestContext context) {
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

    assertNotNull(initialUserSummary);

    String handledSummaryId = waitFor(loanClosedEventHandler.handle(
      buildLoanClosedEvent(userId, loan2Id)));

    assertEquals(initialUserSummary.getId(), handledSummaryId);

    initialUserSummary.getOpenLoans().removeIf(openLoan -> openLoan.getLoanId().equals(loan2Id));

    checkUserSummary(handledSummaryId, initialUserSummary);

    context.completeNow();
  }
}
