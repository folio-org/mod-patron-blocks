package org.folio.rest.utils;

import static org.folio.util.UuidHelper.randomId;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.folio.domain.SynchronizationStatus;
import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.jaxrs.model.ItemAgedToLostEvent;
import org.folio.rest.jaxrs.model.ItemCheckedInEvent;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.ItemClaimedReturnedEvent;
import org.folio.rest.jaxrs.model.ItemDeclaredLostEvent;
import org.folio.rest.jaxrs.model.LoanDueDateChangedEvent;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.OpenFeeFine;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.SynchronizationJob;
import org.folio.rest.jaxrs.model.UserSummary;

public class EntityBuilder {

  public static OpenLoan buildLoan(boolean recall, boolean itemLost, Date dueDate) {
    return new OpenLoan()
      .withLoanId(randomId())
      .withDueDate(dueDate)
      .withRecall(recall)
      .withItemLost(itemLost);
  }

  public static OpenFeeFine buildFeeFine(String loanId, String feeFineId, String feeFineTypeId,
    BigDecimal balance) {

    return new OpenFeeFine()
      .withLoanId(loanId)
      .withFeeFineId(feeFineId)
      .withFeeFineTypeId(feeFineTypeId)
      .withBalance(balance);
  }

  public static UserSummary buildUserSummary(String userId, List<OpenFeeFine> feesFines,
    List<OpenLoan> openLoans) {

    return new UserSummary()
      .withId(randomId())
      .withUserId(userId)
      .withOpenFeesFines(feesFines)
      .withOpenLoans(openLoans);
  }

  public static Metadata buildDefaultMetadata() {
    return new Metadata().withCreatedDate(new Date());
  }

  public static ItemCheckedOutEvent buildItemCheckedOutEvent(String userId, String loanId,
    Date dueDate) {

    return new ItemCheckedOutEvent()
      .withUserId(userId)
      .withLoanId(loanId)
      .withDueDate(dueDate)
      .withMetadata(buildDefaultMetadata());
  }

  public static ItemCheckedInEvent buildItemCheckedInEvent(String userId, String loanId,
    Date returnDate) {

    return new ItemCheckedInEvent()
      .withUserId(userId)
      .withLoanId(loanId)
      .withReturnDate(returnDate)
      .withMetadata(buildDefaultMetadata());
  }

  public static ItemClaimedReturnedEvent buildItemClaimedReturnedEvent(String userId,
    String loanId) {

    return new ItemClaimedReturnedEvent()
      .withUserId(userId)
      .withLoanId(loanId)
      .withMetadata(buildDefaultMetadata());
  }

  public static ItemDeclaredLostEvent buildItemDeclaredLostEvent(String userId,
    String loanId) {

    return new ItemDeclaredLostEvent()
      .withUserId(userId)
      .withLoanId(loanId)
      .withMetadata(buildDefaultMetadata());
  }

  public static ItemAgedToLostEvent buildItemAgedToLostEvent(String userId,
    String loanId) {

    return new ItemAgedToLostEvent()
      .withUserId(userId)
      .withLoanId(loanId)
      .withMetadata(buildDefaultMetadata());
  }

  public static FeeFineBalanceChangedEvent buildFeeFineBalanceChangedEvent(String userId,
    String loanId, String feeFineId, String feeFineTypeId, BigDecimal balance) {

    return new FeeFineBalanceChangedEvent()
      .withUserId(userId)
      .withLoanId(loanId)
      .withFeeFineId(feeFineId)
      .withFeeFineTypeId(feeFineTypeId)
      .withBalance(balance)
      .withMetadata(buildDefaultMetadata());
  }

  public static LoanDueDateChangedEvent buildLoanDueDateChangedEvent(String userId,
    String loanId, Date dueDate, boolean dueDateChangedByRecall) {

    return new LoanDueDateChangedEvent()
      .withUserId(userId)
      .withLoanId(loanId)
      .withDueDate(dueDate)
      .withDueDateChangedByRecall(dueDateChangedByRecall)
      .withMetadata(buildDefaultMetadata());
  }

  public static SynchronizationJob buildSynchronizationJob(SynchronizationJob.Scope scope,
    String userId, SynchronizationStatus status, int totalNumberOfLoans,
    int totalNumberOfFeesFines, int numberOfProcessedLoans, int numberOfProcessedFeesFines) {

    return new SynchronizationJob()
      .withId(randomId())
      .withUserId(userId)
      .withScope(scope)
      .withStatus(status.getValue())
      .withTotalNumberOfLoans(totalNumberOfLoans)
      .withTotalNumberOfFeesFines(totalNumberOfFeesFines)
      .withNumberOfProcessedLoans(numberOfProcessedLoans)
      .withNumberOfProcessedFeesFines(numberOfProcessedFeesFines);
  }
}
