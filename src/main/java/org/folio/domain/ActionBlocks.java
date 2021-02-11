package org.folio.domain;

import static org.folio.domain.Condition.MAX_NUMBER_OF_ITEMS_CHARGED_OUT;
import static org.folio.domain.Condition.MAX_NUMBER_OF_LOST_ITEMS;
import static org.folio.domain.Condition.MAX_NUMBER_OF_OVERDUE_ITEMS;
import static org.folio.domain.Condition.MAX_NUMBER_OF_OVERDUE_RECALLS;
import static org.folio.domain.Condition.MAX_OUTSTANDING_FEE_FINE_BALANCE;
import static org.folio.domain.Condition.RECALL_OVERDUE_BY_MAX_NUMBER_OF_DAYS;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.OpenFeeFine;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.PatronBlockLimit;
import org.folio.rest.jaxrs.model.UserSummary;

public class ActionBlocks {
  private static final Logger log = LogManager.getLogger(ActionBlocks.class);
  private static final Double NUMBER_OF_MINUTES_IN_ONE_DAY = 1440.0;

  private final boolean blockBorrowing;
  private final boolean blockRenewals;
  private final boolean blockRequests;

  public static ActionBlocks byLimit(UserSummary userSummary, PatronBlockLimit limit) {
    if (userSummary == null || limit == null || userSummary.getOpenLoans() == null) {
      log.error("Failed to determine blocks: one of the parameters is null");
      return empty();
    }

    return byLimit(userSummary, limit, userSummary.getOpenLoans().stream()
      .filter(openLoan -> openLoan.getLoanId() != null)
      .collect(Collectors.toMap(OpenLoan::getLoanId, r -> 0)));
  }

  public static ActionBlocks byLimit(UserSummary userSummary, PatronBlockLimit limit,
    Map<String, Integer> overdueMinutes) {

    if (userSummary == null || limit == null || overdueMinutes == null || limit.getValue() == null
      || limit.getConditionId() == null) {

      log.error("Failed to determine blocks: one of the parameters is null");
      return empty();
    }

    Condition condition = Condition.getById(limit.getConditionId());

    boolean blockBorrowing = false;
    boolean blockRenewals = false;
    boolean blockRequests = false;

    double limitValue = limit.getValue();

    if (condition == MAX_NUMBER_OF_ITEMS_CHARGED_OUT) {
      int numberOfOpenLoans = (int) userSummary.getOpenLoans().stream()
        .filter(ActionBlocks::itemIsNotClaimedReturned)
        .count();

      blockBorrowing = numberOfOpenLoans >= limitValue;
      blockRenewals = blockRequests = numberOfOpenLoans > limitValue;
    }
    else if (condition == MAX_NUMBER_OF_LOST_ITEMS) {
      blockBorrowing = blockRenewals = blockRequests = userSummary.getOpenLoans().stream()
        .filter(ActionBlocks::itemIsNotClaimedReturned)
        .filter(OpenLoan::getItemLost)
        .count() > limitValue;
    }
    else if (condition == MAX_NUMBER_OF_OVERDUE_ITEMS) {
      blockBorrowing = blockRenewals = blockRequests = userSummary.getOpenLoans().stream()
        .filter(ActionBlocks::itemIsNotClaimedReturned)
        .filter(openLoan -> ActionBlocks.isLoanOverdue(openLoan, overdueMinutes))
        .count() > limitValue;
    }
    else if (condition == MAX_NUMBER_OF_OVERDUE_RECALLS) {
      blockBorrowing = blockRenewals = blockRequests = userSummary.getOpenLoans().stream()
        .filter(ActionBlocks::itemIsNotClaimedReturned)
        .filter(openLoan -> ActionBlocks.isLoanOverdue(openLoan, overdueMinutes))
        .filter(OpenLoan::getRecall)
        .count() > limitValue;
    }
    else if (condition == RECALL_OVERDUE_BY_MAX_NUMBER_OF_DAYS) {
      blockBorrowing = blockRenewals = blockRequests = userSummary.getOpenLoans().stream()
        .filter(ActionBlocks::itemIsNotClaimedReturned)
        .filter(openLoan -> ActionBlocks.isLoanOverdue(openLoan, overdueMinutes))
        .filter(OpenLoan::getRecall)
        .map(openLoan -> ActionBlocks.getLoanOverdueDays(openLoan, overdueMinutes))
        .anyMatch(days -> days > limitValue);
    }
    else if (condition == MAX_OUTSTANDING_FEE_FINE_BALANCE) {
      blockBorrowing = blockRenewals = blockRequests = userSummary.getOpenFeesFines().stream()
        .filter(feeFine -> feeFineIsNotRelatedToItemClaimedReturned(feeFine, userSummary))
        .map(OpenFeeFine::getBalance)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .compareTo(BigDecimal.valueOf(limitValue)) > 0;
    }

    return new ActionBlocks(blockBorrowing, blockRenewals, blockRequests);
  }

  public ActionBlocks(boolean blockBorrowing, boolean blockRenewals, boolean blockRequests) {
    this.blockBorrowing = blockBorrowing;
    this.blockRenewals = blockRenewals;
    this.blockRequests = blockRequests;
  }

  public boolean getBlockBorrowing() {
    return blockBorrowing;
  }

  public boolean getBlockRenewals() {
    return blockRenewals;
  }

  public boolean getBlockRequests() {
    return blockRequests;
  }

  public boolean isNotEmpty() {
    return blockBorrowing || blockRenewals || blockRequests;
  }

  public static ActionBlocks and(ActionBlocks left, ActionBlocks right) {
    return new ActionBlocks(left.blockBorrowing && right.blockBorrowing,
      left.blockRenewals && right.blockRenewals,
      left.blockRequests && right.blockRequests);
  }

  public static ActionBlocks empty() {
    return new ActionBlocks(false, false, false);
  }

  private static int getLoanOverdueMinutes(OpenLoan loan, Map<String, Integer> overdueMinutes) {
    if (loan == null || loan.getLoanId() == null || overdueMinutes.get(loan.getLoanId()) == null) {
      log.error("Failed to get loan overdue minutes: one of the parameters is null");
      return 0;
    }

    return overdueMinutes.get(loan.getLoanId());
  }

  private static boolean isLoanOverdue(OpenLoan loan, Map<String, Integer> overdueMinutes) {
    return getLoanOverdueMinutes(loan, overdueMinutes) > 0;
  }

  private static int getLoanOverdueDays(OpenLoan loan, Map<String, Integer> overdueMinutes) {
    return (int) Math.ceil(
      ((double) getLoanOverdueMinutes(loan, overdueMinutes)) / NUMBER_OF_MINUTES_IN_ONE_DAY);
  }

  private static boolean itemIsNotClaimedReturned(OpenLoan loan) {
    return !loan.getItemClaimedReturned();
  }

  private static boolean feeFineIsNotRelatedToItemClaimedReturned(OpenFeeFine feeFine,
    UserSummary userSummary) {

    if (feeFine.getLoanId() == null) {
      return true;
    }

    return userSummary.getOpenLoans().stream()
      .filter(OpenLoan::getItemClaimedReturned)
      .map(OpenLoan::getLoanId)
      .filter(Objects::nonNull)
      .noneMatch(loanId -> loanId.equals(feeFine.getLoanId()));
  }
}
