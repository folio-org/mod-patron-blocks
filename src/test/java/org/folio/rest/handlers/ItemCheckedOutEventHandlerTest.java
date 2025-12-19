package org.folio.rest.handlers;

import static org.folio.rest.utils.EntityBuilder.buildDefaultMetadata;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.Optional;

import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.UserSummary;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.junit5.VertxTestContext;

public class ItemCheckedOutEventHandlerTest extends EventHandlerTestBase {
  private EventHandler<ItemCheckedOutEvent> itemCheckedOutEventHandler;

  @BeforeEach
  public void beforeEach() {
    super.resetMocks();

    initUserSummaryRepository();

    itemCheckedOutEventHandler = new EventHandler<>(postgresClient);

    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
  }

  @Test
  public void userSummaryShouldBeCreatedWhenDoesNotExist(VertxTestContext context) {
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

    checkUserSummary(summaryId, userSummaryToCompare);

    context.completeNow();
  }

  @Test
  public void shouldAddOpenLoanWhenUserSummaryExists(VertxTestContext context) {
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

    checkUserSummary(summaryId, expectedUserSummary);

    context.completeNow();
  }

  @Test
  public void shouldNotChangeWhenOpenLoanWithTheSameLoanIdExists(VertxTestContext context) {
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

    assertEquals(initialUserSummary.getId(), updatedUserSummary.getId());

    checkUserSummary(updatedUserSummary.getId(), initialUserSummary);

    context.completeNow();
  }
}
