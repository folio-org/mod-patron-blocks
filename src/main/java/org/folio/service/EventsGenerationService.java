package org.folio.service;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.model.SynchronizationJob.Scope.USER;
import static org.folio.util.LogUtil.asJson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.repository.SynchronizationJobRepository;
import org.folio.rest.client.BulkDownloadClient;
import org.folio.rest.jaxrs.model.SynchronizationJob;
import org.folio.util.UuidHelper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public abstract class EventsGenerationService<T> {
  protected static final Logger log = LogManager.getLogger(EventsGenerationService.class);
  private static final int PAGE_SIZE = 100;
  private static final String FILTER_BY_ID_QUERY_TEMPLATE = " and id > %s";

  protected final SynchronizationJobRepository syncRepository;
  private final BulkDownloadClient<T> bulkDownloadClient;

  Set<String> userIds = new HashSet<>();

  protected EventsGenerationService(BulkDownloadClient<T> bulkDownloadClient,
    SynchronizationJobRepository syncRepository) {

    this.syncRepository = syncRepository;
    this.bulkDownloadClient = bulkDownloadClient;
  }

  public Future<SynchronizationJob> generateEvents(SynchronizationJob job) {
    log.debug("generateEvents:: parameters job: {}", () -> asJson(job));
    return generateEventsRecursively(job, buildQuery(job), null);
  }

  private Future<SynchronizationJob> generateEventsRecursively(SynchronizationJob job,
    String originalQuery, String lastFetchedId) {

    log.debug("generateEventsRecursively:: parameters job: {}, originalQuery: {}," +
      " lastFetchedId: {}", () -> asJson(job), () -> originalQuery, () -> lastFetchedId);

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
      .compose(syncJob -> fetchNextPage(syncJob, currentPage.get(), originalQuery))
      .onSuccess(result -> log.info("generateEventsRecursively:: result: {}",
        () -> asJson(result)));
  }

  private Future<List<T>> generateEventsForPage(List<T> page) {
    log.debug("generateEventsForPage:: parameters page: list(size={})", page.size());
    return page.stream()
      .map(this::generateEvents)
      .reduce(succeededFuture(), (prev, next) -> prev.compose(r -> next))
      .map(page)
      .onSuccess(result -> log.info("generateEventsForPage:: result: list(size={})", result.size()));
  }

  private Future<SynchronizationJob> fetchNextPage(SynchronizationJob job, List<T> lastPage,
    String query) {
    log.debug("fetchNextPage:: parameters job: {}, lastPage: list(size={}), query: {}",
      () -> asJson(job), lastPage::size, () -> query);

    if (lastPage.size() < PAGE_SIZE) {
      log.info("fetchNextPage:: {} finished processing last page", getClass().getSimpleName());
      return succeededFuture(job);
    }

    T lastElement = lastPage.get(lastPage.size() - 1);
    String lastElementId = JsonObject.mapFrom(lastElement).getString("id");
    UuidHelper.validateUUID(lastElementId, true);

    return generateEventsRecursively(job, query, lastElementId)
      .onSuccess(result -> log.info("fetchNextPage:: result: {}", () -> asJson(job)));
  }

  private Future<SynchronizationJob> handleError(SynchronizationJob syncJob, Throwable error) {
    String className = getClass().getSimpleName();
    log.warn("handleError:: {} failed to generate events", className, error);
    syncJob.getErrors().add(error.getLocalizedMessage());
    return syncRepository.update(syncJob);
  }

  private void logEventsGenerationResult(AsyncResult<List<T>> result) {
    String className = getClass().getSimpleName();

    if (result.failed()) {
      log.warn("logEventsGenerationResult:: {} failed to generate events", className,
        result.cause());
    } else {
      log.info("logEventsGenerationResult:: {} successfully generated events for {} entities",
        className, result.result().size());
    }
  }

  private static String buildQuery(SynchronizationJob job) {
    log.debug("buildQuery:: parameters job: {}", () -> asJson(job));

    StringBuilder query = new StringBuilder("status.name==Open");
    if (job.getScope() == USER) {
      query.append(" and userId==").append(job.getUserId());
    }

    log.info("buildQuery:: result: {}", query);
    return query.toString();
  }

  protected abstract Future<T> generateEvents(T entity);

  protected abstract Future<SynchronizationJob> updateStats(SynchronizationJob job,
    List<T> entities);

  public Set<String> getUserIds() {
    return userIds;
  }
}
