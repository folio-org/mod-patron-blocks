package org.folio.repository;

import static org.folio.util.LogUtil.asJson;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.ModuleName;
import org.folio.util.UuidHelper;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public class EventRepository<T> extends BaseRepository<T> {

  private static final Logger log = LogManager.getLogger(EventRepository.class);
  private static final int NUMBER_OF_EVENTS_LIMIT = 10000;

  public EventRepository(PostgresClient pgClient, String tableName, Class<T> entityType) {
    super(pgClient, tableName, entityType);
  }

  public Future<String> save(T entity) {
    return super.save(entity, UuidHelper.randomId());
  }

  public Future<List<T>> getByUserId(String userId) {
    log.debug("getByUserId:: parameters userId: {}", userId);
    return this.get(new Criterion(new Criteria()
      .addField("'userId'")
      .setOperation("=")
      .setVal(userId)
      .setJSONB(true)
    ).setLimit(new Limit(NUMBER_OF_EVENTS_LIMIT)))
      .onSuccess(result -> log.info("getByUserId:: result: {}", () -> asJson(result)));
  }

  public Future<Void> removeByUserId(String tenantId, String userId) {
    log.debug("removeByUserId:: parameters tenantId: {}, userId: {}", tenantId, userId);
    Promise<Void> promise = Promise.promise();

    String deleteByUserIdQuery = String.format(
      "DELETE FROM %s_%s.%s WHERE jsonb ->> 'userId' = '%s'", tenantId,
      ModuleName.getModuleName(), tableName, userId);
    pgClient.execute(deleteByUserIdQuery, reply -> {
      if (reply.failed()) {
        log.warn("removeByUserId:: Failed to delete entries from table {}.{} by user ID {}",
          tenantId, tableName, userId);
        promise.future().failed();
      } else {
        log.info("removeByUserId:: Deleted entries from table {}.{} by user ID {}", tenantId,
          tableName, userId);
        promise.complete();
      }
    });

    return promise.future();
  }
}
