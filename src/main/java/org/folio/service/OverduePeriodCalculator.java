package org.folio.service;

import static java.util.Optional.ofNullable;
import static org.folio.util.LogUtil.asJson;
import static org.joda.time.Minutes.minutesBetween;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.util.Period;
import org.joda.time.DateTime;

public class OverduePeriodCalculator {
  private static final Logger log = LogManager.getLogger(OverduePeriodCalculator.class);
  private static final int ZERO_MINUTES = 0;

  private OverduePeriodCalculator() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static int calculateOverdueMinutes(OpenLoan openLoan) {
    log.debug("calculateOverdueMinutes:: parameters openLoan: {}", () -> asJson(openLoan));
    final DateTime systemTime = DateTime.now();

    int result = loanIsOverdue(openLoan, systemTime)
      ? calculateOverdueMinutes(openLoan, systemTime)
      : ZERO_MINUTES;
    log.info("calculateOverdueMinutes:: result: {}", result);
    return result;
  }

  private static boolean loanIsOverdue(OpenLoan openLoan, DateTime systemTime) {
    log.debug("loanIsOverdue:: parameters openLoan: {}, systemTime: {}",
      () -> asJson(openLoan), () -> systemTime);
    boolean result = openLoan.getDueDate().before(systemTime.toDate());
    log.info("loanIsOverdue:: result: {}", result);
    return result;
  }

  private static Integer calculateOverdueMinutes(OpenLoan openLoan, DateTime systemTime) {
    log.debug("calculateOverdueMinutes:: parameters openLoan: {}, systemTime: {}",
      () -> asJson(openLoan), () -> systemTime);
    DateTime dueDate = new DateTime(openLoan.getDueDate());
    int overdueMinutes = minutesBetween(dueDate, systemTime).getMinutes();

    int result = overdueMinutes > getGracePeriodMinutes(openLoan)
      ? overdueMinutes
      : ZERO_MINUTES;
    log.info("calculateOverdueMinutes:: result: {}", result);
    return result;
  }

  private static int getGracePeriodMinutes(OpenLoan openLoan) {
    return ofNullable(openLoan.getGracePeriod())
      .map(Period::from)
      .map(Period::toMinutes)
      .orElse(ZERO_MINUTES);
  }
}
