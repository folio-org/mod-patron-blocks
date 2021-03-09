package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.domain.Condition.MAX_NUMBER_OF_ITEMS_CHARGED_OUT;
import static org.folio.domain.Condition.MAX_NUMBER_OF_LOST_ITEMS;
import static org.folio.domain.Condition.MAX_NUMBER_OF_OVERDUE_ITEMS;
import static org.folio.domain.Condition.MAX_NUMBER_OF_OVERDUE_RECALLS;
import static org.folio.domain.Condition.MAX_OUTSTANDING_FEE_FINE_BALANCE;
import static org.folio.domain.Condition.RECALL_OVERDUE_BY_MAX_NUMBER_OF_DAYS;
import static org.folio.repository.PatronBlockLimitsRepository.PATRON_BLOCK_LIMITS_TABLE_NAME;
import static org.folio.repository.UserSummaryRepository.USER_SUMMARY_TABLE_NAME;
import static org.folio.rest.utils.EntityBuilder.buildFeeFineBalanceChangedEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemAgedToLostEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemClaimedReturnedEvent;
import static org.folio.rest.utils.EntityBuilder.buildItemDeclaredLostEvent;
import static org.folio.rest.utils.EntityBuilder.buildLoanDueDateChangedEvent;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.joda.time.DateTime.now;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.folio.domain.Condition;
import org.folio.repository.PatronBlockConditionsRepository;
import org.folio.repository.PatronBlockLimitsRepository;
import org.folio.repository.UserSummaryRepository;
import org.folio.rest.TestBase;
import org.folio.rest.handlers.FeeFineBalanceChangedEventHandler;
import org.folio.rest.handlers.ItemAgedToLostEventHandler;
import org.folio.rest.handlers.ItemCheckedOutEventHandler;
import org.folio.rest.handlers.ItemClaimedReturnedEventHandler;
import org.folio.rest.handlers.ItemDeclaredLostEventHandler;
import org.folio.rest.handlers.LoanDueDateChangedEventHandler;
import org.folio.rest.jaxrs.model.AutomatedPatronBlock;
import org.folio.rest.jaxrs.model.AutomatedPatronBlocks;
import org.folio.rest.jaxrs.model.OpenFeeFine;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.PatronBlockCondition;
import org.folio.rest.jaxrs.model.PatronBlockLimit;
import org.folio.rest.jaxrs.model.UserSummary;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class AutomatedPatronBlocksAPITest extends TestBase {
  private static final String PATRON_GROUP_ID = randomId();
  private static final boolean SINGLE_LIMIT = true;
  private static final boolean ALL_LIMITS = false;
  private static final String LOAN_POLICY_ID = randomId();

  private final PatronBlockLimitsRepository limitsRepository =
    new PatronBlockLimitsRepository(postgresClient);
  private final UserSummaryRepository summaryRepository =
    new UserSummaryRepository(postgresClient);
  private final PatronBlockConditionsRepository conditionsRepository =
    new PatronBlockConditionsRepository(postgresClient);

  private final ItemCheckedOutEventHandler itemCheckedOutEventHandler =
    new ItemCheckedOutEventHandler(postgresClient);
  private final ItemDeclaredLostEventHandler itemDeclaredLostEventHandler =
    new ItemDeclaredLostEventHandler(postgresClient);
  private final ItemAgedToLostEventHandler itemAgedToLostEventHandler =
    new ItemAgedToLostEventHandler(postgresClient);
  private final LoanDueDateChangedEventHandler loanDueDateChangedEventHandler =
    new LoanDueDateChangedEventHandler(postgresClient);
  private final FeeFineBalanceChangedEventHandler feeFineBalanceChangedEventHandler =
    new FeeFineBalanceChangedEventHandler(postgresClient);
  private final ItemClaimedReturnedEventHandler itemClaimedReturnedEventHandler =
    new ItemClaimedReturnedEventHandler(postgresClient);

  private static final EnumMap<Condition, Integer> LIMIT_VALUES;
  static {
    LIMIT_VALUES = new EnumMap<>(Condition.class);
    LIMIT_VALUES.put(MAX_NUMBER_OF_ITEMS_CHARGED_OUT, 20);
    LIMIT_VALUES.put(MAX_NUMBER_OF_LOST_ITEMS, 8);
    LIMIT_VALUES.put(MAX_NUMBER_OF_OVERDUE_ITEMS, 16);
    LIMIT_VALUES.put(MAX_NUMBER_OF_OVERDUE_RECALLS, 12);
    LIMIT_VALUES.put(RECALL_OVERDUE_BY_MAX_NUMBER_OF_DAYS, 24);
    LIMIT_VALUES.put(MAX_OUTSTANDING_FEE_FINE_BALANCE, 28);
  }

  private String userId;
  private boolean expectBlockBorrowing;
  private boolean expectBlockRenewals;
  private boolean expectBlockRequests;

  @Before
  public void beforeEach() {
    super.resetMocks();
    mockUsersResponse();
    deleteAllFromTable(PATRON_BLOCK_LIMITS_TABLE_NAME);
    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);

    userId = randomId();

    expectBlockBorrowing = false;
    expectBlockRenewals = false;
    expectBlockRequests = false;

    Arrays.stream(Condition.values())
      .forEach(condition -> updateCondition(condition, true, true, true));

    wireMock.stubFor(get(urlEqualTo("/loan-policy-storage/loan-policies/" + LOAN_POLICY_ID))
      .atPriority(5)
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(makeLoanPolicyResponseBody())));
  }

  @Test
  public void shouldReturnBadRequestErrorWhenCalledWithInvalidUserId() {
    sendRequest("invalid")
      .then()
      .statusCode(400)
      .contentType(ContentType.TEXT)
      .body(equalTo("Invalid user UUID: \"invalid\""));
  }

  @Test
  public void shouldReturnNoBlocksWhenUserSummaryDoesNotExist() {
    String emptyBlocksResponse = toJson(new AutomatedPatronBlocks());
    sendRequestAndCheckResult(randomId(), emptyBlocksResponse);
  }

  @Test
  public void shouldReturnNoBlocksWhenNoLimitsExistForPatronGroup() {
    String emptyBlocksResponse = toJson(new AutomatedPatronBlocks());
    sendRequestAndCheckResult(randomId(), emptyBlocksResponse);
  }

  private void validateMaxNumberOfItemsChargedOutBlockResponse(int openLoansSizeDelta,
    boolean singleLimit) {

    final Condition condition = MAX_NUMBER_OF_ITEMS_CHARGED_OUT;
    final int limitValue = LIMIT_VALUES.get(condition);

    IntStream.range(0, limitValue + openLoansSizeDelta)
      .forEach(num -> {
        String loanId = randomId();
        Date dueDate = now().plusHours(1).toDate();

        stubLoan(loanId, dueDate, false);

        waitFor(itemCheckedOutEventHandler.handle(
          buildItemCheckedOutEvent(userId, loanId, dueDate)));
      });

    String expectedResponse = createLimitsAndBuildExpectedResponse(condition, singleLimit);

    sendRequestAndCheckResult(expectedResponse);
  }

  @Test
  public void noBlockWhenMaxNumberOfItemsChargedOutLimitIsNotReached() {
    expectNoBlocks();
    validateMaxNumberOfItemsChargedOutBlockResponse(-1, SINGLE_LIMIT);
  }

  @Test
  public void noBlockWhenMaxNumberOfItemsChargedOutLimitIsNotReachedAndAllLimitsExist() {
    expectNoBlocks();
    validateMaxNumberOfItemsChargedOutBlockResponse(-1, ALL_LIMITS);
  }

  @Test
  public void blockWhenMaxNumberOfItemsChargedOutLimitIsReached() {
    expectBlockBorrowing = true;
    expectBlockRenewals = false;
    expectBlockRequests = false;
    validateMaxNumberOfItemsChargedOutBlockResponse(0, SINGLE_LIMIT);
  }

  @Test
  public void blockWhenMaxNumberOfItemsChargedOutLimitIsReachedAndAllLimitsExist() {
    expectBlockBorrowing = true;
    expectBlockRenewals = false;
    expectBlockRequests = false;
    validateMaxNumberOfItemsChargedOutBlockResponse(0, ALL_LIMITS);
  }

  @Test
  public void blockWhenMaxNumberOfItemsChargedOutLimitIsExceeded() {
    expectAllBlocks();
    validateMaxNumberOfItemsChargedOutBlockResponse(1, SINGLE_LIMIT);
  }

  @Test
  public void blockWhenMaxNumberOfItemsChargedOutLimitIsExceededAndAllLimitsExist() {
    expectAllBlocks();
    validateMaxNumberOfItemsChargedOutBlockResponse(1, ALL_LIMITS);
  }

  private void validateMaxNumberOfLostItemsBlockResponseWithItemsDeclaredLost(
    int lostItemsDelta, boolean singleLimit) {

    final Condition condition = MAX_NUMBER_OF_LOST_ITEMS;
    int limitValue = LIMIT_VALUES.get(condition);

    IntStream.range(0, limitValue + lostItemsDelta)
      .forEach(num -> {
        String loanId = randomId();
        Date dueDate = now().plusHours(1).toDate();

        stubLoan(loanId, dueDate, false);

        waitFor(itemCheckedOutEventHandler.handle(
          buildItemCheckedOutEvent(userId, loanId, dueDate)));

        waitFor(itemDeclaredLostEventHandler.handle(buildItemDeclaredLostEvent(userId, loanId)));
      });

    String expectedResponse = createLimitsAndBuildExpectedResponse(condition, singleLimit);

    sendRequestAndCheckResult(expectedResponse);
  }

  @Test
  public void noBlockWhenMaxNumberOfLostItemsLimitIsNotReachedWithItemsDeclaredLost() {
    expectNoBlocks();
    validateMaxNumberOfLostItemsBlockResponseWithItemsDeclaredLost(-1, SINGLE_LIMIT);
  }

  @Test
  public void noBlockWhenMaxNumberOfLostItemsLimitIsNotReachedWithItemsDeclaredLostAndAllLimitsExist() {
    expectNoBlocks();
    validateMaxNumberOfLostItemsBlockResponseWithItemsDeclaredLost(-1, ALL_LIMITS);
  }

  @Test
  public void noBlockWhenMaxNumberOfLostItemsLimitIsReachedWithItemsDeclaredLost() {
    expectNoBlocks();
    validateMaxNumberOfLostItemsBlockResponseWithItemsDeclaredLost(0, SINGLE_LIMIT);
  }

  @Test
  public void noBlockWhenMaxNumberOfLostItemsLimitIsReachedWithItemsDeclaredLostAndAllLimitsExist() {
    expectNoBlocks();
    validateMaxNumberOfLostItemsBlockResponseWithItemsDeclaredLost(0, ALL_LIMITS);
  }

  @Test
  public void blockWhenMaxNumberOfLostItemsLimitIsExceededWithItemsDeclaredLost() {
    expectAllBlocks();
    validateMaxNumberOfLostItemsBlockResponseWithItemsDeclaredLost(1, SINGLE_LIMIT);
  }

  @Test
  public void blockWhenMaxNumberOfLostItemsLimitIsExceededWithItemsDeclaredLostAndAllLimitsExist() {
    expectAllBlocks();
    validateMaxNumberOfLostItemsBlockResponseWithItemsDeclaredLost(1, ALL_LIMITS);
  }

  private void validateMaxNumberOfLostItemsBlockResponseWithItemsAgedToLost(
    int lostItemsDelta, boolean singleLimit) {

    final Condition condition = MAX_NUMBER_OF_LOST_ITEMS;
    int limitValue = LIMIT_VALUES.get(condition);

    IntStream.range(0, limitValue + lostItemsDelta)
      .forEach(num -> {
        String loanId = randomId();
        Date dueDate = now().plusHours(1).toDate();

        stubLoan(loanId, dueDate, false);

        waitFor(itemCheckedOutEventHandler.handle(
          buildItemCheckedOutEvent(userId, loanId, dueDate)));

        waitFor(itemAgedToLostEventHandler.handle(buildItemAgedToLostEvent(userId, loanId)));
      });

    String expectedResponse = createLimitsAndBuildExpectedResponse(condition, singleLimit);

    sendRequestAndCheckResult(expectedResponse);
  }

  @Test
  public void noBlockWhenMaxNumberOfLostItemsLimitIsNotReachedWithItemsAgedToLost() {
    expectNoBlocks();
    validateMaxNumberOfLostItemsBlockResponseWithItemsAgedToLost(-1, SINGLE_LIMIT);
  }

  @Test
  public void noBlockWhenMaxNumberOfLostItemsLimitIsNotReachedWithItemsAgedToLostAndAllLimitsExist() {
    expectNoBlocks();
    validateMaxNumberOfLostItemsBlockResponseWithItemsAgedToLost(-1, ALL_LIMITS);
  }

  @Test
  public void noBlockWhenMaxNumberOfLostItemsLimitIsReachedWithItemsAgedToLost() {
    expectNoBlocks();
    validateMaxNumberOfLostItemsBlockResponseWithItemsAgedToLost(0, SINGLE_LIMIT);
  }

  @Test
  public void noBlockWhenMaxNumberOfLostItemsLimitIsReachedWithItemsAgedToLostAndAllLimitsExist() {
    expectNoBlocks();
    validateMaxNumberOfLostItemsBlockResponseWithItemsAgedToLost(0, ALL_LIMITS);
  }

  @Test
  public void blockWhenMaxNumberOfLostItemsLimitIsExceededWithItemsAgedToLost() {
    expectAllBlocks();
    validateMaxNumberOfLostItemsBlockResponseWithItemsAgedToLost(1, SINGLE_LIMIT);
  }

  @Test
  public void blockWhenMaxNumberOfLostItemsLimitIsExceededWithItemsAgedToLostAndAllLimitsExist() {
    expectAllBlocks();
    validateMaxNumberOfLostItemsBlockResponseWithItemsAgedToLost(1, ALL_LIMITS);
  }

  private void validateMaxOverdueItemsBlockResponse(int openLoansSizeDelta, boolean singleLimit) {
    final Condition condition = MAX_NUMBER_OF_OVERDUE_ITEMS;
    int limitValue = LIMIT_VALUES.get(condition);

    IntStream.range(0, limitValue + openLoansSizeDelta)
      .forEach(num -> {
        String loanId = randomId();
        Date dueDate = now().minusHours(1).toDate();

        stubLoan(loanId, dueDate, false);

        waitFor(itemCheckedOutEventHandler.handle(
          buildItemCheckedOutEvent(userId, loanId, dueDate)));
      });

    String expectedResponse = createLimitsAndBuildExpectedResponse(condition, singleLimit);

    sendRequestAndCheckResult(expectedResponse);
  }

  @Test
  public void noBlockWhenMaxNumberOfOverdueItemsLimitIsNotReached() {
    expectNoBlocks();
    validateMaxOverdueItemsBlockResponse(-1, SINGLE_LIMIT);
  }

  @Test
  public void noBlockWhenMaxNumberOfOverdueItemsLimitIsNotReachedAndAllLimitsExist() {
    expectNoBlocks();
    validateMaxOverdueItemsBlockResponse(-1, ALL_LIMITS);
  }

  @Test
  public void noBlockWhenMaxNumberOfOverdueItemsLimitIsReached() {
    expectNoBlocks();
    validateMaxOverdueItemsBlockResponse(0, SINGLE_LIMIT);
  }

  @Test
  public void noBlockWhenMaxNumberOfOverdueItemsLimitReachedAndAllLimitsExist() {
    expectNoBlocks();
    validateMaxOverdueItemsBlockResponse(0, ALL_LIMITS);
  }

  @Test
  public void blockWhenMaxNumberOfOverdueItemsLimitIsExceeded() {
    expectAllBlocks();
    validateMaxOverdueItemsBlockResponse(1, SINGLE_LIMIT);
  }

  @Test
  public void blockWhenMaxNumberOfOverdueItemsLimitIsExceededAndAllLimitsExist() {
    expectAllBlocks();
    validateMaxOverdueItemsBlockResponse(1, ALL_LIMITS);
  }

  private void validateMaxOverdueRecallsBlockResponse(int openLoansSizeDelta, boolean singleLimit) {
    Condition condition = MAX_NUMBER_OF_OVERDUE_RECALLS;
    int limitValue = LIMIT_VALUES.get(condition);

    IntStream.range(0, limitValue + openLoansSizeDelta)
      .forEach(num -> {
        String loanId = randomId();
        Date dueDate = now().minusHours(1).toDate();

        stubLoan(loanId, dueDate, true);

        waitFor(itemCheckedOutEventHandler.handle(
          buildItemCheckedOutEvent(userId, loanId, dueDate)));

        waitFor(loanDueDateChangedEventHandler.handle(
          buildLoanDueDateChangedEvent(userId, loanId, dueDate, true)));
      });

    String expectedResponse = createLimitsAndBuildExpectedResponse(condition, singleLimit);

    sendRequestAndCheckResult(expectedResponse);
  }

  @Test
  public void noBlockWhenMaxNumberOfOverdueRecallsLimitIsNotReached() {
    expectNoBlocks();
    validateMaxOverdueRecallsBlockResponse(-1, SINGLE_LIMIT);
  }

  @Test
  public void noBlockWhenMaxNumberOfOverdueRecallsLimitIsNotReachedAndAllLimitsExist() {
    expectNoBlocks();
    validateMaxOverdueRecallsBlockResponse(-1, ALL_LIMITS);
  }

  @Test
  public void noBlockWhenMaxNumberOfOverdueRecallsLimitIsReached() {
    expectNoBlocks();
    validateMaxOverdueRecallsBlockResponse(0, SINGLE_LIMIT);
  }

  @Test
  public void noBlockWhenMaxNumberOfOverdueRecallsLimitIsReachedAndAllLimitsExist() {
    expectNoBlocks();
    validateMaxOverdueRecallsBlockResponse(0, ALL_LIMITS);
  }

  @Test
  public void blockWhenMaxNumberOfOverdueRecallsLimitIsExceeded() {
    expectAllBlocks();
    validateMaxOverdueRecallsBlockResponse(1, SINGLE_LIMIT);
  }

  @Test
  public void blockWhenMaxNumberOfOverdueRecallsLimitIsExceededAllLimitsExist() {
    expectAllBlocks();
    validateMaxOverdueRecallsBlockResponse(1, ALL_LIMITS);
  }

  private void validateRecallOverdueByMaximumNumberOfDaysBlockResponse(int dueDateDelta,
    boolean singleLimit) {

    final Condition condition = RECALL_OVERDUE_BY_MAX_NUMBER_OF_DAYS;
    int limitValue = LIMIT_VALUES.get(condition);

    String loanId = randomId();
    Date dueDate = now().minusDays(limitValue + dueDateDelta).toDate();

    stubLoan(loanId, dueDate, true);

    waitFor(itemCheckedOutEventHandler.handle(
      buildItemCheckedOutEvent(userId, loanId, dueDate)));

    waitFor(loanDueDateChangedEventHandler.handle(
      buildLoanDueDateChangedEvent(userId, loanId, dueDate, true)));

    String expectedResponse = createLimitsAndBuildExpectedResponse(condition, singleLimit);

    sendRequestAndCheckResult(expectedResponse);
  }

  @Test
  public void noBlockWhenRecallOverdueByMaximumNumberOfDaysLimitIsNotReached() {
    expectNoBlocks();
    validateRecallOverdueByMaximumNumberOfDaysBlockResponse(-1, SINGLE_LIMIT);
  }

  @Test
  public void noBlockWhenRecallOverdueByMaximumNumberOfDaysLimitIsNotReachedAndAllLimitsExist() {
    expectNoBlocks();
    validateRecallOverdueByMaximumNumberOfDaysBlockResponse(-1, ALL_LIMITS);
  }

  @Test
  public void noBlockWhenRecallOverdueByMaximumNumberOfDaysLimitIsReached() {
    expectNoBlocks();
    validateRecallOverdueByMaximumNumberOfDaysBlockResponse(0, SINGLE_LIMIT);
  }

  @Test
  public void noBlockWhenRecallOverdueByMaximumNumberOfDaysLimitIsReachedAndAllLimitsExist() {
    expectNoBlocks();
    validateRecallOverdueByMaximumNumberOfDaysBlockResponse(0, ALL_LIMITS);
  }

  @Test
  public void blockWhenRecallOverdueByMaximumNumberOfDaysLimitIsExceeded() {
    expectAllBlocks();
    validateRecallOverdueByMaximumNumberOfDaysBlockResponse(1, SINGLE_LIMIT);
  }

  @Test
  public void blockWhenRecallOverdueByMaximumNumberOfDaysLimitIsExceededAllLimitsExist() {
    expectAllBlocks();
    validateRecallOverdueByMaximumNumberOfDaysBlockResponse(1, ALL_LIMITS);
  }

  private void validateMaxOutstandingFeeFineBalanceBlockResponse(int feeFineBalanceDelta,
    boolean singleLimit) {

    final Condition condition = MAX_OUTSTANDING_FEE_FINE_BALANCE;
    int limitValue = LIMIT_VALUES.get(condition);
    int numberOfFeesFines = 2;

    BigDecimal balancePerFeeFine = BigDecimal.valueOf(limitValue + feeFineBalanceDelta)
      .divide(BigDecimal.valueOf(numberOfFeesFines), 2, RoundingMode.UNNECESSARY);

    IntStream.range(0, numberOfFeesFines)
      .forEach(num -> waitFor(feeFineBalanceChangedEventHandler.handle(
          buildFeeFineBalanceChangedEvent(userId, randomId(), randomId(), randomId(),
            balancePerFeeFine))));

    String expectedResponse = createLimitsAndBuildExpectedResponse(condition, singleLimit);

    sendRequestAndCheckResult(expectedResponse);
  }

  @Test
  public void noBlockWhenMaxOutstandingFeeFineBalanceLimitIsNotReached() {
    expectNoBlocks();
    validateMaxOutstandingFeeFineBalanceBlockResponse(-1, SINGLE_LIMIT);
  }

  @Test
  public void noBlockWhenMaxOutstandingFeeFineBalanceLimitIsNotReachedAndAllLimitsExist() {
    expectNoBlocks();
    validateMaxOutstandingFeeFineBalanceBlockResponse(-1, ALL_LIMITS);
  }

  @Test
  public void noBlockWhenMaxOutstandingFeeFineBalanceLimitIsReached() {
    expectNoBlocks();
    validateMaxOutstandingFeeFineBalanceBlockResponse(0, SINGLE_LIMIT);
  }

  @Test
  public void noBlockWhenMaxOutstandingFeeFineBalanceLimitIsReachedAndAllLimitsExist() {
    expectNoBlocks();
    validateMaxOutstandingFeeFineBalanceBlockResponse(0, ALL_LIMITS);
  }

  @Test
  public void blockWhenMaxOutstandingFeeFineBalanceLimitIsExceeded() {
    expectAllBlocks();
    validateMaxOutstandingFeeFineBalanceBlockResponse(1, SINGLE_LIMIT);
  }

  @Test
  public void blockWhenMaxOutstandingFeeFineBalanceLimitIsExceededAllLimitsExist() {
    expectAllBlocks();
    validateMaxOutstandingFeeFineBalanceBlockResponse(1, ALL_LIMITS);
  }

  @Test
  public void everythingIsBlockedWhenAllLimitsAreExceeded() {
    expectAllBlocks();
    exceedAllLimits(false);
    String expectedResponse = buildDefaultResponseFor(Condition.values());
    sendRequestAndCheckResult(expectedResponse);
  }

  @Test
  public void nothingIsBlockedWhenAllLimitsAreExceededForItemsClaimedReturned() {
    expectNoBlocks();
    exceedAllLimits(true);
    sendRequestAndCheckResult(toJson(new AutomatedPatronBlocks()));
  }

  private void exceedAllLimits(boolean claimedReturned) {
    createLimitsForAllConditions();

    int numberOfOpenLoans = Collections.max(Arrays.asList(
      LIMIT_VALUES.get(MAX_NUMBER_OF_LOST_ITEMS),
      LIMIT_VALUES.get(MAX_NUMBER_OF_ITEMS_CHARGED_OUT),
      LIMIT_VALUES.get(MAX_NUMBER_OF_OVERDUE_ITEMS),
      LIMIT_VALUES.get(MAX_NUMBER_OF_OVERDUE_RECALLS))) + 1;

    BigDecimal balance = BigDecimal.valueOf(LIMIT_VALUES.get(MAX_OUTSTANDING_FEE_FINE_BALANCE) + 1);

    IntStream.range(0, numberOfOpenLoans)
      .forEach(num -> {
        String loanId = randomId();
        Date dueDate = now().minusDays(LIMIT_VALUES.get(RECALL_OVERDUE_BY_MAX_NUMBER_OF_DAYS) + 1)
          .toDate();

        stubLoan(loanId, dueDate, true);

        waitFor(itemCheckedOutEventHandler.handle(
          buildItemCheckedOutEvent(userId, loanId, dueDate)));

        waitFor(loanDueDateChangedEventHandler.handle(
          buildLoanDueDateChangedEvent(userId, loanId, dueDate, true)));

        waitFor(itemDeclaredLostEventHandler.handle(buildItemDeclaredLostEvent(userId, loanId)));

        if (claimedReturned) {
          waitFor(itemClaimedReturnedEventHandler.handle(
            buildItemClaimedReturnedEvent(userId, loanId)));
        }

        if (num == 0) {
          waitFor(feeFineBalanceChangedEventHandler.handle(
            buildFeeFineBalanceChangedEvent(userId, loanId, randomId(), randomId(),
              balance)));
        }
      });
  }

  @Test
  public void updatedValuesFromConditionArePassedToResponse(TestContext context) {
    final Condition condition = MAX_NUMBER_OF_ITEMS_CHARGED_OUT;
    int limitValue = LIMIT_VALUES.get(condition);

    createLimitsForAllConditions();

    IntStream.range(0, limitValue + 1)
      .forEach(num -> {
        String loanId = randomId();
        Date dueDate = now().plusHours(1).toDate();

        stubLoan(loanId, dueDate, false);

        waitFor(itemCheckedOutEventHandler.handle(
          buildItemCheckedOutEvent(userId, loanId, dueDate)));
      });

    Optional<PatronBlockCondition> optionalResult =
      waitFor(conditionsRepository.get(condition.getId()));

    context.assertTrue(optionalResult.isPresent());

    final PatronBlockCondition originalCondition = optionalResult.get();

    PatronBlockCondition updatedCondition = new PatronBlockCondition()
      .withId(condition.getId())
      .withBlockBorrowing(!originalCondition.getBlockBorrowing())
      .withBlockRenewals(originalCondition.getBlockRenewals())
      .withBlockRequests(!originalCondition.getBlockRequests())
      .withMessage(EMPTY);

    waitFor(conditionsRepository.update(updatedCondition));

    String expectedResponse = toJson(
      new AutomatedPatronBlocks().withAutomatedPatronBlocks(Stream.of(
        createBlock(condition, updatedCondition.getMessage(), updatedCondition.getBlockBorrowing(),
          updatedCondition.getBlockRenewals(), updatedCondition.getBlockRequests()))
        .filter(Objects::nonNull)
        .collect(toList())
      ));

    sendRequestAndCheckResult(expectedResponse);

    context.assertTrue(waitFor(conditionsRepository.update(originalCondition)));
  }

  @Test
  public void noBlockWhenLoanIsNotOverdue() {
    expectNoBlocks();

    final Condition condition = MAX_NUMBER_OF_OVERDUE_ITEMS;
    int limitValue = LIMIT_VALUES.get(condition);

    IntStream.range(0, limitValue + 1)
      .forEach(num -> {
        String loanId = randomId();
        Date dueDate = now().plusHours(1).toDate();

        stubLoan(loanId, dueDate, false);

        waitFor(itemCheckedOutEventHandler.handle(
          buildItemCheckedOutEvent(userId, loanId, dueDate)));
      });

    String expectedResponse = createLimitsAndBuildExpectedResponse(condition, true);

    sendRequest(userId)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body(equalTo(expectedResponse));
  }

  @Test
  public void noBlockWhenLoanIsNotOverdueBecauseOfGracePeriod() {
    expectNoBlocks();

    wireMock.stubFor(get(urlEqualTo("/loan-policy-storage/loan-policies/" + LOAN_POLICY_ID))
      .atPriority(5)
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(makeLoanPolicyWithGracePeriodResponseBody())));

    final Condition condition = MAX_NUMBER_OF_OVERDUE_ITEMS;
    int limitValue = LIMIT_VALUES.get(condition);

    IntStream.range(0, limitValue + 1)
      .forEach(num -> {
        String loanId = randomId();
        Date dueDate = now().minusHours(1).toDate();

        stubLoan(loanId, dueDate, false);

        waitFor(itemCheckedOutEventHandler.handle(
          buildItemCheckedOutEvent(userId, loanId, dueDate)));
      });

    String expectedResponse = createLimitsAndBuildExpectedResponse(condition, true);

    sendRequest(userId)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body(equalTo(expectedResponse));
  }

  @Test
  public void blockWhenLoanIsOverdue() {
    expectAllBlocks();

    final Condition condition = MAX_NUMBER_OF_OVERDUE_ITEMS;
    int limitValue = LIMIT_VALUES.get(condition);

    IntStream.range(0, limitValue + 1)
      .forEach(num -> {
        String loanId = randomId();
        Date dueDate = now().minusHours(1).toDate();

        stubLoan(loanId, dueDate, false);

        waitFor(itemCheckedOutEventHandler.handle(
          buildItemCheckedOutEvent(userId, loanId, dueDate)));
      });

    String expectedResponse = createLimitsAndBuildExpectedResponse(condition, true);

    sendRequest(userId)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body(equalTo(expectedResponse));
  }

  @Test
  public void blockWhenLoanIsOverdueAndGracePeriodExists() {
    expectAllBlocks();

    wireMock.stubFor(get(urlEqualTo("/loan-policy-storage/loan-policies/" + LOAN_POLICY_ID))
      .atPriority(5)
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(makeLoanPolicyWithGracePeriodResponseBody())));

    final Condition condition = MAX_NUMBER_OF_OVERDUE_ITEMS;
    int limitValue = LIMIT_VALUES.get(condition);

    IntStream.range(0, limitValue + 1)
      .forEach(num -> {
        String loanId = randomId();
        Date dueDate = now().minusHours(6).toDate();

        stubLoan(loanId, dueDate, false);

        waitFor(itemCheckedOutEventHandler.handle(
          buildItemCheckedOutEvent(userId, loanId, dueDate)));
      });

    String expectedResponse = createLimitsAndBuildExpectedResponse(condition, true);

    sendRequest(userId)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body(equalTo(expectedResponse));
  }

  @Test
  public void itemsDeclaredLostAndAgedToLostAreCombinedForMaxNumberOfLostItemsBlock() {
    expectAllBlocks();

    final Condition condition = MAX_NUMBER_OF_LOST_ITEMS;
    int limitValue = LIMIT_VALUES.get(condition);

    int totalNumberOfEvents = limitValue + 1;
    int numberOfItemDeclaredLostEvents = totalNumberOfEvents / 2;
    int numberOfItemAgedToLostEvents = totalNumberOfEvents - numberOfItemDeclaredLostEvents;

    IntStream.range(0, numberOfItemDeclaredLostEvents)
      .forEach(num -> {
        String loanId = randomId();
        Date dueDate = now().plusHours(1).toDate();

        stubLoan(loanId, dueDate, false);

        waitFor(itemCheckedOutEventHandler.handle(
          buildItemCheckedOutEvent(userId, loanId, dueDate)));

        waitFor(itemAgedToLostEventHandler.handle(buildItemAgedToLostEvent(userId, loanId)));
      });

    IntStream.range(0, numberOfItemAgedToLostEvents)
      .forEach(num -> {
        String loanId = randomId();
        Date dueDate = now().plusHours(1).toDate();

        stubLoan(loanId, dueDate, false);

        waitFor(itemCheckedOutEventHandler.handle(
          buildItemCheckedOutEvent(userId, loanId, dueDate)));

        waitFor(itemAgedToLostEventHandler.handle(buildItemAgedToLostEvent(userId, loanId)));
      });

    String expectedResponse = createLimitsAndBuildExpectedResponse(condition, true);

    sendRequestAndCheckResult(expectedResponse);
  }

  private Response sendRequest(String userId) {
    return okapiClient.get("automated-patron-blocks/" + userId);
  }

  private ValidatableResponse sendRequestAndCheckResult(String userId, String expectedResponse) {
    return sendRequest(userId)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body(equalTo(expectedResponse));
  }

  private ValidatableResponse sendRequestAndCheckResult(String expectedResponse) {
    return sendRequest(userId)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body(equalTo(expectedResponse));
  }

  private void mockUsersResponse() {
    String mockResponse = new JsonObject()
      .put("id", userId)
      .put("patronGroup", PATRON_GROUP_ID)
      .encodePrettily();

    wireMock.stubFor(get(urlPathMatching("/users/.+"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(mockResponse)
      ));
  }

  private void updateCondition(Condition condition, boolean blockBorrowing, boolean blockRenewals,
    boolean blockRequests) throws IllegalArgumentException {

    PatronBlockCondition patronBlockCondition =
      waitFor(conditionsRepository.get(condition.getId()))
        .orElseThrow(() -> new IllegalArgumentException("Invalid condition ID"))
        .withBlockBorrowing(blockBorrowing)
        .withBlockRenewals(blockRenewals)
        .withBlockRequests(blockRequests);

    waitFor(conditionsRepository.update(patronBlockCondition));
  }

  private String createLimit(Condition condition, String patronGroupId, double value) {
    PatronBlockLimit limit = new PatronBlockLimit()
      .withId(randomId())
      .withConditionId(condition.getId())
      .withPatronGroupId(patronGroupId)
      .withValue(value);

    return waitFor(limitsRepository.save(limit));
  }

  private String createSummary(String userId, List<OpenFeeFine> feesFines,
    List<OpenLoan> openLoans) {

    UserSummary userSummary = new UserSummary()
      .withId(randomId())
      .withUserId(userId)
      .withOpenFeesFines(feesFines)
      .withOpenLoans(openLoans);

    return waitFor(summaryRepository.save(userSummary));
  }

  private AutomatedPatronBlock createBlock(Condition condition, String message) {
    return createBlock(condition, message, expectBlockBorrowing, expectBlockRenewals, expectBlockRequests);
  }

  private AutomatedPatronBlock createBlock(Condition condition, String message,
    boolean blockBorrowing, boolean blockRenewals, boolean blockRequests) {

    if (blockBorrowing || blockRenewals || blockRequests) {
      return new AutomatedPatronBlock()
        .withMessage(message)
        .withBlockBorrowing(blockBorrowing)
        .withBlockRenewals(blockRenewals)
        .withBlockRequests(blockRequests)
        .withPatronBlockConditionId(condition.getId());
    }

    return null;
  }

  private void createLimitsForAllConditions() {
    for (Condition condition : Condition.values()) {
      createLimit(condition, PATRON_GROUP_ID, LIMIT_VALUES.get(condition));
    }
  }

  private String buildDefaultResponseFor(Condition... conditions) {
    return toJson(
      new AutomatedPatronBlocks().withAutomatedPatronBlocks(
        Arrays.stream(conditions)
          .map(condition -> createBlock(condition, EMPTY))
          .filter(Objects::nonNull)
          .collect(toList())
      ));
  }

  private String createLimitsAndBuildExpectedResponse(Condition condition, boolean singleLimit) {

    int limitValue = LIMIT_VALUES.get(condition);

    if (singleLimit) {
      createLimit(condition, PATRON_GROUP_ID, limitValue);
    } else {
      createLimitsForAllConditions();
    }

    return buildDefaultResponseFor(condition);
  }

  private void expectNoBlocks() {
    expectBlockBorrowing = false;
    expectBlockRenewals = false;
    expectBlockRequests = false;
  }

  private void expectAllBlocks() {
    expectBlockBorrowing = true;
    expectBlockRenewals = true;
    expectBlockRequests = true;
  }

  private static String makeLoanPolicyResponseBody() {
    return new JsonObject()
      .put("id", LOAN_POLICY_ID)
      .put("loansPolicy", new JsonObject()
        .put("gracePeriod", new JsonObject()
          .put("duration", 0)
          .put("intervalId", "Minutes")))
      .put("requestManagement", new JsonObject()
        .put("recalls", new JsonObject()
          .put("allowRecallsToExtendOverdueLoans", true)))
      .encodePrettily();
  }

  private static String makeLoanPolicyWithGracePeriodResponseBody() {
    return new JsonObject()
      .put("id", LOAN_POLICY_ID)
      .put("loansPolicy", new JsonObject()
        .put("gracePeriod", new JsonObject()
          .put("duration", 5)
          .put("intervalId", "Hours")))
      .put("requestManagement", new JsonObject()
        .put("recalls", new JsonObject()
          .put("allowRecallsToExtendOverdueLoans", true)))
      .encodePrettily();
  }

  private static String makeLoanResponseBody(Date dueDate, boolean recall, String itemId) {
    return new JsonObject()
      .put("id", randomId())
      .put("overdueFinePolicyId", randomId())
      .put("loanPolicyId", LOAN_POLICY_ID)
      .put("dueDate", new DateTime(dueDate).toString(ISODateTimeFormat.dateTime()))
      .put("dueDateChangedByRecall", recall)
      .put("itemId", itemId)
      .encodePrettily();
  }

  private static void stubLoan(String loanId, Date dueDate, boolean recall) {
    wireMock.stubFor(get(urlEqualTo("/loan-storage/loans/" + loanId))
      .atPriority(5)
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(makeLoanResponseBody(dueDate, recall, randomId()))));
  }
}
