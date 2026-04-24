package org.folio.rest.impl;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.folio.repository.UserSummaryLockRepository.USER_SUMMARY_LOCK_TABLE_NAME;
import static org.folio.rest.utils.EntityBuilder.buildFeeFineBalanceChangedEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemAgedToLostEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedInEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemClaimedReturnedEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemDeclaredLostEvent;
import static org.folio.rest.utils.EntityBuilder.buildLoanDueDateChangedEvent;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.folio.domain.Event;
import org.folio.repository.UserSummaryRepository;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.jaxrs.model.ItemAgedToLostEvent;
import org.folio.rest.jaxrs.model.ItemCheckedInEvent;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.ItemClaimedReturnedEvent;
import org.folio.rest.jaxrs.model.ItemDeclaredLostEvent;
import org.folio.rest.jaxrs.model.LoanDueDateChangedEvent;
import org.folio.rest.jaxrs.model.OpenFeeFine;
import org.folio.rest.jaxrs.model.UserSummary;
import org.folio.rest.utils.EventClient;
import org.folio.rest.utils.OkapiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EventHandlersAPITest extends TestBase {
  public static final String USER_ID = randomId();
  public static final String INVALID_USER_ID = USER_ID + "xyz";

  private UserSummaryRepository userSummaryRepository;

  @BeforeEach
  void beforeEach() {
    super.resetMocks();

    userSummaryRepository = new UserSummaryRepository(postgresClient);

    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
    deleteAllFromTable(USER_SUMMARY_LOCK_TABLE_NAME);
  }

  @Test
  void feeFineBalanceChangedEventProcessedSuccessfully() {
    sendEventAndVerifyThatUserSummaryWasCreated(createFeeFineBalanceChangedEvent());
  }

  @Test
  void concurrentFeeFineEventsWithoutUserIdResolveUserByFeeFineId() {
    String firstLoanId = randomId();
    String firstFeeFineId = randomId();
    String firstFeeFineTypeId = randomId();
    String secondLoanId = randomId();
    String secondFeeFineId = randomId();
    String secondFeeFineTypeId = randomId();

    waitFor(userSummaryRepository.save(new UserSummary()
      .withId(randomId())
      .withUserId(USER_ID)
      .withOpenLoans(List.of())
      .withOpenFeesFines(List.of(
        new OpenFeeFine()
          .withLoanId(firstLoanId)
          .withFeeFineId(firstFeeFineId)
          .withFeeFineTypeId(firstFeeFineTypeId)
          .withBalance(BigDecimal.ONE),
        new OpenFeeFine()
          .withLoanId(secondLoanId)
          .withFeeFineId(secondFeeFineId)
          .withFeeFineTypeId(secondFeeFineTypeId)
          .withBalance(BigDecimal.TEN)))));

    assertEquals(List.of(), waitFor(userSummaryRepository.getByUserId(USER_ID))
      .orElseThrow()
      .getOpenLoans());

    runConcurrently(
      () -> newEventClient()
        .sendEvent(buildFeeFineBalanceChangedEvent(null, null, firstFeeFineId, null, BigDecimal.ZERO)),
      () -> newEventClient()
        .sendEvent(buildFeeFineBalanceChangedEvent(null, null, secondFeeFineId, null, BigDecimal.ZERO))
    );

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> getUserSummary().isEmpty());
  }

  @Test
  void shouldNotCreateUserSummary() {
    assertFalse(getUserSummary().isPresent());
    eventClient.sendEvent(createItemCheckedInEvent());
    eventClient.sendEvent(createItemClaimedReturnedEvent());
    eventClient.sendEvent(createItemDeclaredLostEvent());
    eventClient.sendEvent(createItemAgedToLostEvent());
    eventClient.sendEvent(createLoanDueDateChangedEvent());
    assertFalse(getUserSummary().isPresent());
  }

  @Test
  void feeFineBalanceChangedEventValidationFails() {
    eventClient.sendEventAndVerifyValidationFailure(
      createFeeFineBalanceChangedEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void itemCheckedInEventProcessedSuccessfully() {
    eventClient.sendEvent(createItemCheckedInEvent());
  }

  @Test
  void itemCheckedInEventValidationFails() {
    eventClient.sendEventAndVerifyValidationFailure(
      createItemCheckedInEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void itemCheckedOutEventProcessedSuccessfully() {
    sendEventAndVerifyThatUserSummaryWasCreated(createItemCheckedOutEvent());
  }

  @Test
  void concurrentItemCheckedOutEventsForSameUserProduceSingleMergedSummary() {
    assertFalse(getUserSummary().isPresent());

    ItemCheckedOutEvent firstEvent = createItemCheckedOutEvent();
    ItemCheckedOutEvent secondEvent = createItemCheckedOutEvent();

    runConcurrently(
      () -> newEventClient().sendEvent(firstEvent),
      () -> newEventClient().sendEvent(secondEvent)
    );

    Awaitility.await()
      .atMost(5, SECONDS)
      .untilAsserted(() -> {
        List<UserSummary> summaries = waitFor(userSummaryRepository.getAllWithDefaultLimit());

        assertEquals(1, summaries.size());
        assertEquals(2, summaries.get(0).getOpenLoans().size());
      });
  }

  @Test
  void concurrentFirstWriteItemCheckedOutEventsForSameUserProduceSingleSummaryRow() {
    assertFalse(getUserSummary().isPresent());

    ItemCheckedOutEvent firstEvent = createItemCheckedOutEvent();
    ItemCheckedOutEvent secondEvent = createItemCheckedOutEvent();
    ItemCheckedOutEvent thirdEvent = createItemCheckedOutEvent();

    runConcurrently(
      () -> newEventClient().sendEvent(firstEvent),
      () -> newEventClient().sendEvent(secondEvent),
      () -> newEventClient().sendEvent(thirdEvent)
    );

    Awaitility.await()
      .atMost(5, SECONDS)
      .untilAsserted(() -> {
        List<UserSummary> summaries = waitFor(userSummaryRepository.getAllWithDefaultLimit());

        assertEquals(1, summaries.size());

        UserSummary summary = summaries.get(0);
        assertEquals(USER_ID, summary.getUserId());
        assertEquals(3, summary.getOpenLoans().size());
        assertEquals(Set.of(firstEvent.getLoanId(), secondEvent.getLoanId(), thirdEvent.getLoanId()),
          summary.getOpenLoans().stream()
            .map(openLoan -> openLoan.getLoanId())
            .collect(Collectors.toSet()));
      });
  }

  @Test
  void runConcurrentlyDoesNotDependOnCommonPoolAvailability() throws InterruptedException {
    int parallelism = ForkJoinPool.getCommonPoolParallelism();
    CountDownLatch blockersStarted = new CountDownLatch(parallelism);
    CountDownLatch releaseBlockers = new CountDownLatch(1);

    List<CompletableFuture<Void>> blockers = java.util.stream.IntStream.range(0, parallelism)
      .mapToObj(index -> CompletableFuture.runAsync(() -> {
        blockersStarted.countDown();
        awaitLatch(releaseBlockers);
      }))
      .toList();

    assertTrue(blockersStarted.await(5, SECONDS));

    ExecutorService callerExecutor = Executors.newSingleThreadExecutor();
    CompletableFuture<Void> runConcurrentlyFuture = CompletableFuture.runAsync(
      () -> runConcurrently(() -> { }, () -> { }), callerExecutor);

    try {
      assertDoesNotThrow(() -> runConcurrentlyFuture.orTimeout(2, SECONDS).join());
    } finally {
      releaseBlockers.countDown();
      callerExecutor.shutdownNow();
      blockers.forEach(CompletableFuture::join);
    }
  }

  @Test
  void itemCheckedOutEventValidationFails() {
    eventClient.sendEventAndVerifyValidationFailure(
      createItemCheckedOutEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void loanDueDateChangedEventProcessedSuccessfully() {
    eventClient.sendEvent(createLoanDueDateChangedEvent());
  }

  @Test
  void loanDueDateChangedEventValidationFails() {
    eventClient.sendEventAndVerifyValidationFailure(
      createLoanDueDateChangedEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void itemDeclaredLostEventProcessedSuccessfully() {
    eventClient.sendEvent(createItemDeclaredLostEvent());
  }

  @Test
  void itemDeclaredLostEventValidationFails() {
    eventClient.sendEventAndVerifyValidationFailure(
      createItemDeclaredLostEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void itemAgedToLostEventProcessedSuccessfully() {
    eventClient.sendEvent(createItemAgedToLostEvent());
  }

  @Test
  void itemAgedToLostEventValidationFails() {
    eventClient.sendEventAndVerifyValidationFailure(
      createItemAgedToLostEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void itemClaimedReturnedEventProcessedSuccessfully() {
    eventClient.sendEvent(createItemClaimedReturnedEvent());
  }

  @Test
  void itemClaimedReturnedEventValidationFails() {
    eventClient.sendEventAndVerifyValidationFailure(
      createItemClaimedReturnedEvent().withUserId(INVALID_USER_ID));
  }

  @Test
  void eventHandlingFailsWhenEventJsonIsInvalid() {
    eventClient.sendEvent("not json", FeeFineBalanceChangedEvent.class, SC_BAD_REQUEST);
  }

  @Test
  void loanDueDateChangedEventWithMissingRequiredDueDateProperty() {
    eventClient.sendEventAndVerifyValidationFailure(
      createLoanDueDateChangedEvent().withDueDate(null));
  }

  private static FeeFineBalanceChangedEvent createFeeFineBalanceChangedEvent() {
    return buildFeeFineBalanceChangedEvent(
      USER_ID, randomId(), randomId(), randomId(), BigDecimal.TEN);
  }

  private static ItemCheckedInEvent createItemCheckedInEvent() {
    return buildItemCheckedInEvent(USER_ID, randomId(), new Date());
  }

  private static ItemCheckedOutEvent createItemCheckedOutEvent() {
    return buildItemCheckedOutEvent(USER_ID, randomId(), new Date());
  }

  private static LoanDueDateChangedEvent createLoanDueDateChangedEvent() {
    return buildLoanDueDateChangedEvent(USER_ID, randomId(), new Date(), false);
  }

  private static ItemDeclaredLostEvent createItemDeclaredLostEvent() {
    return buildItemDeclaredLostEvent(USER_ID, randomId());
  }

  private static ItemAgedToLostEvent createItemAgedToLostEvent() {
    return buildItemAgedToLostEvent(USER_ID, randomId());
  }

  private static ItemClaimedReturnedEvent createItemClaimedReturnedEvent() {
    return buildItemClaimedReturnedEvent(USER_ID, randomId());
  }

  private void sendEventAndVerifyThatUserSummaryWasCreated(Event event) {
    assertFalse(getUserSummary().isPresent());

    eventClient.sendEvent(event);

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> getUserSummary().isPresent());
  }

  private Optional<UserSummary> getUserSummary() {
    return waitFor(userSummaryRepository.getByUserId(USER_ID));
  }

  private void runConcurrently(Runnable... actions) {
    CyclicBarrier barrier = new CyclicBarrier(actions.length);
    ExecutorService executor = Executors.newFixedThreadPool(actions.length);

    try {
      CompletableFuture.allOf(java.util.Arrays.stream(actions)
        .map(action -> CompletableFuture.runAsync(() -> {
          awaitBarrier(barrier);
          action.run();
        }, executor))
        .toArray(CompletableFuture[]::new))
        .join();
    } finally {
      executor.shutdownNow();
    }
  }

  private void awaitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (BrokenBarrierException e) {
      throw new IllegalStateException(e);
    }
  }

  private void awaitLatch(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private EventClient newEventClient() {
    return new EventClient(new OkapiClient(getMockedOkapiUrl(), OKAPI_TENANT, OKAPI_TOKEN));
  }
}
