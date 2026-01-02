package org.folio.rest.handlers;

import static java.util.Collections.singletonList;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.folio.rest.utils.EntityBuilder.buildLoanDueDateChangedEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.Optional;

import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.LoanDueDateChangedEvent;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.UserSummary;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.junit5.VertxTestContext;

public class LoanDueDateChangedEventHandlerTest extends EventHandlerTestBase {

  private EventHandler<LoanDueDateChangedEvent> loanDueDateChangedEventHandler;
  private EventHandler<ItemCheckedOutEvent> itemCheckedOutEventHandler;

  @BeforeEach
  void beforeEach() {
    super.resetMocks();

    initUserSummaryRepository();

    loanDueDateChangedEventHandler = new EventHandler<>(postgresClient);
    itemCheckedOutEventHandler = new EventHandler<>(postgresClient);

    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
    deleteAllFromTable(ITEM_CHECKED_OUT_EVENT_TABLE_NAME);
  }

  @Test
  void existingLoanIsUpdated(VertxTestContext context) {
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
    assertEquals(summaryBeforeEvent.getId(), updatedSummaryId);

    UserSummary expectedUserSummary = summaryBeforeEvent
      .withOpenLoans(singletonList(new OpenLoan()
          .withLoanId(loanId)
          .withDueDate(event.getDueDate())
          .withRecall(true)));

    checkUserSummary(updatedSummaryId, expectedUserSummary);

    context.completeNow();
  }
}
