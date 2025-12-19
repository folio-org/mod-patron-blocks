package org.folio.rest.handlers;

import static java.util.Collections.singletonList;
import static org.folio.rest.utils.EntityBuilder.buildItemAgedToLostEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;

import java.util.Date;

import org.folio.rest.jaxrs.model.ItemAgedToLostEvent;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.junit5.VertxTestContext;

public class ItemAgedToLostEventHandlerTest extends EventHandlerTestBase {
  private EventHandler<ItemAgedToLostEvent> itemAgedToLostEventHandler;
  private EventHandler<ItemCheckedOutEvent> itemCheckedOutEventHandler;

  @BeforeEach
  public void beforeEach() {
    super.resetMocks();

    initUserSummaryRepository();

    itemAgedToLostEventHandler = new EventHandler<>(postgresClient);
    itemCheckedOutEventHandler = new EventHandler<>(postgresClient);

    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
  }

  @Test
  public void shouldFlipItemLostFlagWhenUserSummaryExists(VertxTestContext context) {
    String userId = randomId();
    String loanId = randomId();
    Date dueDate = new Date();

    String userSummaryId = waitFor(itemCheckedOutEventHandler.handle(
      buildItemCheckedOutEvent(userId, loanId, dueDate)));

    waitFor(itemAgedToLostEventHandler.handle(buildItemAgedToLostEvent(userId, loanId)));

    UserSummary expectedUserSummary = new UserSummary()
      .withUserId(userId)
      .withOpenLoans(singletonList(
        new OpenLoan()
          .withDueDate(dueDate)
          .withLoanId(loanId)
          .withRecall(false)
          .withItemClaimedReturned(false)
          .withItemLost(true)));

    checkUserSummary(userSummaryId, expectedUserSummary);

    context.completeNow();
  }
}
