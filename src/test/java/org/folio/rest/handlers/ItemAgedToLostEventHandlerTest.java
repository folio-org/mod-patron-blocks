package org.folio.rest.handlers;

import static java.util.Collections.singletonList;
import static org.folio.repository.UserSummaryRepository.USER_SUMMARY_TABLE_NAME;
import static org.folio.rest.utils.EntityBuilder.buildDefaultMetadata;
import static org.folio.rest.utils.EntityBuilder.buildItemAgedToLostEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;

import java.util.Date;

import org.folio.rest.jaxrs.model.ItemAgedToLostEvent;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.UserSummary;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ItemAgedToLostEventHandlerTest extends EventHandlerTestBase {
  private static final EventHandler<ItemAgedToLostEvent> itemAgedToLostEventHandler =
    new EventHandler<>(postgresClient);

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

    ItemAgedToLostEvent event = new ItemAgedToLostEvent()
      .withUserId(userId)
      .withLoanId(loanId)
      .withMetadata(buildDefaultMetadata());

    String summaryId = waitFor(itemAgedToLostEventHandler.handle(event));

    UserSummary userSummaryToCompare = new UserSummary()
      .withUserId(userId)
      .withOpenLoans(singletonList(new OpenLoan()
        .withLoanId(loanId)
        .withRecall(false)
        .withItemClaimedReturned(false)
        .withItemLost(true)));

    checkUserSummary(summaryId, userSummaryToCompare, context);
  }

  @Test
  public void shouldFlipItemLostFlagWhenUserSummaryExists(TestContext context) {
    String userId = randomId();
    String loanId = randomId();
    Date dueDate = new Date();

    String userSummaryId = waitFor(itemCheckedOutEventHandler.handle(
      buildItemCheckedOutEvent(userId, loanId, dueDate)));

    waitFor(itemAgedToLostEventHandler.handle(
      buildItemAgedToLostEvent(userId, loanId)));

    UserSummary expectedUserSummary = new UserSummary()
      .withUserId(userId)
      .withOpenLoans(singletonList(
        new OpenLoan()
          .withDueDate(dueDate)
          .withLoanId(loanId)
          .withRecall(false)
          .withItemClaimedReturned(false)
          .withItemLost(true)));

    checkUserSummary(userSummaryId, expectedUserSummary, context);
  }
}
