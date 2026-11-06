package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.utils.EntityBuilder.buildFeeFineBalanceChangedEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.folio.repository.UserSummaryRepository;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.jaxrs.model.UserSummary;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.pgclient.PgException;

public class UserSummaryServiceUnitTest extends TestBase {

  @Mock
  private PostgresClient postgresClient;

  @Mock
  private UserSummaryRepository userSummaryRepository;

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
  void shouldDelegateFindSummariesForUsersToRepository() {
    String userId1 = randomId();
    String userId2 = randomId();
    List<String> userIds = List.of(userId1, userId2);
    UserSummary summary1 = buildUserSummary(randomId(), userId1);
    Map<String, UserSummary> expectedSummaries = Map.of(userId1, summary1);
    setInternalState(userSummaryService, "userSummaryRepository", userSummaryRepository);
    when(userSummaryRepository.findSummariesForUsers(userIds))
      .thenReturn(succeededFuture(expectedSummaries));

    Map<String, UserSummary> actualSummaries = waitFor(userSummaryService.findSummariesForUsers(userIds));

    assertEquals(expectedSummaries, actualSummaries);
    verify(userSummaryRepository).findSummariesForUsers(userIds);
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

}
