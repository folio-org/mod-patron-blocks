package org.folio.repository;

import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;
import static org.folio.util.LogUtil.asJson;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.domain.SynchronizationStatus;
import org.folio.rest.jaxrs.model.SynchronizationJob;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class SynchronizationJobRepository extends BaseRepository<SynchronizationJob> {
  private static final Logger log = LogManager.getLogger(SynchronizationJobRepository.class);

  private static final String SYNCHRONIZATION_JOBS_TABLE = "synchronization_jobs";
  private static final int SYNC_JOBS_LIMIT = 1;

  public SynchronizationJobRepository(PostgresClient pgClient) {
    super(pgClient, SYNCHRONIZATION_JOBS_TABLE, SynchronizationJob.class);
  }

  public Future<String> save(SynchronizationJob entity) {
    return save(entity, entity.getId());
  }

  public Future<List<SynchronizationJob>> getJobsByStatus(SynchronizationStatus syncStatus) {
    log.debug("getJobsByStatus:: parameters syncStatus: {}", syncStatus);

    Criterion criterion = new Criterion(new Criteria()
      .addField("'status'")
      .setOperation("=")
      .setVal(syncStatus.getValue())
      .setJSONB(true));

    return get(criterion);
  }

  public Future<SynchronizationJob> getTheOldestSyncRequest(String tenantId) {
    log.debug("getTheOldestSyncRequest:: parameters tenantId: {}", tenantId);
    String tableName = String.format("%s.%s", convertToPsqlStandard(tenantId),
      SYNCHRONIZATION_JOBS_TABLE);

    String sql = String.format("SELECT jsonb FROM %s WHERE jsonb->>'status' = 'open' " +
      "ORDER BY(jsonb #>> '{metadata,createdDate}') ASC LIMIT '%d'", tableName,
      SYNC_JOBS_LIMIT);

    return select(sql)
      .map(requests -> {
        if (requests.size() == 0) {
          throw new RuntimeException("There are no open requests");
        }
        return requests.iterator().next();
      })
      .map(row -> row.getValue(0))
      .map(JsonObject.class::cast)
      .map(jsonObject -> jsonObject.mapTo(SynchronizationJob.class))
      .onSuccess(r -> log.info("getTheOldestSyncRequest:: result: {}", () -> asJson(r)));
  }

  public Future<RowSet<Row>> select(String sql) {
    log.debug("select:: parameters sql: {}", sql);
    Promise<RowSet<Row>> promise = Promise.promise();
    pgClient.select(sql, promise);
    return promise.future()
      .onSuccess(r -> log.info("select:: result: {}", () -> asJson(r)));
  }

  public Future<SynchronizationJob> update(SynchronizationJob job) {
    log.debug("update:: parameters job: {}", () -> asJson(job));
    return update(job, job.getId())
      .onSuccess(r -> log.info("update:: Synchronization job updated: {}", () -> asJson(job)))
      .onFailure(t -> log.warn("update:: Synchronization job update failed", t))
      .map(job);
  }
}
