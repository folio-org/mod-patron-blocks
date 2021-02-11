package org.folio.repository;

import static java.math.BigDecimal.TEN;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.folio.repository.UserSummaryRepository.USER_SUMMARY_TABLE_NAME;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.OpenFeeFine;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.UserSummary;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.pgclient.PgException;

@RunWith(VertxUnitRunner.class)
public class UserSummaryRepositoryTest extends TestBase {
  private final UserSummaryRepository repository = new UserSummaryRepository(postgresClient);

  @Before
  public void setUp() {
    resetMocks();
    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
  }

  @Test
  public void shouldAddUserSummary(TestContext context) {
    String summaryId = randomId();
    UserSummary userSummaryToSave =  createUserSummary(summaryId, randomId());
    waitFor(repository.save(userSummaryToSave));

    Optional<UserSummary> retrievedUserSummary = waitFor(repository.get(summaryId));

    context.assertTrue(retrievedUserSummary.isPresent());
    assertSummariesAreEqual(userSummaryToSave, retrievedUserSummary.get(), context);
  }

  @Test
  public void shouldFailWhenAttemptingToSaveSummaryWithDuplicateId(TestContext context) {
    String sameSummaryId = randomId();
    UserSummary userSummaryToSave1 =  createUserSummary(sameSummaryId, randomId());
    UserSummary userSummaryToSave2 =  createUserSummary(sameSummaryId, randomId());

    waitFor(repository.save(userSummaryToSave1));
    Future<String> saveDuplicateSummary = repository.save(userSummaryToSave2);
    waitFor(saveDuplicateSummary);

    context.assertTrue(saveDuplicateSummary.failed());
    context.assertTrue(saveDuplicateSummary.cause() instanceof PgException);
    context.assertTrue(saveDuplicateSummary.cause().getMessage().contains(
      "duplicate key value violates unique constraint \\\"user_summary_pkey\\\""));
  }

  @Test
  public void shouldFailWhenAttemptingToSaveSummaryWithDuplicateUserId(TestContext context) {
    String sameUserId = randomId();
    UserSummary userSummaryToSave1 =  createUserSummary(randomId(), sameUserId);
    UserSummary userSummaryToSave2 =  createUserSummary(randomId(), sameUserId);

    waitFor(repository.save(userSummaryToSave1));
    Future<String> saveDuplicateSummary = repository.save(userSummaryToSave2);
    waitFor(saveDuplicateSummary);

    context.assertTrue(saveDuplicateSummary.failed());
    context.assertTrue(saveDuplicateSummary.cause() instanceof PgException);
    context.assertTrue(saveDuplicateSummary.cause().getMessage().contains(
      "duplicate key value violates unique constraint \\\"user_summary_userid_idx_unique\\\""));
  }

  @Test
  public void shouldGetUserSummaryById(TestContext context) {
    UserSummary expectedUserSummary = createUserSummary(randomId(), randomId());

    waitFor(GenericCompositeFuture.all(List.of(
      repository.save(createUserSummary(randomId(), randomId())),
      repository.save(expectedUserSummary),
      repository.save(createUserSummary(randomId(), randomId()))))
    );

    Optional<UserSummary> retrievedUserSummary =
      waitFor(repository.get(expectedUserSummary.getId()));

    context.assertTrue(retrievedUserSummary.isPresent());
    assertSummariesAreEqual(expectedUserSummary, retrievedUserSummary.get(), context);
  }

  @Test
  public void shouldGetUserSummaryByUserId(TestContext context) {
    UserSummary expectedUserSummary = createUserSummary(randomId(), randomId());

    waitFor(GenericCompositeFuture.all(List.of(
      repository.save(createUserSummary(randomId(), randomId())),
      repository.save(expectedUserSummary),
      repository.save(createUserSummary(randomId(), randomId()))))
    );

    Optional<UserSummary> retrievedUserSummary =
      waitFor(repository.getByUserId(expectedUserSummary.getUserId()));

    context.assertTrue(retrievedUserSummary.isPresent());
    assertSummariesAreEqual(expectedUserSummary, retrievedUserSummary.get(), context);
  }

  @Test
  public void shouldUpdateUserSummary(TestContext context) {
    String userSummaryId = randomId();

    waitFor(repository.save(
      createUserSummary(userSummaryId, randomId())));

    UserSummary updatedUserSummary = createUserSummary(userSummaryId, randomId());
    updatedUserSummary.withOpenFeesFines(singletonList(
      new OpenFeeFine()
        .withBalance(TEN)
        .withFeeFineId(randomId())
        .withFeeFineTypeId(randomId())
        .withLoanId(randomId())));

    waitFor(repository.update(updatedUserSummary));
    Optional<UserSummary> userSummary = waitFor(repository.get(userSummaryId));

    context.assertTrue(userSummary.isPresent());
    assertSummariesAreEqual(updatedUserSummary, userSummary.get(), context);
  }

  @Test
  public void shouldDeleteUserSummary(TestContext context) {
    String userSummaryId1 = randomId();
    String userSummaryId2 = randomId();
    String userSummaryId3 = randomId();

    UserSummary userSummary = createUserSummary(userSummaryId2, randomId());

    waitFor(GenericCompositeFuture.all(List.of(
      repository.save(
        createUserSummary(userSummaryId1, randomId())),
      repository.save(userSummary),
      repository.save(
        createUserSummary(userSummaryId3, randomId()))))
    );

    List<UserSummary> retrievedSummaries =
      waitFor(repository.get(null, 0, 100));

    context.assertEquals(3, retrievedSummaries.size());

    waitFor(GenericCompositeFuture.all(List.of(
      repository.delete(userSummaryId1),
      repository.delete(userSummaryId3)))
    );

    List<UserSummary> remainingUserSummaries =
      waitFor(repository.get(null, 0, 100));

    context.assertEquals(1, remainingUserSummaries.size());
    assertSummariesAreEqual(userSummary, remainingUserSummaries.get(0), context);
  }

  @Test
  public void shouldUpsertUserSummary(TestContext context) {
    String summaryId = randomId();
    UserSummary initialUserSummary = createUserSummary(summaryId, randomId());

    waitFor(repository.upsert(initialUserSummary));
    Optional<UserSummary> retrievedInitialSummary =
      waitFor(repository.get(summaryId));

    context.assertTrue(retrievedInitialSummary.isPresent());
    assertSummariesAreEqual(initialUserSummary, retrievedInitialSummary.get(), context);

    UserSummary updatedSummary = initialUserSummary.withOpenLoans(singletonList(
      new OpenLoan()
      .withLoanId(randomId())
      .withDueDate(new Date())
      .withItemLost(false)
      .withRecall(false)));

    waitFor(repository.upsert(updatedSummary));

    Optional<UserSummary> retrievedUpdatedSummary =
      waitFor(repository.get(summaryId));

    context.assertTrue(retrievedUpdatedSummary.isPresent());
    assertSummariesAreEqual(updatedSummary, retrievedUpdatedSummary.get(), context);
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

  private void assertSummariesAreEqual(UserSummary expected, UserSummary actual, TestContext ctx) {
    ctx.assertEquals(expected.getId(), actual.getId());
    ctx.assertEquals(expected.getUserId(), actual.getUserId());
    ctx.assertEquals(expected.getOpenFeesFines().size(), actual.getOpenFeesFines().size());
    ctx.assertEquals(expected.getOpenLoans().size(), actual.getOpenLoans().size());
  }

}
