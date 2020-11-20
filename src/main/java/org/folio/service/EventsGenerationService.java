package org.folio.service;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.model.SynchronizationJob.Scope.USER;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.folio.repository.SynchronizationJobRepository;
import org.folio.rest.client.BulkDownloadClient;
import org.folio.rest.jaxrs.model.SynchronizationJob;
import org.folio.util.UuidHelper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public abstract class EventsGenerationService<T> {
  protected static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final int PAGE_SIZE = 100;
  private static final String FILTER_BY_ID_QUERY_TEMPLATE = " and id > %s";

  protected final SynchronizationJobRepository syncRepository;
  private final BulkDownloadClient<T> bulkDownloadClient;

  protected EventsGenerationService(BulkDownloadClient<T> bulkDownloadClient,
    SynchronizationJobRepository syncRepository) {

    this.syncRepository = syncRepository;
    this.bulkDownloadClient = bulkDownloadClient;
  }

  public Future<SynchronizationJob> generateEvents(SynchronizationJob job) {
    return generateEventsRecursively(job, buildQuery(job), null);
  }

  private Future<SynchronizationJob> generateEventsRecursively(SynchronizationJob job,
    String originalQuery, String lastFetchedId) {

    String query = lastFetchedId != null
      ? originalQuery + String.format(FILTER_BY_ID_QUERY_TEMPLATE, lastFetchedId)
      : originalQuery;

    AtomicReference<List<T>> currentPage = new AtomicReference<>(new ArrayList<>());

    return bulkDownloadClient.fetchPage(query, PAGE_SIZE)
      .onSuccess(currentPage::set)
      .compose(this::generateEventsForPage)
      .onComplete(this::logEventsGenerationResult)
      .compose(page -> updateStats(job, page))
      .recover(error -> handleError(job, error))
      .compose(syncJob -> fetchNextPage(syncJob, currentPage.get(), originalQuery));
  }

  private Future<List<T>> generateEventsForPage(List<T> page) {
    return page.stream()
      .map(this::generateEvents)
      .reduce(succeededFuture(), (prev, next) -> prev.compose(r -> next))
      .map(page);
  }

  private Future<SynchronizationJob> fetchNextPage(SynchronizationJob job, List<T> lastPage,
    String query) {

    if (lastPage.size() < PAGE_SIZE) {
      log.info("{} finished processing last page", getClass().getSimpleName());
      return succeededFuture(job);
    }

    T lastElement = lastPage.get(lastPage.size() - 1);
    String lastElementId = JsonObject.mapFrom(lastElement).getString("id");
    UuidHelper.validateUUID(lastElementId, true);

    return generateEventsRecursively(job, query, lastElementId);
  }

  private Future<SynchronizationJob> handleError(SynchronizationJob syncJob, Throwable error) {
    syncJob.getErrors().add(error.getLocalizedMessage());
    return syncRepository.update(syncJob);
  }

  private void logEventsGenerationResult(AsyncResult<List<T>> result) {
    String className = getClass().getSimpleName();

    if (result.failed()) {
      log.error("{} failed to generate events: {}", className, result.cause().getMessage());
    } else {
      log.info("{} successfully generated events for {} entities", className, result.result().size());
    }
  }

  private static String buildQuery(SynchronizationJob job) {
    StringBuilder query = new StringBuilder("status.name==Open");
    if (job.getScope() == USER) {
      query.append(" and userId==").append(job.getUserId());
    }

    return query.toString();
  }

  protected abstract Future<T> generateEvents(T entity);

  protected abstract Future<SynchronizationJob> updateStats(SynchronizationJob job,
    List<T> entities);
}
