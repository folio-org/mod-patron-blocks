package org.folio.rest.handlers;

import static org.folio.rest.utils.EntityBuilder.buildItemCheckedInEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;
import java.util.Optional;

import org.folio.rest.jaxrs.model.ItemCheckedInEvent;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.UserSummary;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.junit5.VertxTestContext;

public class ItemCheckedInHandlerTest extends EventHandlerTestBase {
  private EventHandler<ItemCheckedOutEvent> itemCheckedOutEventHandler;
  private EventHandler<ItemCheckedInEvent> itemCheckedInEventHandler;

//  @BeforeEach
//  void beforeEach() {
//    super.resetMocks();
//
//    initUserSummaryRepository();
//
//    itemCheckedOutEventHandler = new EventHandler<>(postgresClient);
//    itemCheckedInEventHandler = new EventHandler<>(postgresClient);
//
//    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
//    deleteAllFromTable(ITEM_CHECKED_OUT_EVENT_TABLE_NAME);
//    deleteAllFromTable(ITEM_CHECKED_IN_EVENT_TABLE_NAME);
//  }
//
//  @Test
//  void existingLoanIsRemovedFromSummary(VertxTestContext context) {
//    String userId = randomId();
//    String loan1Id = randomId();
//    String loan2Id = randomId();
//    DateTime dueDate1 = DateTime.now();
//    DateTime dueDate2 = DateTime.now();
//
//    waitFor(itemCheckedOutEventHandler.handle(buildItemCheckedOutEvent(userId, loan1Id,
//      dueDate1.toDate())));
//    waitFor(itemCheckedOutEventHandler.handle(buildItemCheckedOutEvent(userId, loan2Id,
//      dueDate2.toDate())));
//
//    UserSummary initialUserSummary = waitFor(userSummaryRepository.getByUserId(userId)
//      .map(Optional::get));
//
//    assertNotNull(initialUserSummary);
//
//    String handledSummaryId = waitFor(itemCheckedInEventHandler.handle(
//      buildItemCheckedInEvent(userId, loan2Id, new Date())));
//
//    assertEquals(initialUserSummary.getId(), handledSummaryId);
//
//    initialUserSummary.getOpenLoans().removeIf(openLoan -> openLoan.getLoanId().equals(loan2Id));
//
//    checkUserSummary(handledSummaryId, initialUserSummary);
//
//    context.completeNow();
//  }
//
//  @Test
//  void existingSummaryRemainsIntactWhenWhenLoanDoesNotExist(VertxTestContext context) {
//    final String userId = randomId();
//    String loanId = randomId();
//    DateTime dueDate = DateTime.now();
//
//    String savedSummaryId = waitFor(itemCheckedOutEventHandler.handle(
//      buildItemCheckedOutEvent(userId, loanId, dueDate.toDate())));
//
//    UserSummary initialUserSummary = waitFor(userSummaryRepository.getByUserId(userId)
//      .map(Optional::get));
//
//    waitFor(itemCheckedInEventHandler.handle(
//      buildItemCheckedInEvent(userId, randomId(), new Date())));
//
//    checkUserSummary(savedSummaryId, initialUserSummary);
//
//    context.completeNow();
//  }
//
//  @Test
//  void eventIsIgnoredWhenSummaryForUserDoesNotExist(VertxTestContext context) {
//    String userId = randomId();
//    ItemCheckedInEvent event = buildItemCheckedInEvent(userId, randomId(), new Date());
//
//    waitFor(itemCheckedInEventHandler.handle(event));
//
//    Optional<UserSummary> optionalSummary = waitFor(userSummaryRepository.getByUserId(userId));
//    assertFalse(optionalSummary.isPresent());
//
//    context.completeNow();
//  }

}
