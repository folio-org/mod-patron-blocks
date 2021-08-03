package org.folio.service;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import java.util.List;
import java.util.Map;

import org.folio.repository.SynchronizationJobRepository;
import org.folio.rest.client.BulkDownloadClient;
import org.folio.rest.handlers.EventHandler;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.ItemClaimedReturnedEvent;
import org.folio.rest.jaxrs.model.ItemDeclaredLostEvent;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.model.LoanDueDateChangedEvent;
import org.folio.rest.jaxrs.model.SynchronizationJob;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class LoanEventsGenerationService extends EventsGenerationService<Loan> {

  private static final String DECLARED_LOST_STATUS = "Declared lost";
  private static final String CLAIMED_RETURNED_STATUS = "Claimed returned";

  private final EventHandler<ItemCheckedOutEvent> checkedOutEventHandler;
  private final EventHandler<ItemDeclaredLostEvent> declaredLostEventHandler;
  private final EventHandler<ItemClaimedReturnedEvent> claimedReturnedEventHandler;
  private final EventHandler<LoanDueDateChangedEvent> dueDateChangedEventHandler;

  public LoanEventsGenerationService(Map<String, String> okapiHeaders, Vertx vertx,
    SynchronizationJobRepository syncRepository) {

    super(new BulkDownloadClient<>("/loan-storage/loans", "loans", Loan.class, vertx, okapiHeaders),
      syncRepository);

    this.checkedOutEventHandler = new EventHandler<>(okapiHeaders, vertx);
    this.declaredLostEventHandler = new EventHandler<>(okapiHeaders, vertx);
    this.claimedReturnedEventHandler = new EventHandler<>(okapiHeaders, vertx);
    this.dueDateChangedEventHandler = new EventHandler<>(okapiHeaders, vertx);
  }

  @Override
  protected Future<Loan> generateEvents(Loan loan) {
    final String loanId = loan.getId();
    log.info("Generating events for loan {}...", loanId);

    userIds.add(loan.getUserId());

    return succeededFuture(loan)
      .compose(v -> generateItemCheckedOutEvent(loan))
      .compose(v -> generateClaimedReturnedEvent(loan))
      .compose(v -> generateDeclaredLostEvent(loan))
      .compose(v -> generateDueDateChangedEvent(loan))
      .onSuccess(r -> log.info("Successfully generated events for loan {}", loanId))
      .onFailure(t -> log.error("Failed to generate events for loan {}: {}", loanId,
        t.getLocalizedMessage()))
      .map(loan);
  }

  private Future<String> generateItemCheckedOutEvent(Loan loan) {
   return checkedOutEventHandler.handleSkippingUserSummaryUpdate(new ItemCheckedOutEvent()
      .withLoanId(loan.getId())
      .withUserId(loan.getUserId())
      .withDueDate(loan.getDueDate())
      .withMetadata(loan.getMetadata()));
  }

  private Future<String> generateClaimedReturnedEvent(Loan loan) {
    if (CLAIMED_RETURNED_STATUS.equalsIgnoreCase(loan.getItemStatus())) {
      return claimedReturnedEventHandler.handleSkippingUserSummaryUpdate(new ItemClaimedReturnedEvent()
        .withLoanId(loan.getId())
        .withUserId(loan.getUserId())
        .withMetadata(loan.getMetadata()));
    }
    return succeededFuture(null);
  }

  private Future<String> generateDeclaredLostEvent(Loan loan) {
    if (DECLARED_LOST_STATUS.equals(loan.getItemStatus())) {
      return declaredLostEventHandler.handleSkippingUserSummaryUpdate(new ItemDeclaredLostEvent()
          .withLoanId(loan.getId())
          .withUserId(loan.getUserId())
          .withMetadata(loan.getMetadata()));
    }
    return succeededFuture(null);
  }

  private Future<Void> generateDueDateChangedEvent(Loan loan) {
    if (isTrue(loan.getDueDateChangedByRecall())) {
      dueDateChangedEventHandler.handleSkippingUserSummaryUpdate(new LoanDueDateChangedEvent()
          .withLoanId(loan.getId())
          .withUserId(loan.getUserId())
          .withDueDate(loan.getDueDate())
          .withDueDateChangedByRecall(loan.getDueDateChangedByRecall())
          .withMetadata(loan.getMetadata()));
    }
    return succeededFuture(null);
  }

  @Override
  protected Future<SynchronizationJob> updateStats(SynchronizationJob job, List<Loan> loans){
    int processedLoansCount = job.getNumberOfProcessedLoans() + loans.size();
    return syncRepository.update(job.withNumberOfProcessedLoans(processedLoansCount));
  }
}
