package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.utils.EntityBuilder.buildFeeFineBalanceChangedEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Date;

import org.folio.repository.UserSummaryLockRepository;
import org.folio.repository.UserSummaryRepository;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.UserSummary;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.pgclient.PgException;

public class UserSummaryServiceUnitTest extends TestBase {

  @Mock
  private PostgresClient postgresClient;

  @Mock
  private UserSummaryRepository userSummaryRepository;

  @Mock
  private Conn conn;

  private final UserSummaryService userSummaryService = new UserSummaryService(postgresClient);

  @BeforeEach
  void beforeEach() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void shouldStopRetryingAfterRunningOutOfAttempts() {
    PgException pgException = new PgException("", "", "23F09", "");
    String userId = randomId();
    String summaryId = randomId();
    UserSummary userSummary = buildUserSummary(summaryId, userId);
    BigDecimal balance1 = new BigDecimal("3.33");
    setInternalState(userSummaryService, "userSummaryRepository", userSummaryRepository);
    when(userSummaryRepository.save(userSummary)).thenReturn(succeededFuture(summaryId));
    when(userSummaryRepository.findByUserIdOrBuildNew(userId)).thenReturn(
      succeededFuture(userSummary));
    doReturn(failedFuture(pgException)).when(userSummaryRepository).upsert(userSummary);
    userSummaryRepository.save(userSummary);
    FeeFineBalanceChangedEvent feeFineBalanceChangedEvent1 = buildFeeFineBalanceChangedEvent(
      userId, randomId(), randomId(), randomId(), balance1);
    waitFor(userSummaryService.updateUserSummaryWithEvent(userSummary, feeFineBalanceChangedEvent1));

    verify(userSummaryRepository, times(11)).upsert(userSummary);
  }

  @Test
  void shouldUseConnAwareRepositoryMethodsWhenUpdatingUnderUserLock() {
    String userId = randomId();
    String summaryId = randomId();
    UserSummary userSummary = buildUserSummary(summaryId, userId);
    TestUserSummaryRepository connAwareRepository = new TestUserSummaryRepository(conn, userSummary);
    TestUserSummaryLockRepository lockRepository = new TestUserSummaryLockRepository(conn, userId);

    setInternalState(userSummaryService, "userSummaryRepository", connAwareRepository);
    setInternalState(userSummaryService, "userSummaryLockRepository", lockRepository);

    ItemCheckedOutEvent event = buildItemCheckedOutEvent(userId, randomId(), new Date());

    String updatedSummaryId = waitFor(userSummaryService.updateUserSummaryWithEventUsingLock(userId, event));

    assertEquals(summaryId, updatedSummaryId);
    assertEquals(1, connAwareRepository.connAwareFindCalls);
    assertEquals(1, connAwareRepository.connAwareUpsertCalls);
  }

  private UserSummary buildUserSummary(String id, String userId) {
    return new UserSummary()
      .withId(id)
      .withUserId(userId);
  }

  private static void setInternalState(Object target, String field, Object value) {
    Class<?> c = target.getClass();
    try {
      Field f = c.getDeclaredField(field);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException(
        "Unable to set internal state on a private field. [...]", e);
    }
  }

  private class TestUserSummaryRepository extends UserSummaryRepository {
    private final Conn expectedConn;
    private final UserSummary userSummary;
    private int connAwareFindCalls;
    private int connAwareUpsertCalls;

    TestUserSummaryRepository(Conn expectedConn, UserSummary userSummary) {
      super(postgresClient);
      this.expectedConn = expectedConn;
      this.userSummary = userSummary;
    }

    @Override
    public Future<UserSummary> findByUserIdOrBuildNew(String userId) {
      return failedFuture(new AssertionError("lock update should fetch summary via Conn"));
    }

    @Override
    public Future<String> upsert(UserSummary entity) {
      return failedFuture(new AssertionError("lock update should upsert summary via Conn"));
    }

    @Override
    public Future<Boolean> delete(String id) {
      return failedFuture(new AssertionError("lock update should delete summary via Conn"));
    }

    public Future<UserSummary> findByUserIdOrBuildNew(Conn conn, String userId) {
      assertSame(expectedConn, conn);
      assertEquals(userSummary.getUserId(), userId);
      connAwareFindCalls++;
      return succeededFuture(userSummary);
    }

    public Future<String> upsert(Conn conn, UserSummary entity) {
      assertSame(expectedConn, conn);
      connAwareUpsertCalls++;
      return succeededFuture(entity.getId());
    }

    public Future<Boolean> delete(Conn conn, String id) {
      assertSame(expectedConn, conn);
      return succeededFuture(Boolean.TRUE);
    }
  }

  private class TestUserSummaryLockRepository extends UserSummaryLockRepository {
    private final Conn expectedConn;
    private final String expectedUserId;

    TestUserSummaryLockRepository(Conn expectedConn, String expectedUserId) {
      super(postgresClient);
      this.expectedConn = expectedConn;
      this.expectedUserId = expectedUserId;
    }

    @Override
    public <T> Future<T> withUserLock(String userId, java.util.function.Function<Conn, Future<T>> action) {
      assertEquals(expectedUserId, userId);
      return action.apply(expectedConn);
    }
  }

}
