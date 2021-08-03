package org.folio.rest.handlers;

import static java.util.Collections.singletonList;
import static org.folio.repository.UserSummaryRepository.USER_SUMMARY_TABLE_NAME;
import static org.folio.rest.utils.EntityBuilder.buildDefaultMetadata;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemClaimedReturnedEvent;

import java.util.Date;

import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.ItemClaimedReturnedEvent;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.UserSummary;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ItemClaimedReturnedEventHandlerTest extends EventHandlerTestBase {
  private static final EventHandler<ItemClaimedReturnedEvent> itemClaimedReturnedEventHandler =
    new EventHandler<>(postgresClient);
  private static final EventHandler<ItemCheckedOutEvent> itemCheckedOutEventHandler =
    new EventHandler<>(postgresClient);

  @Before
  public void beforeEach() {
    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
  }

  @Test
  public void userSummaryShouldBeCreatedWhenDoesNotExist(TestContext context) {
    String userId = randomId();
    String loanId = randomId();

    ItemClaimedReturnedEvent event = new ItemClaimedReturnedEvent()
      .withUserId(userId)
      .withLoanId(loanId)
      .withMetadata(buildDefaultMetadata());

    String summaryId = waitFor(itemClaimedReturnedEventHandler.handle(event));

    UserSummary userSummaryToCompare = new UserSummary()
      .withUserId(userId)
      .withOpenLoans(singletonList(new OpenLoan()
        .withLoanId(loanId)
        .withRecall(false)
        .withItemClaimedReturned(true)
        .withItemLost(false)));

    checkUserSummary(summaryId, userSummaryToCompare, context);
  }

  @Test
  public void newOpenLoanShouldBeCreatedForExistingSummaryWhenDoesNotExist(TestContext context) {
    String userId = randomId();
    String loanId = randomId();

    UserSummary userSummary = new UserSummary()
      .withUserId(userId);

    waitFor(userSummaryRepository.save(userSummary));

    ItemClaimedReturnedEvent event = new ItemClaimedReturnedEvent()
      .withUserId(userId)
      .withLoanId(loanId)
      .withMetadata(buildDefaultMetadata());

    String summaryId = waitFor(itemClaimedReturnedEventHandler.handle(event));

    UserSummary userSummaryToCompare = new UserSummary()
      .withUserId(userId)
      .withOpenLoans(singletonList(new OpenLoan()
        .withLoanId(loanId)
        .withRecall(false)
        .withItemClaimedReturned(true)
        .withItemLost(false)));

    checkUserSummary(summaryId, userSummaryToCompare, context);
  }

  @Test
  public void shouldFlipItemClaimedReturnedFlagWhenUserSummaryExists(TestContext context) {
    String userId = randomId();
    String loanId = randomId();
    Date dueDate = new Date();

    UserSummary userSummary = new UserSummary()
      .withUserId(userId)
      .withOpenLoans(singletonList(
        new OpenLoan()
          .withDueDate(dueDate)
          .withLoanId(loanId)
          .withRecall(false)
          .withItemClaimedReturned(false)
          .withItemLost(false)));

    waitFor(userSummaryRepository.save(userSummary));

    waitFor(itemCheckedOutEventHandler.handle(buildItemCheckedOutEvent(userId, loanId, dueDate)));

    String summaryId = waitFor(itemClaimedReturnedEventHandler.handle(
      buildItemClaimedReturnedEvent(userId, loanId)));

    userSummary.getOpenLoans().get(0).setItemClaimedReturned(true);

    checkUserSummary(summaryId, userSummary, context);
  }
}
