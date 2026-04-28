package org.folio.repository;

import static org.folio.util.UuidHelper.randomId;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.folio.rest.jaxrs.model.UserSummary;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

public class UserSummaryLockRepository extends BaseRepository<UserSummary> {
  public static final String USER_SUMMARY_LOCK_TABLE_NAME = "user_summary_lock";

  public UserSummaryLockRepository(PostgresClient pgClient) {
    super(pgClient, USER_SUMMARY_LOCK_TABLE_NAME, UserSummary.class);
  }

  public Future<String> ensureLockRowExists(String userId) {
    return getByUserId(userId)
      .compose(existingLock -> existingLock
        .map(lock -> Future.succeededFuture(lock.getId()))
        .orElseGet(() -> insertLockRowIfMissing(userId, this::save, this::getByUserId)));
  }

  public <T> Future<T> withUserLock(String userId, Function<Conn, Future<T>> action) {
    Objects.requireNonNull(action, "action must not be null");

    return pgClient.withTrans(conn -> ensureLockRowExistsWithoutConflict(conn, userId)
      .compose(ignored -> conn.execute("SELECT id FROM " + getQualifiedTableName()
          + " WHERE jsonb->>'userId' = $1 FOR UPDATE", Tuple.of(userId))
        .compose(rowSet -> action.apply(conn))));
  }

  public Future<Optional<UserSummary>> getByUserId(String userId) {
    return get(UserSummaryRepository.buildCriterionWithUserId(userId))
      .map(results -> results.stream().findFirst());
  }

  public Future<String> save(UserSummary entity) {
    return super.save(entity, entity.getId());
  }

  private Future<Void> ensureLockRowExistsWithoutConflict(Conn conn, String userId) {
    String id = randomId();

    return conn.execute("INSERT INTO " + getQualifiedTableName() + " (id, jsonb) VALUES ($1, $2) ON CONFLICT DO NOTHING",
        Tuple.of(id, buildLockRowJson(id, userId)))
      .mapEmpty();
  }

  private Future<String> insertLockRowIfMissing(String userId,
      Function<UserSummary, Future<String>> saveLockRow,
      Function<String, Future<Optional<UserSummary>>> findLockRowByUserId) {
    return saveLockRow.apply(new UserSummary()
        .withId(randomId())
        .withUserId(userId))
      .recover(throwable -> {
        if (!PgExceptionUtil.isUniqueViolation(throwable)) {
          return Future.failedFuture(throwable);
        }

        return findLockRowByUserId.apply(userId)
          .compose(lock -> lock
            .<Future<String>>map(existing -> Future.succeededFuture(existing.getId()))
            .orElseGet(() -> Future.failedFuture(throwable)));
      });
  }

  private String getQualifiedTableName() {
    return pgClient.getSchemaName() + "." + tableName;
  }

  private static JsonObject buildLockRowJson(String id, String userId) {
    return JsonObject.mapFrom(new UserSummary()
      .withId(id)
      .withUserId(userId));
  }
}
