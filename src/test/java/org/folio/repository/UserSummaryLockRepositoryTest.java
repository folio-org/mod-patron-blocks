package org.folio.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.UserSummary;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgException;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

public class UserSummaryLockRepositoryTest extends TestBase {
  private final UserSummaryLockRepository repository = new UserSummaryLockRepository(postgresClient);

  @BeforeEach
  void setUp() {
    resetMocks();
    deleteAllFromTable(USER_SUMMARY_LOCK_TABLE_NAME);
  }

  @Test
  void shouldCreateLockRowWhenMissing(VertxTestContext context) {
    String userId = randomId();

    assertFalse(waitFor(repository.getByUserId(userId)).isPresent());

    waitFor(repository.ensureLockRowExists(userId));

    Optional<UserSummary> lockSummary = waitFor(repository.getByUserId(userId));

    assertTrue(lockSummary.isPresent());
    assertEquals(userId, lockSummary.get().getUserId());

    context.completeNow();
  }

  @Test
  void shouldReturnExistingLockRowWhenAlreadyExists(VertxTestContext context) {
    String lockId = randomId();
    String userId = randomId();

    waitFor(repository.save(new UserSummary()
      .withId(lockId)
      .withUserId(userId)));

    String returnedId = waitFor(repository.ensureLockRowExists(userId));
    List<UserSummary> lockRows = waitFor(repository.get(null, 0, 100));

    assertEquals(lockId, returnedId);
    assertEquals(1, lockRows.size());
    assertEquals(lockId, lockRows.get(0).getId());
    assertEquals(userId, lockRows.get(0).getUserId());

    context.completeNow();
  }

  @Test
  void shouldCreateSingleLockRowWhenConcurrentCallersEnsureLockRowExists(VertxTestContext context) {
    String userId = randomId();

    var createLocks = io.vertx.core.Future.all(List.of(
      repository.ensureLockRowExists(userId),
      repository.ensureLockRowExists(userId),
      repository.ensureLockRowExists(userId),
      repository.ensureLockRowExists(userId),
      repository.ensureLockRowExists(userId)));

    waitFor(createLocks);

    assertTrue(createLocks.succeeded());

    List<UserSummary> lockRows = waitFor(repository.get(null, 0, 100));

    assertEquals(1, lockRows.size());
    assertEquals(userId, lockRows.get(0).getUserId());

    context.completeNow();
  }

  @Test
  void shouldTreatPgUniqueViolationAsDuplicateCreationRace(VertxTestContext context) {
    String lockId = randomId();
    String userId = randomId();
    TestUserSummaryLockRepository testRepository = new TestUserSummaryLockRepository(lockId, userId);

    String returnedId = waitFor(testRepository.ensureLockRowExists(userId));

    assertEquals(lockId, returnedId);
    assertTrue(PgExceptionUtil.isUniqueViolation(testRepository.saveFailure));

    context.completeNow();
  }

  @Test
  void shouldRunActionWithinUserLockTransactionAndCreateLockRow(VertxTestContext context) {
    String userId = randomId();
    AtomicBoolean callbackRan = new AtomicBoolean();

    Method withUserLockMethod;
    try {
      withUserLockMethod = UserSummaryLockRepository.class.getMethod("withUserLock", String.class,
        java.util.function.Function.class);
    } catch (NoSuchMethodException e) {
      fail("withUserLock method is missing", e);
      return;
    }

    @SuppressWarnings("unchecked")
    io.vertx.core.Future<String> withLock = (io.vertx.core.Future<String>) invokeWithUserLock(withUserLockMethod, userId,
      conn -> conn.execute(
          "SELECT count(*) AS count FROM " + postgresClient.getSchemaName() + "." + USER_SUMMARY_LOCK_TABLE_NAME
            + " WHERE jsonb->>'userId' = $1",
          Tuple.of(userId))
        .map(rows -> {
          callbackRan.set(true);
          return String.valueOf(rows.iterator().next().getLong("count"));
        }));

    String callbackResult = waitFor(withLock);

    Optional<UserSummary> lockSummary = waitFor(repository.getByUserId(userId));

    assertTrue(withLock.succeeded(), String.valueOf(withLock.cause()));
    assertTrue(callbackRan.get());
    assertEquals("1", callbackResult);
    assertTrue(lockSummary.isPresent());
    assertEquals(userId, lockSummary.get().getUserId());

    context.completeNow();
  }

  @Test
  void shouldCreateSingleLockRowWhenConcurrentCallersUseWithUserLock(VertxTestContext context) {
    String userId = randomId();

    var lockCalls = io.vertx.core.Future.all(List.of(
      repository.withUserLock(userId, ignoredConn -> Future.succeededFuture("one")),
      repository.withUserLock(userId, ignoredConn -> Future.succeededFuture("two")),
      repository.withUserLock(userId, ignoredConn -> Future.succeededFuture("three")),
      repository.withUserLock(userId, ignoredConn -> Future.succeededFuture("four")),
      repository.withUserLock(userId, ignoredConn -> Future.succeededFuture("five"))));

    waitFor(lockCalls, 10);

    assertTrue(lockCalls.succeeded(), String.valueOf(lockCalls.cause()));

    List<UserSummary> lockRows = waitFor(repository.get(null, 0, 100));

    assertEquals(1, lockRows.size());
    assertEquals(userId, lockRows.get(0).getUserId());

    context.completeNow();
  }

  @Test
  void shouldKeepWithUserLockFullyTransactionalAndLockByUserId() {
    String userId = randomId();
    PostgresClient transactionalPgClient = mock(PostgresClient.class);
    Conn conn = mock(Conn.class);
    @SuppressWarnings("unchecked")
    RowSet<Row> rowSet = mock(RowSet.class);
    AtomicReference<String> bootstrapSql = new AtomicReference<>();
    AtomicReference<Tuple> bootstrapTuple = new AtomicReference<>();
    AtomicReference<String> lockSql = new AtomicReference<>();
    AtomicReference<Tuple> lockTuple = new AtomicReference<>();

    UserSummaryLockRepository transactionalRepository = new UserSummaryLockRepository(transactionalPgClient) {
      @Override
      public Future<Optional<UserSummary>> getByUserId(String ignoredUserId) {
        return Future.failedFuture(new AssertionError("withUserLock should stay on Conn and not call getByUserId"));
      }
    };

    when(transactionalPgClient.getSchemaName()).thenReturn("test_schema");
    doAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      java.util.function.Function<Conn, Future<String>> transaction = invocation.getArgument(0);
      return transaction.apply(conn);
    }).when(transactionalPgClient).withTrans(any());
    when(conn.execute(anyString(), any(Tuple.class))).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      Tuple tuple = invocation.getArgument(1);

      if (bootstrapSql.get() == null) {
        bootstrapSql.set(sql);
        bootstrapTuple.set(tuple);
      } else {
        lockSql.set(sql);
        lockTuple.set(tuple);
      }

      return Future.succeededFuture(rowSet);
    });

    String result = waitFor(transactionalRepository.withUserLock(userId, ignoredConn -> Future.succeededFuture("locked")));

    assertEquals("locked", result);
    assertTrue(bootstrapSql.get().contains("INSERT INTO test_schema.user_summary_lock"));
    assertTrue(bootstrapSql.get().contains("VALUES ($1, $2) ON CONFLICT DO NOTHING"));
    assertNotNull(bootstrapTuple.get().getString(0));
    assertEquals(userId, bootstrapTuple.get().getJsonObject(1).getString("userId"));
    assertTrue(lockSql.get().contains("WHERE jsonb->>'userId' = $1 FOR UPDATE"));
    assertEquals(userId, lockTuple.get().getString(0));
    verify(conn, times(2)).execute(anyString(), any(Tuple.class));
    verify(conn, never()).save(eq(USER_SUMMARY_LOCK_TABLE_NAME), anyString(), any(UserSummary.class));
    verify(conn, never()).execute(eq("SELECT id FROM test_schema.user_summary_lock WHERE id = $1 FOR UPDATE"), any(Tuple.class));
  }

  private Object invokeWithUserLock(Method withUserLockMethod, String userId,
      java.util.function.Function<Conn, io.vertx.core.Future<String>> action) {
    try {
      return withUserLockMethod.invoke(repository, userId, action);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Failed to invoke withUserLock", e);
    }
  }

  private static class TestUserSummaryLockRepository extends UserSummaryLockRepository {
    private final String lockId;
    private final String expectedUserId;
    private final Map<String, UserSummary> lockRows = new HashMap<>();
    private int getByUserIdCalls;
    private Throwable saveFailure;

    TestUserSummaryLockRepository(String lockId, String expectedUserId) {
      super(postgresClient);
      this.lockId = lockId;
      this.expectedUserId = expectedUserId;
      this.lockRows.put(expectedUserId, new UserSummary().withId(lockId).withUserId(expectedUserId));
    }

    @Override
    public Future<Optional<UserSummary>> getByUserId(String userId) {
      assertEquals(expectedUserId, userId);
      getByUserIdCalls++;

      if (getByUserIdCalls == 1) {
        return Future.succeededFuture(Optional.empty());
      }

      return Future.succeededFuture(Optional.ofNullable(lockRows.get(userId)));
    }

    @Override
    public Future<String> save(UserSummary entity) {
      saveFailure = new PgException("write failed", "detail", "23505", "error");
      return Future.failedFuture(saveFailure);
    }
  }
}
