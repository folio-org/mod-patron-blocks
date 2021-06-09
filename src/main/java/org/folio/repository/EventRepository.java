package org.folio.repository;

import java.util.List;

import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.ModuleName;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public class EventRepository<T> extends BaseRepository<T> {
  private static final int NUMBER_OF_EVENTS_LIMIT = 10000;

  public EventRepository(PostgresClient pgClient, String tableName, Class<T> entityType) {
    super(pgClient, tableName, entityType);
  }

  public Future<List<T>> getByUserId(String userId) {
    return this.get(new Criterion(new Criteria()
      .addField("'userId'")
      .setOperation("=")
      .setVal(userId)
      .setJSONB(true)
    ).setLimit(new Limit(NUMBER_OF_EVENTS_LIMIT)));
  }

  public Future<Void> removeByUserId(String tenantId, String userId) {
    Promise<Void> promise = Promise.promise();

    String deleteByUserIdQuery = String.format(
      "DELETE FROM %s_%s.%s WHERE jsonb ->> 'userId' = '%s'", tenantId,
      ModuleName.getModuleName(), tableName, userId);
    pgClient.execute(deleteByUserIdQuery, reply -> {
      if (reply.failed()) {
        promise.future().failed();
      } else {
        promise.complete();
      }
    });

    return promise.future();
  }
}
