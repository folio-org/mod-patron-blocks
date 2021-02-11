package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.joda.time.Minutes.minutesBetween;

import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exception.OverduePeriodCalculatorException;
import org.folio.rest.client.CirculationStorageClient;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.model.LoanPolicy;
import org.folio.rest.jaxrs.model.LoansPolicy;
import org.folio.util.Period;
import org.joda.time.DateTime;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;

public class OverduePeriodCalculatorService {
  private static final Logger log = LogManager.getLogger(OverduePeriodCalculatorService.class);

  private static final int ZERO_MINUTES = 0;

  private final CirculationStorageClient circulationStorageClient;

  public OverduePeriodCalculatorService(Vertx vertx, Map<String, String> okapiHeaders) {
    this.circulationStorageClient = new CirculationStorageClient(vertx, okapiHeaders);
  }

  public OverduePeriodCalculatorService(CirculationStorageClient circulationStorageClient) {
    this.circulationStorageClient = circulationStorageClient;
  }

  public Future<Integer> getMinutes(Loan loan, DateTime systemTime) {
    if (loan == null || loan.getLoanPolicyId() == null || loan.getDueDate() == null
      || systemTime == null) {

      String message = "Failed to calculate overdue minutes. One of the parameters is null: " +
        "loan, overdue fine policy, loan policy, due date, system time";
      log.error(message);
      return failedFuture(new OverduePeriodCalculatorException(message));
    }

    return succeededFuture(new CalculationContext())
      .map(ctx -> ctx.withLoan(loan))
      .compose(ctx -> circulationStorageClient.findLoanPolicyById(loan.getLoanPolicyId())
        .map(ctx::withLoanPolicy))
      .compose(ctx -> {
        if (loanIsOverdue(ctx.getLoan(), systemTime)) {
          return succeededFuture(ctx)
            .compose(r -> calculateOverdueMinutes(ctx.getLoan(), systemTime)
              .map(om -> adjustOverdueWithGracePeriod(ctx, om)));
        }
        else {
          return succeededFuture(ZERO_MINUTES);
        }
      });
  }

  private boolean loanIsOverdue(Loan loan, DateTime systemTime) {
    return loan.getDueDate().before(systemTime.toDate());
  }

  private Future<Integer> calculateOverdueMinutes(Loan loan, DateTime systemTime) {
    int overdueMinutes = minutesBetween(new DateTime(loan.getDueDate()), systemTime).getMinutes();
    return succeededFuture(overdueMinutes);
  }

  private Integer adjustOverdueWithGracePeriod(CalculationContext context, int overdueMinutes) {
    return overdueMinutes > getGracePeriodMinutes(context) ? overdueMinutes : ZERO_MINUTES;
  }

  private int getGracePeriodMinutes(CalculationContext context) {
    return Optional.ofNullable(context)
      .map(CalculationContext::getLoanPolicy)
      .map(LoanPolicy::getLoansPolicy)
      .map(LoansPolicy::getGracePeriod)
      .map(gp -> Period.from(gp.getDuration(), gp.getIntervalId().value()))
      .map(Period::toMinutes)
      .orElse(ZERO_MINUTES);
  }

  @With
  @AllArgsConstructor
  @NoArgsConstructor(force = true)
  @Getter
  private static class CalculationContext {
    final Loan loan;
    final LoanPolicy loanPolicy;
  }
}
