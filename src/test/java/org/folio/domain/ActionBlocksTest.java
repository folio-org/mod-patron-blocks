package org.folio.domain;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.folio.domain.ActionBlocks.byLimit;
import static org.folio.domain.Condition.MAX_NUMBER_OF_LOST_ITEMS;
import static org.folio.domain.Condition.MAX_OUTSTANDING_FEE_FINE_BALANCE;
import static org.folio.util.UuidHelper.randomId;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import java.util.stream.Stream;

import org.folio.rest.jaxrs.model.OpenFeeFine;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.PatronBlockLimit;
import org.folio.rest.jaxrs.model.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

public class ActionBlocksTest {

  static Stream<Arguments> byLimitShouldReturnNoBlocks() {
    final UserSummary userSummary = new UserSummary();
    final PatronBlockLimit limit = new PatronBlockLimit();

    return Stream.of(
      Arguments.of(null, null),
      Arguments.of(null, limit.withValue(1.23).withConditionId(MAX_NUMBER_OF_LOST_ITEMS.getId())),
      Arguments.of(userSummary, null),
      Arguments.of(userSummary, limit),
      Arguments.of(userSummary, limit.withValue(1.23))
    );
  }

  @ParameterizedTest
  @MethodSource
  public void byLimitShouldReturnNoBlocks(UserSummary summary, PatronBlockLimit limit) {
    ActionBlocks actionBlocks = byLimit(summary, limit);
    assertAllBlocksAreFalse(actionBlocks);
  }

  @Test
  void emptyReturnsNoBlocks() {
    assertAllBlocksAreFalse(ActionBlocks.empty());
  }

  @Test
  void byLimitReturnsEmptyBlocksWhenCalledWithUnknownConditionId() {
    PatronBlockLimit limit = new PatronBlockLimit()
      .withValue(1.23)
      .withConditionId(randomId());

    ActionBlocks actionBlocks = byLimit(new UserSummary(), limit);

    assertAllBlocksAreFalse(actionBlocks);
  }

  @ParameterizedTest
  @CsvSource({
    "false, false, false, false",
    "true, true, true, true",
    "true, false, false, true",
    "false, true, false, true",
    "false, false, true, true",
    "true, true, false, true",
    "false, true, true, true",
    "true, false, true, true"
  })
  public void isNotEmptyTest(boolean blockBorrowing, boolean blockRenewals, boolean blockRequests,
    boolean expectedResult) {

    ActionBlocks actionBlocks = new ActionBlocks(blockBorrowing, blockRenewals, blockRequests);
    boolean isNotEmpty = actionBlocks.isNotEmpty();

    if (expectedResult) {
      assertTrue(isNotEmpty);
    } else {
      assertFalse(isNotEmpty);
    }
  }

  @Test
  void byLimitReturnsNoBlocksForOutstandingFeeFineBalanceWhenItemIsClaimedReturned() {
    final PatronBlockLimit limit = new PatronBlockLimit()
      .withPatronGroupId(randomId())
      .withConditionId(MAX_OUTSTANDING_FEE_FINE_BALANCE.getId())
      .withValue(0.33);

    String firstLoanId = randomId();
    String secondLoanId = randomId();

    final UserSummary userSummary = new UserSummary()
      .withOpenLoans(asList(
        new OpenLoan()
          .withLoanId(firstLoanId)
          .withDueDate(new Date())
          .withItemLost(false)
          .withRecall(false)
          .withItemClaimedReturned(true),
        new OpenLoan()
          .withLoanId(secondLoanId)
          .withDueDate(new Date())
          .withItemLost(false)
          .withRecall(false)
          .withItemClaimedReturned(true)))
      .withOpenFeesFines(asList(
        new OpenFeeFine()
          .withBalance(BigDecimal.ONE)
          .withFeeFineId(randomId())
          .withFeeFineTypeId(randomId())
          .withLoanId(secondLoanId),
        new OpenFeeFine()
          .withBalance(BigDecimal.TEN)
          .withFeeFineId(randomId())
          .withFeeFineTypeId(randomId())
          .withLoanId(firstLoanId)));

    assertAllBlocksAreFalse(byLimit(userSummary, limit));
  }

  @Test
  void byLimitReturnsAllBlocksForBalanceWhenItemClaimedReturnedButFeeFineHasNoLoanId() {
    final PatronBlockLimit limit = new PatronBlockLimit()
      .withPatronGroupId(randomId())
      .withConditionId(MAX_OUTSTANDING_FEE_FINE_BALANCE.getId())
      .withValue(5.00);

    String loanId = randomId();

    final UserSummary userSummary = new UserSummary()
      .withOpenLoans(singletonList(
        new OpenLoan()
          .withLoanId(loanId)
          .withDueDate(new Date())
          .withItemLost(false)
          .withRecall(false)
          .withItemClaimedReturned(true)))
      .withOpenFeesFines(singletonList(
        new OpenFeeFine()
          .withBalance(BigDecimal.TEN)
          .withFeeFineId(randomId())
          .withFeeFineTypeId(randomId())
          .withLoanId(null)));

    assertAllBlocksAreTrue(byLimit(userSummary, limit));
  }

  private static void assertAllBlocksAreFalse(ActionBlocks actionBlocks) {
    assertFalse(actionBlocks.getBlockBorrowing());
    assertFalse(actionBlocks.getBlockRenewals());
    assertFalse(actionBlocks.getBlockRequests());
  }

  private static void assertAllBlocksAreTrue(ActionBlocks actionBlocks) {
    assertTrue(actionBlocks.getBlockBorrowing());
    assertTrue(actionBlocks.getBlockRenewals());
    assertTrue(actionBlocks.getBlockRequests());
  }
}
