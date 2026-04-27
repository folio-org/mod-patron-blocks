package org.folio.rest.handlers;

import static java.util.Collections.singletonList;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemClaimedReturnedEvent;

import java.util.Date;

import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.ItemClaimedReturnedEvent;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.junit5.VertxTestContext;

public class ItemClaimedReturnedEventHandlerTest extends EventHandlerTestBase {
//  private EventHandler<ItemClaimedReturnedEvent> itemClaimedReturnedEventHandler;
//  private EventHandler<ItemCheckedOutEvent> itemCheckedOutEventHandler;
//
//  @BeforeEach
//  void beforeEach() {
//    initUserSummaryRepository();
//
//    itemClaimedReturnedEventHandler = new EventHandler<>(postgresClient);
//    itemCheckedOutEventHandler = new EventHandler<>(postgresClient);
//
//    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
//  }
//
//  @Test
//  void shouldFlipItemClaimedReturnedFlagWhenUserSummaryExists(VertxTestContext context) {
//    String userId = randomId();
//    String loanId = randomId();
//    Date dueDate = new Date();
//
//    UserSummary userSummary = new UserSummary()
//      .withUserId(userId)
//      .withOpenLoans(singletonList(
//        new OpenLoan()
//          .withDueDate(dueDate)
//          .withLoanId(loanId)
//          .withRecall(false)
//          .withItemClaimedReturned(false)
//          .withItemLost(false)));
//
//    waitFor(userSummaryRepository.save(userSummary));
//
//    waitFor(itemCheckedOutEventHandler.handle(buildItemCheckedOutEvent(userId, loanId, dueDate)));
//
//    String summaryId = waitFor(itemClaimedReturnedEventHandler.handle(
//      buildItemClaimedReturnedEvent(userId, loanId)));
//
//    userSummary.getOpenLoans().get(0).setItemClaimedReturned(true);
//
//    checkUserSummary(summaryId, userSummary);
//
//    context.completeNow();
//  }
}
