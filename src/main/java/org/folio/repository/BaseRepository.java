package org.folio.repository;

import java.util.List;
import java.util.Optional;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.tools.utils.ModuleName;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class BaseRepository<T> {
  private static final int DEFAULT_LIMIT = 100;

  protected final PostgresClient pgClient;
  protected final String tableName;
  private final Class<T> entityType;

  public BaseRepository(PostgresClient pgClient, String tableName, Class<T> entityType) {
    this.pgClient = pgClient;
    this.tableName = tableName;
    this.entityType = entityType;
  }

  public Future<List<T>> get(String query, int offset, int limit) {
    Promise<Results<T>> promise = Promise.promise();
    try {
      String[] fieldList = {"*"};
      CQLWrapper cql = getCQL(query, limit, offset);
      pgClient.get(tableName, entityType, fieldList, cql, true, false, promise);
    } catch (Exception e) {
      promise.fail(e);
    }
    return promise.future().map(Results::getResults);
  }

  public Future<List<T>> get(Criterion criterion) {
    Promise<Results<T>> promise = Promise.promise();
    pgClient.get(tableName, entityType, criterion, true, promise);
    return promise.future()
      .map(Results::getResults);
  }

  public Future<Optional<T>> get(String id) {
    Promise<T> promise = Promise.promise();
    pgClient.getById(tableName, id, entityType, promise);
    return promise.future().map(Optional::ofNullable);
  }

  public Future<List<T>> getAllWithDefaultLimit() {
    return getAllWithLimit(DEFAULT_LIMIT);
  }

  public Future<List<T>> getAllWithLimit(int limit) {
    return get(null, 0, limit);
  }

  public Future<String> save(T entity, String id) {
    Promise<String> promise = Promise.promise();
    pgClient.save(tableName, id, entity, promise);
    return promise.future();
  }

  public Future<String> upsert(T entity, String id) {
    Promise<String> promise = Promise.promise();
    pgClient.upsert(tableName, id, entity, promise);
    return promise.future();
  }

  public Future<Boolean> update(T entity, String id) {
    Promise<RowSet<Row>> promise = Promise.promise();
    pgClient.update(tableName, entity, id, promise);
    return promise.future().map(updateResult -> updateResult.rowCount() == 1);
  }

  public Future<Boolean> delete(String id) {
    Promise<RowSet<Row>> promise = Promise.promise();
    pgClient.delete(tableName, id, promise);
    return promise.future().map(updateResult -> updateResult.rowCount() == 1);
  }

  public Future<Void> removeAll(String tenantId) {
    Promise<Void> promise = Promise.promise();
    String deleteAllQuery = String.format("DELETE FROM %s_%s.%s", tenantId,
      ModuleName.getModuleName(), tableName);
    pgClient.execute(deleteAllQuery, reply -> {
      if (reply.failed()) {
        promise.future().failed();
      } else {
        promise.complete();
      }
    });
    return promise.future();
  }

  /**
   * Build CQL from request URL query
   *
   * @param query - query from URL
   * @param limit - limit of records for pagination
   * @return - CQL wrapper for building postgres request to database
   * @throws org.folio.cql2pgjson.exception.FieldException field exception
   */
  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(tableName + ".jsonb");
    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }

}
