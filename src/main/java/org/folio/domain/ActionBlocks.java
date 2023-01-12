package org.folio.domain;

import static org.folio.domain.Condition.MAX_NUMBER_OF_ITEMS_CHARGED_OUT;
import static org.folio.domain.Condition.MAX_NUMBER_OF_LOST_ITEMS;
import static org.folio.domain.Condition.MAX_NUMBER_OF_OVERDUE_ITEMS;
import static org.folio.domain.Condition.MAX_NUMBER_OF_OVERDUE_RECALLS;
import static org.folio.domain.Condition.MAX_OUTSTANDING_FEE_FINE_BALANCE;
import static org.folio.domain.Condition.RECALL_OVERDUE_BY_MAX_NUMBER_OF_DAYS;
import static org.folio.util.LogUtil.asJson;

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
  private static final String LOG_TEMPLATE_BY_LIMIT_CONDITION = "byLimit:: condition is {}";

  private final boolean blockBorrowing;
  private final boolean blockRenewals;
  private final boolean blockRequests;

  public static ActionBlocks byLimit(UserSummary userSummary, PatronBlockLimit patronBlockLimit) {
    log.debug("byLimit:: parameters userSummary: {}, patronBlockLimit: {}",
      () -> asJson(userSummary), () -> asJson(patronBlockLimit));
    if (userSummary == null || patronBlockLimit == null || userSummary.getOpenLoans() == null) {
      log.warn("byLimit:: Failed to determine blocks because one of the parameters is null; " +
        "parameters userSummary: {}, patronBlockLimit: {}", () -> asJson(userSummary),
        () -> asJson(patronBlockLimit));
      return empty();
    }
    ActionBlocks actionBlocks = byLimit(userSummary, patronBlockLimit, userSummary.getOpenLoans().stream()
      .filter(openLoan -> openLoan.getLoanId() != null)
      .collect(Collectors.toMap(OpenLoan::getLoanId, r -> 0)));
    log.info("byLimit:: result: {}", () -> asJson(actionBlocks));
    return actionBlocks;
  }

  public static ActionBlocks byLimit(UserSummary userSummary, PatronBlockLimit patronBlockLimit,
    Map<String, Integer> overdueMinutes) {
    log.debug("byLimit:: parameters userSummary: {}, patronBlockLimit: {}, " +
      "overdueMinutes: {}", () -> asJson(userSummary), () -> asJson(patronBlockLimit),
      () -> overdueMinutes);
    if (userSummary == null || patronBlockLimit == null || overdueMinutes == null ||
      patronBlockLimit.getValue() == null || patronBlockLimit.getConditionId() == null) {
      log.warn("byLimit:: Failed to determine blocks because one of the parameters is null; " +
        "parameters userSummary: {}, patronBlockLimit: {}, patronBlockLimit.value, " +
        "patronBlockLimit.conditionId), overdueMinutes: {}", () -> asJson(userSummary),
        () -> asJson(patronBlockLimit), () -> overdueMinutes);
      return empty();
    }

    Condition condition = Condition.getById(patronBlockLimit.getConditionId());

    boolean blockBorrowing = false;
    boolean blockRenewals = false;
    boolean blockRequests = false;

    double limitValue = patronBlockLimit.getValue();

    if (condition == MAX_NUMBER_OF_ITEMS_CHARGED_OUT) {
      log.info(LOG_TEMPLATE_BY_LIMIT_CONDITION, condition);
      int numberOfOpenLoans = (int) userSummary.getOpenLoans().stream()
        .filter(ActionBlocks::itemIsNotClaimedReturned)
        .count();
      log.info("byLimit:: number of open loans is {}", numberOfOpenLoans);
      blockBorrowing = numberOfOpenLoans >= limitValue;
      blockRenewals = blockRequests = numberOfOpenLoans > limitValue;
    }
    else if (condition == MAX_NUMBER_OF_LOST_ITEMS) {
      log.info(LOG_TEMPLATE_BY_LIMIT_CONDITION, condition);
      blockBorrowing = blockRenewals = blockRequests = userSummary.getOpenLoans().stream()
        .filter(ActionBlocks::itemIsNotClaimedReturned)
        .filter(OpenLoan::getItemLost)
        .count() > limitValue;
    }
    else if (condition == MAX_NUMBER_OF_OVERDUE_ITEMS) {
      log.info(LOG_TEMPLATE_BY_LIMIT_CONDITION, condition);
      blockBorrowing = blockRenewals = blockRequests = userSummary.getOpenLoans().stream()
        .filter(ActionBlocks::itemIsNotClaimedReturned)
        .filter(openLoan -> ActionBlocks.isLoanOverdue(openLoan, overdueMinutes))
        .count() > limitValue;
    }
    else if (condition == MAX_NUMBER_OF_OVERDUE_RECALLS) {
      log.info(LOG_TEMPLATE_BY_LIMIT_CONDITION, condition);
      blockBorrowing = blockRenewals = blockRequests = userSummary.getOpenLoans().stream()
        .filter(ActionBlocks::itemIsNotClaimedReturned)
        .filter(openLoan -> ActionBlocks.isLoanOverdue(openLoan, overdueMinutes))
        .filter(OpenLoan::getRecall)
        .count() > limitValue;
    }
    else if (condition == RECALL_OVERDUE_BY_MAX_NUMBER_OF_DAYS) {
      log.info(LOG_TEMPLATE_BY_LIMIT_CONDITION, condition);
      blockBorrowing = blockRenewals = blockRequests = userSummary.getOpenLoans().stream()
        .filter(ActionBlocks::itemIsNotClaimedReturned)
        .filter(openLoan -> ActionBlocks.isLoanOverdue(openLoan, overdueMinutes))
        .filter(OpenLoan::getRecall)
        .map(openLoan -> ActionBlocks.getLoanOverdueDays(openLoan, overdueMinutes))
        .anyMatch(days -> days > limitValue);
    }
    else if (condition == MAX_OUTSTANDING_FEE_FINE_BALANCE) {
      log.info(LOG_TEMPLATE_BY_LIMIT_CONDITION, condition);
      blockBorrowing = blockRenewals = blockRequests = userSummary.getOpenFeesFines().stream()
        .filter(feeFine -> feeFineIsNotRelatedToItemClaimedReturned(feeFine, userSummary))
        .map(OpenFeeFine::getBalance)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .compareTo(BigDecimal.valueOf(limitValue)) > 0;
    }

    ActionBlocks actionBlocks = new ActionBlocks(blockBorrowing, blockRenewals, blockRequests);
    log.info("byLimit:: result: {}", () -> asJson(actionBlocks));
    return actionBlocks;
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

  private static int getLoanOverdueMinutes(OpenLoan openLoan, Map<String, Integer> overdueMinutes) {
    log.debug("getLoanOverdueMinutes:: parameters openLoan: {}, overdueMinutes: {}",
      () -> asJson(openLoan), () -> overdueMinutes);
    if (openLoan == null || openLoan.getLoanId() == null ||
      overdueMinutes.get(openLoan.getLoanId()) == null) {
      log.warn("getLoanOverdueMinutes:: Failed to get loan overdue minutes because one of the" +
        " values is null; openLoan: {}, openLoan.loanId, overdueMinutes: {}, " +
          "overdueMinutes.get(openLoan.loanId)", () -> asJson(openLoan), () -> overdueMinutes);
      return 0;
    }
    int loanOverdueMinutes = overdueMinutes.get(openLoan.getLoanId());
    log.info("getLoanOverdueMinutes:: result: {}", loanOverdueMinutes);
    return loanOverdueMinutes;
  }

  private static boolean isLoanOverdue(OpenLoan openLoan, Map<String, Integer> overdueMinutes) {
    log.debug("isLoanOverdue: parameters openLoan: {}, overdueMinutes: {}",
      () -> asJson(openLoan), () -> overdueMinutes);
    boolean loanIsOverdue = getLoanOverdueMinutes(openLoan, overdueMinutes) > 0;
    log.info("isLoanOverdue:: result: {}", loanIsOverdue);
    return loanIsOverdue;
  }

  private static int getLoanOverdueDays(OpenLoan openLoan, Map<String, Integer> overdueMinutes) {
    log.debug("getLoanOverdueDays:: parameters openLoan: {}, overdueMinutes: {}",
      () -> asJson(openLoan), () -> overdueMinutes);
    int loanOverdueDays = (int) Math.ceil(
      ((double) getLoanOverdueMinutes(openLoan, overdueMinutes)) / NUMBER_OF_MINUTES_IN_ONE_DAY);
    log.info("getLoanOverdueDays:: result: {}", loanOverdueDays);
    return loanOverdueDays;
  }

  private static boolean itemIsNotClaimedReturned(OpenLoan openLoan) {
    log.debug("itemIsNotClaimedReturned:: parameters openLoan: {}",
      () -> asJson(openLoan));
    boolean itemIsNotClaimedReturned = !openLoan.getItemClaimedReturned();
    log.info("itemIsNotClaimedReturned:: result: {}", itemIsNotClaimedReturned);
    return itemIsNotClaimedReturned;
  }

  private static boolean feeFineIsNotRelatedToItemClaimedReturned(OpenFeeFine feeFine,
    UserSummary userSummary) {
    log.debug("feeFineIsNotRelatedToItemClaimedReturned:: parameters feeFine: {}, userSummary: {}",
      () -> asJson(feeFine), () -> asJson(userSummary));
    if (feeFine.getLoanId() == null) {
      log.info("feeFineIsNotRelatedToItemClaimedReturned:: feeFine: {}, feeFine.getLoanId is null",
        () -> asJson(feeFine));
      return true;
    }

    boolean feeFineIsNotRelatedToItemClaimedReturned = userSummary.getOpenLoans().stream()
      .filter(OpenLoan::getItemClaimedReturned)
      .map(OpenLoan::getLoanId)
      .filter(Objects::nonNull)
      .noneMatch(loanId -> loanId.equals(feeFine.getLoanId()));
    log.info("feeFineIsNotRelatedToItemClaimedReturned:: result: {}",
      feeFineIsNotRelatedToItemClaimedReturned);
    return feeFineIsNotRelatedToItemClaimedReturned;
  }
}
