package org.folio.service;

import static org.folio.util.LogUtil.asJson;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.folio.repository.SynchronizationJobRepository;
import org.folio.rest.client.BulkDownloadClient;
import org.folio.rest.handlers.EventHandler;
import org.folio.rest.handlers.FeeFineBalanceChangedEventHandler;
import org.folio.rest.jaxrs.model.Account;
import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.jaxrs.model.SynchronizationJob;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class FeesFinesEventsGenerationService extends EventsGenerationService<Account> {

  private final EventHandler<FeeFineBalanceChangedEvent> feeFineBalanceChangedEventHandler;

  public FeesFinesEventsGenerationService(Map<String, String> headers, Vertx vertx,
    SynchronizationJobRepository syncRepository) {

    super(new BulkDownloadClient<>("/accounts", "accounts", Account.class, vertx, headers),
      syncRepository);
    this.feeFineBalanceChangedEventHandler = new FeeFineBalanceChangedEventHandler(headers, vertx);
  }

  @Override
  protected Future<Account> generateEvents(Account account) {
    log.debug("generateEvents:: parameters account: {}", () -> asJson(account));

    final String accountId = account.getId();
    userIds.add(account.getUserId());

    final FeeFineBalanceChangedEvent event = new FeeFineBalanceChangedEvent()
      .withBalance(BigDecimal.valueOf(account.getRemaining()))
      .withFeeFineId(account.getId())
      .withFeeFineTypeId(account.getFeeFineId())
      .withUserId(account.getUserId())
      .withLoanId(account.getLoanId())
      .withMetadata(account.getMetadata());

    return feeFineBalanceChangedEventHandler.handleSkippingUserSummaryUpdate(event)
      .onSuccess(r -> log.info("generateEvents:: Successfully generated events for account {}", accountId))
      .onFailure(t -> log.warn("generateEvents:: Failed to generate events for account {}", accountId, t))
      .map(account);
  }

  @Override
  protected Future<SynchronizationJob> updateStats(SynchronizationJob job, List<Account> accounts) {
    log.debug("updateStats:: parameters job: {}, accounts: list(size={})", () -> asJson(job),
      accounts::size);
    int processedFeesFinesCount = job.getNumberOfProcessedFeesFines() + accounts.size();
    return syncRepository.update(job.withNumberOfProcessedFeesFines(processedFeesFinesCount))
      .onSuccess(result -> log.info("updateStats:: result: {}", () -> asJson(result)));
  }

}
