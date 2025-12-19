package org.folio.rest.handlers;

import static java.util.Collections.singletonList;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemDeclaredLostEvent;

import java.util.Date;

import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.ItemDeclaredLostEvent;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class ItemDeclaredLostEventHandlerTest extends EventHandlerTestBase {
  private EventHandler<ItemDeclaredLostEvent> itemDeclaredLostEventHandler;
  private EventHandler<ItemCheckedOutEvent> itemCheckedOutEventHandler;

  @BeforeEach
  public void beforeEach() {
    super.resetMocks();

    initUserSummaryRepository();

    itemDeclaredLostEventHandler = new EventHandler<>(postgresClient);
    itemCheckedOutEventHandler = new EventHandler<>(postgresClient);

    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
    deleteAllFromTable(ITEM_DECLARED_LOST_EVENT_TABLE_NAME);
    deleteAllFromTable(ITEM_CHECKED_OUT_EVENT_TABLE_NAME);
  }

  @Test
  public void shouldFlipItemLostFlagWhenUserSummaryExists(VertxTestContext context) {
    String userId = randomId();
    String loanId = randomId();
    Date dueDate = new Date();

    String userSummaryId = waitFor(itemCheckedOutEventHandler.handle(
      buildItemCheckedOutEvent(userId, loanId, dueDate)));

    waitFor(itemDeclaredLostEventHandler.handle(
      buildItemDeclaredLostEvent(userId, loanId)));

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
