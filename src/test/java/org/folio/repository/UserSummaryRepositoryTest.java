package org.folio.repository;

import static java.math.BigDecimal.TEN;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.folio.repository.UserSummaryLockRepository.USER_SUMMARY_LOCK_TABLE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.OpenFeeFine;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgException;

public class UserSummaryRepositoryTest extends TestBase {
  private final UserSummaryRepository repository = new UserSummaryRepository(postgresClient);
  private final UserSummaryLockRepository lockRepository = new UserSummaryLockRepository(postgresClient);

  @BeforeEach
  void setUp() {
    resetMocks();
    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
    deleteAllFromTable(USER_SUMMARY_LOCK_TABLE_NAME);
  }

  @Test
  void shouldAddUserSummary(VertxTestContext context) {
    String summaryId = randomId();
    UserSummary userSummaryToSave = createUserSummary(summaryId, randomId());
    waitFor(repository.save(userSummaryToSave));

    Optional<UserSummary> retrievedUserSummary = waitFor(repository.get(summaryId));

    assertTrue(retrievedUserSummary.isPresent());
    assertSummariesAreEqual(userSummaryToSave, retrievedUserSummary.get(), context);

    context.completeNow();
  }

  @Test
  void shouldFailWhenAttemptingToSaveSummaryWithDuplicateId(VertxTestContext context) {
    String sameSummaryId = randomId();
    UserSummary userSummaryToSave1 = createUserSummary(sameSummaryId, randomId());
    UserSummary userSummaryToSave2 = createUserSummary(sameSummaryId, randomId());

    waitFor(repository.save(userSummaryToSave1));
    Future<String> saveDuplicateSummary = repository.save(userSummaryToSave2);
    waitFor(saveDuplicateSummary);

    assertTrue(saveDuplicateSummary.failed());
    assertInstanceOf(PgException.class, saveDuplicateSummary.cause());
    assertTrue(saveDuplicateSummary.cause().getMessage().contains(
      "duplicate key value violates unique constraint \"user_summary_pkey\""));

    context.completeNow();
  }

  @Test
  void shouldFailWhenAttemptingToSaveSummaryWithDuplicateUserId(VertxTestContext context) {
    String sameUserId = randomId();
    UserSummary userSummaryToSave1 = createUserSummary(randomId(), sameUserId);
    UserSummary userSummaryToSave2 = createUserSummary(randomId(), sameUserId);

    waitFor(repository.save(userSummaryToSave1));
    Future<String> saveDuplicateSummary = repository.save(userSummaryToSave2);
    waitFor(saveDuplicateSummary);

    assertTrue(saveDuplicateSummary.failed());
    assertTrue(saveDuplicateSummary.cause() instanceof PgException);
    assertTrue(saveDuplicateSummary.cause().getMessage().contains(
      "duplicate key value violates unique constraint \"user_summary_userid_idx_unique\""));

    context.completeNow();
  }

  @Test
  void shouldGetUserSummaryById(VertxTestContext context) {
    UserSummary expectedUserSummary = createUserSummary(randomId(), randomId());

    waitFor(Future.all(List.of(
      repository.save(createUserSummary(randomId(), randomId())),
      repository.save(expectedUserSummary),
      repository.save(createUserSummary(randomId(), randomId()))))
    );

    Optional<UserSummary> retrievedUserSummary =
      waitFor(repository.get(expectedUserSummary.getId()));

    assertTrue(retrievedUserSummary.isPresent());
    assertSummariesAreEqual(expectedUserSummary, retrievedUserSummary.get(), context);

    context.completeNow();
  }

  @Test
  void shouldGetUserSummaryByUserId(VertxTestContext context) {
    UserSummary expectedUserSummary = createUserSummary(randomId(), randomId());

    waitFor(Future.all(List.of(
      repository.save(createUserSummary(randomId(), randomId())),
      repository.save(expectedUserSummary),
      repository.save(createUserSummary(randomId(), randomId()))))
    );

    Optional<UserSummary> retrievedUserSummary =
      waitFor(repository.getByUserId(expectedUserSummary.getUserId()));

    assertTrue(retrievedUserSummary.isPresent());
    assertSummariesAreEqual(expectedUserSummary, retrievedUserSummary.get(), context);

    context.completeNow();
  }

  @Test
  void shouldUpdateUserSummary(VertxTestContext context) {
    String userSummaryId = randomId();

    waitFor(repository.save(
      createUserSummary(userSummaryId, randomId())));

    waitFor(repository.get(userSummaryId)).ifPresent(userSummary1 -> {
      userSummary1.withOpenFeesFines(singletonList(
        new OpenFeeFine()
          .withBalance(TEN)
          .withFeeFineId(randomId())
          .withFeeFineTypeId(randomId())
          .withLoanId(randomId())));

      waitFor(repository.update(userSummary1));
      Optional<UserSummary> userSummary = waitFor(repository.get(userSummaryId));

      assertTrue(userSummary.isPresent());
      assertSummariesAreEqual(userSummary1, userSummary.get(), context);
    });

    context.completeNow();
  }

  @Test
  void shouldDeleteUserSummary(VertxTestContext context) {
    String userSummaryId1 = randomId();
    String userSummaryId2 = randomId();
    String userSummaryId3 = randomId();

    UserSummary userSummary = createUserSummary(userSummaryId2, randomId());

    waitFor(Future.all(List.of(
      repository.save(
        createUserSummary(userSummaryId1, randomId())),
      repository.save(userSummary),
      repository.save(
        createUserSummary(userSummaryId3, randomId()))))
    );

    List<UserSummary> retrievedSummaries =
      waitFor(repository.get(null, 0, 100));

    assertEquals(3, retrievedSummaries.size());

    waitFor(Future.all(List.of(
      repository.delete(userSummaryId1),
      repository.delete(userSummaryId3)))
    );

    List<UserSummary> remainingUserSummaries =
      waitFor(repository.get(null, 0, 100));

    assertEquals(1, remainingUserSummaries.size());
    assertSummariesAreEqual(userSummary, remainingUserSummaries.get(0), context);

    context.completeNow();
  }

  @Test
  void shouldUpsertUserSummary(VertxTestContext context) {
    String summaryId = randomId();
    UserSummary initialUserSummary = createUserSummary(summaryId, randomId());

    waitFor(repository.upsert(initialUserSummary));
    Optional<UserSummary> retrievedInitialSummaryOptional =
      waitFor(repository.get(summaryId));

    assertTrue(retrievedInitialSummaryOptional.isPresent());
    UserSummary retrievedInitialSummary = retrievedInitialSummaryOptional.get();
    assertSummariesAreEqual(initialUserSummary, retrievedInitialSummary, context);

    UserSummary updatedSummary = retrievedInitialSummary.withOpenLoans(singletonList(
      new OpenLoan()
        .withLoanId(randomId())
        .withDueDate(new Date())
        .withItemLost(false)
        .withRecall(false)));

    waitFor(repository.upsert(updatedSummary));

    Optional<UserSummary> retrievedUpdatedSummary =
      waitFor(repository.get(summaryId));

    assertTrue(retrievedUpdatedSummary.isPresent());
    assertSummariesAreEqual(updatedSummary, retrievedUpdatedSummary.get(), context);

    context.completeNow();
  }

  @Test
  void setUpShouldClearUserSummaryLockTable(VertxTestContext context) {
    String userId = randomId();

    waitFor(lockRepository.ensureLockRowExists(userId));
    assertTrue(waitFor(lockRepository.getByUserId(userId)).isPresent());

    setUp();

    assertTrue(waitFor(lockRepository.getByUserId(userId)).isEmpty());

    context.completeNow();
  }

  private UserSummary createUserSummary(String id, String userId) {

    OpenLoan openLoan = new OpenLoan()
      .withLoanId(randomId())
      .withRecall(false)
      .withItemLost(false)
      .withDueDate(new Date());

    OpenFeeFine openFeeFine = new OpenFeeFine()
      .withFeeFineId(randomId())
      .withFeeFineTypeId(randomId())
      .withBalance(TEN);

    return new UserSummary()
      .withId(id)
      .withUserId(userId)
      .withOpenLoans(asList(openLoan, openLoan))
      .withOpenFeesFines(asList(openFeeFine, openFeeFine));
  }

  private void assertSummariesAreEqual(UserSummary expected, UserSummary actual, 
    VertxTestContext ctx) {
    
    assertEquals(expected.getId(), actual.getId());
    assertEquals(expected.getUserId(), actual.getUserId());
    assertEquals(expected.getOpenFeesFines().size(), actual.getOpenFeesFines().size());
    assertEquals(expected.getOpenLoans().size(), actual.getOpenLoans().size());
    
    ctx.completeNow();
  }

}
