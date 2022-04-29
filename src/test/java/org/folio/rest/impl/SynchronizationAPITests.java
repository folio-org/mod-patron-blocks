package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.rest.jaxrs.model.SynchronizationJob.Scope.FULL;
import static org.folio.rest.jaxrs.model.SynchronizationJob.Scope.USER;
import static org.folio.rest.utils.EntityBuilder.buildItemAgedToLostEvent;
import static org.folio.rest.utils.EntityBuilder.buildSynchronizationJob;
import static org.folio.rest.utils.matcher.SynchronizationJobMatchers.newSynchronizationJobByUser;
import static org.folio.rest.utils.matcher.SynchronizationJobMatchers.newSynchronizationJobFull;
import static org.folio.rest.utils.matcher.SynchronizationJobMatchers.synchronizationJobMatcher;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.joda.time.LocalDateTime.now;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;

import org.awaitility.Awaitility;
import org.folio.domain.SynchronizationStatus;
import org.folio.repository.EventRepository;
import org.folio.repository.SynchronizationJobRepository;
import org.folio.repository.UserSummaryRepository;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.jaxrs.model.ItemAgedToLostEvent;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.ItemClaimedReturnedEvent;
import org.folio.rest.jaxrs.model.ItemDeclaredLostEvent;
import org.folio.rest.jaxrs.model.LoanDueDateChangedEvent;
import org.folio.rest.jaxrs.model.SynchronizationJob;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class SynchronizationAPITests extends TestBase {
  private static final String SYNCHRONIZATION_JOBS_TABLE_NAME = "synchronization_jobs";
  private static final String ITEM_CHECKED_OUT_EVENT_TABLE_NAME = "item_checked_out_event";
  private static final String ITEM_DECLARED_LOST_EVENT_TABLE_NAME = "item_declared_lost_event";
  private static final String ITEM_CLAIMED_RETURNED_EVENT_TABLE_NAME = "item_claimed_returned_event";
  private static final String ITEM_AGED_TO_LOST_EVENT_TABLE_NAME = "item_aged_to_lost_event";
  private static final String LOAN_DUE_DATE_CHANGED_EVENT_TABLE_NAME = "loan_due_date_changed_event";
  private static final String FEE_FINE_BALANCE_CHANGED_EVENT_TABLE_NAME = "fee_fine_balance_changed_event";

  private static final String USER_ID = randomId();
  private static final String ACCOUNT_ID = randomId();
  private static final String FEE_FINE_TYPE_ID = randomId();
  private static final String FEE_FINE_TYPE = "Overdue fine";
  private static final String JOB_STATUS_DONE = "done";
  private static final String JOB_STATUS_FAILED = "failed";

  private EventRepository<ItemCheckedOutEvent> checkOutEventRepository = new EventRepository<>(
    postgresClient, ITEM_CHECKED_OUT_EVENT_TABLE_NAME, ItemCheckedOutEvent.class);
  private EventRepository<ItemClaimedReturnedEvent> itemClaimedReturnedEventRepository =
    new EventRepository<>(postgresClient, ITEM_CLAIMED_RETURNED_EVENT_TABLE_NAME,
      ItemClaimedReturnedEvent.class);
  private EventRepository<ItemDeclaredLostEvent> itemDeclaredLostEventRepository =
    new EventRepository<>(postgresClient, ITEM_DECLARED_LOST_EVENT_TABLE_NAME,
      ItemDeclaredLostEvent.class);
  private EventRepository<ItemAgedToLostEvent> itemAgedToLostEventRepository =
    new EventRepository<>(postgresClient, ITEM_AGED_TO_LOST_EVENT_TABLE_NAME,
      ItemAgedToLostEvent.class);
  private EventRepository<LoanDueDateChangedEvent> loanDueDateChangedEventRepository = new EventRepository<>(
    postgresClient, LOAN_DUE_DATE_CHANGED_EVENT_TABLE_NAME, LoanDueDateChangedEvent.class);
  private EventRepository<FeeFineBalanceChangedEvent> feeFineBalanceChangedEventRepository = new EventRepository<>(
    postgresClient, FEE_FINE_BALANCE_CHANGED_EVENT_TABLE_NAME, FeeFineBalanceChangedEvent.class);

  private final SynchronizationJobRepository synchronizationJobRepository =
    new SynchronizationJobRepository(postgresClient);

  private final UserSummaryRepository userSummaryRepository = new UserSummaryRepository(postgresClient);

  @Before
  public void beforeEach() {
    super.resetMocks();
    deleteAllFromTable(ITEM_CHECKED_OUT_EVENT_TABLE_NAME);
    deleteAllFromTable(ITEM_DECLARED_LOST_EVENT_TABLE_NAME);
    deleteAllFromTable(ITEM_CLAIMED_RETURNED_EVENT_TABLE_NAME);
    deleteAllFromTable(ITEM_AGED_TO_LOST_EVENT_TABLE_NAME);
    deleteAllFromTable(LOAN_DUE_DATE_CHANGED_EVENT_TABLE_NAME);
    deleteAllFromTable(FEE_FINE_BALANCE_CHANGED_EVENT_TABLE_NAME);
    deleteAllFromTable(SYNCHRONIZATION_JOBS_TABLE_NAME);
  }

  @Test
  public void shouldRespondWithSynchronizationJobFull() {
    String synchronizationJobId = createOpenSynchronizationJobFull();

    sendGetSynchronizationJob(synchronizationJobId)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body(is(newSynchronizationJobFull(synchronizationJobId)));
  }

  @Test
  public void shouldRespondWithSynchronizationJobByUser() {
    String synchronizationJobId = createOpenSynchronizationJobByUser();

    sendGetSynchronizationJob(synchronizationJobId)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body(is(newSynchronizationJobByUser(synchronizationJobId, USER_ID)));
  }

  @Test
  public void shouldRespond404WithNonExistingSynchronizationJobId() {
    sendGetSynchronizationJob(randomId())
      .then()
      .statusCode(404);
  }

  @Test
  public void checkOutEventShouldBeCreatedAfterSynchronization() {
    stubLoans(now().plusHours(1).toDate(), false, "Checked out");
    stubAccountsWithEmptyResponse();
    String syncJobId = createOpenSynchronizationJobByUser();

    runSynchronization();

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> waitFor(checkOutEventRepository.getByUserId(USER_ID)).size(), is(1));

    checkSyncJobUpdatedByLoanEvent(syncJobId);
  }

  @Test
  public void claimedReturnedEventShouldBeCreatedAfterSynchronization() {
    stubLoans(now().plusHours(1).toDate(), false, "Claimed returned");
    stubAccountsWithEmptyResponse();
    String syncJobId = createOpenSynchronizationJobByUser();

    runSynchronization();

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> waitFor(itemClaimedReturnedEventRepository.getByUserId(USER_ID)).size(), is(1));

    checkSyncJobUpdatedByLoanEvent(syncJobId);
  }

  @Test
  public void declaredLostEventShouldBeCreatedAfterSynchronization() {
    stubLoans(now().plusHours(1).toDate(), false, "Declared lost");
    stubAccountsWithEmptyResponse();
    String syncJobId = createOpenSynchronizationJobByUser();

    runSynchronization();

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> waitFor(itemDeclaredLostEventRepository.getByUserId(USER_ID)).size(), is(1));

    checkSyncJobUpdatedByLoanEvent(syncJobId);
  }

  @Test
  public void agedToLostEventShouldBeDeletedBeforeSynchronizationJobByUser() {
    eventClient.sendEvent(buildItemAgedToLostEvent(USER_ID, randomId()));
    assertThat(waitFor(itemAgedToLostEventRepository.getByUserId(USER_ID)).size(), is(1));
    String syncJobId = createOpenSynchronizationJobByUser();

    runSynchronization();

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> waitFor(itemAgedToLostEventRepository.getByUserId(USER_ID)).size(), is(0));

    checkSyncJob(syncJobId);
  }

  @Test
  public void agedToLostEventsShouldBeDeletedBeforeSynchronizationJobFull() {
    eventClient.sendEvent(buildItemAgedToLostEvent(randomId(), randomId()));
    eventClient.sendEvent(buildItemAgedToLostEvent(randomId(), randomId()));

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> waitFor(itemAgedToLostEventRepository.getAllWithDefaultLimit()).size(), is(2));

    String syncJobId = createOpenSynchronizationJobFull();

    runSynchronization();

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> waitFor(itemAgedToLostEventRepository.getAllWithDefaultLimit()).size(), is(0));

    checkSyncJob(syncJobId);
  }

  @Test
  public void dueDateChangedEventShouldBeCreatedAfterSynchronization() {
    stubLoans(now().plusHours(1).toDate(), true, "Checked out");
    stubAccountsWithEmptyResponse();
    String syncJobId = createOpenSynchronizationJobByUser();
    EventRepository<LoanDueDateChangedEvent> loanDueDateChangedEventRepository = new EventRepository<>(
      postgresClient, LOAN_DUE_DATE_CHANGED_EVENT_TABLE_NAME, LoanDueDateChangedEvent.class);

    runSynchronization();

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> waitFor(loanDueDateChangedEventRepository.getByUserId(USER_ID)).size(), is(1));

    checkSyncJobUpdatedByLoanEvent(syncJobId);
  }

  @Test
  public void feeFineBalanceChangedEventShouldBeCreatedAfterSynchronization() {
    stubLoansWithEmptyResponse();
    stubAccounts();
    String syncJobId = createOpenSynchronizationJobFull();

    runSynchronization();

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> waitFor(
        feeFineBalanceChangedEventRepository.getByUserId(USER_ID)).size(), is(1));

    List<FeeFineBalanceChangedEvent> feeFineBalanceChangedEvents = waitFor(
      feeFineBalanceChangedEventRepository.getByUserId(USER_ID));
    FeeFineBalanceChangedEvent generatedEvent = feeFineBalanceChangedEvents.get(0);

    assertThat(generatedEvent.getFeeFineTypeId(), is(FEE_FINE_TYPE_ID));
    assertThat(generatedEvent.getFeeFineId(), is(ACCOUNT_ID));

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> waitFor(synchronizationJobRepository.get(syncJobId))
        .orElse(null), synchronizationJobMatcher(JOB_STATUS_DONE, 0, 0, 0, 1));
  }

  @Test
  public void feeFineBalanceChangedEventShouldNotBeCreatedIfFeesFinesNotFound() {
    stubLoansWithEmptyResponse();
    stubAccounts();
    stubFeeFinesWith404();
    createOpenSynchronizationJobFull();

    runSynchronization();

    assertThat(waitFor(feeFineBalanceChangedEventRepository.getByUserId(USER_ID)).size(), is(0));
  }

  @Test
  public void shouldNotCreateAnyEvents() {
    stubLoansWithEmptyResponse();
    stubAccountsWithEmptyResponse();

    runSynchronization();

    assertThat(waitFor(checkOutEventRepository.getByUserId(USER_ID)).size(), is(0));
    assertThat(waitFor(itemClaimedReturnedEventRepository.getByUserId(USER_ID)).size(), is(0));
    assertThat(waitFor(itemDeclaredLostEventRepository.getByUserId(USER_ID)).size(), is(0));
    assertThat(waitFor(loanDueDateChangedEventRepository.getByUserId(USER_ID)).size(), is(0));
    assertThat(waitFor(feeFineBalanceChangedEventRepository.getByUserId(USER_ID)).size(), is(0));
  }

  @Test
  public void shouldNotDoAnythingIfSynchronizationIsInProgress() {
    stubLoans(now().plusHours(1).toDate(), true, "Checked out");
    stubAccountsWithEmptyResponse();
    SynchronizationJob synchronizationJob = buildSynchronizationJob(FULL, null,
      SynchronizationStatus.IN_PROGRESS, 0, 0, 0, 0);
    waitFor(synchronizationJobRepository.save(synchronizationJob));

    runSynchronization();
    assertThat(waitFor(checkOutEventRepository.getByUserId(USER_ID)).size(), is(0));
  }

  @Test
  public void syncJobShouldFailIfLoanStorageIsNotResponding() {
    stubAccountsWithEmptyResponse();
    String syncJobId = createOpenSynchronizationJobFull();

    runSynchronization();

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> waitFor(synchronizationJobRepository.get(syncJobId))
        .orElse(null), synchronizationJobMatcher(JOB_STATUS_DONE, 0, 0, 0, 0));
  }

  @Test
  public void syncJobShouldFailIfFeesFinesIsNotResponding() {
    stubLoansWithEmptyResponse();
    String syncJobId = createOpenSynchronizationJobFull();

    runSynchronization();

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> waitFor(synchronizationJobRepository.get(syncJobId))
        .orElse(null), synchronizationJobMatcher(JOB_STATUS_DONE, 0, 0, 0, 0));
  }

  @Test
  public void shouldReturn422IfSyncJobIsNotValid() {
    createNotValidSynchronizationJobByUser()
      .then()
      .statusCode(422);
  }

  @Test
  public void summaryShouldBeDeletedWhenNoEventsWereGeneratedDuringUserSynchronization() {
    stubLoansWithEmptyResponse();
    stubAccounts();
    String firstJobId = createOpenSynchronizationJobByUser();
    runSynchronization();

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> waitFor(synchronizationJobRepository.get(firstJobId))
        .orElse(null), is(synchronizationJobMatcher(JOB_STATUS_DONE, 0, 0, 0, 1)));

    getUserSummary().then().statusCode(200);

    stubAccountsWithEmptyResponse();
    String secondJobId = createOpenSynchronizationJobByUser();
    runSynchronization();

    Awaitility.await()
      .atMost(5, SECONDS)
      .until(() -> waitFor(synchronizationJobRepository.get(secondJobId))
        .orElse(null), is(synchronizationJobMatcher(JOB_STATUS_DONE, 0, 0, 0, 0)));

    getUserSummary().then().statusCode(404);
  }

  protected void checkThatStatusIsFailed(String syncJobId) {
    Awaitility.await()
      .atMost(30, SECONDS)
      .until(() -> waitFor(synchronizationJobRepository.get(syncJobId))
        .orElse(null), synchronizationJobMatcher(JOB_STATUS_FAILED, 0, 0, 0, 0));
  }

  protected void runSynchronization() {
    okapiClient.post("/automated-patron-blocks/synchronization/start", EMPTY)
      .then()
      .statusCode(202);
  }

  protected void checkSyncJob(String syncJobId) {
    Awaitility.await()
      .atMost(30, SECONDS)
      .until(() -> waitFor(synchronizationJobRepository.get(syncJobId))
        .orElse(null), synchronizationJobMatcher(JOB_STATUS_DONE, 0, 0, 0, 0));
  }

  protected void checkSyncJobUpdatedByLoanEvent(String syncJobId) {
    Awaitility.await()
      .atMost(30, SECONDS)
      .until(() -> waitFor(synchronizationJobRepository.get(syncJobId))
        .orElse(null), synchronizationJobMatcher(JOB_STATUS_DONE, 0, 0, 1, 0));
  }

  private String createOpenSynchronizationJobFull() {
    SynchronizationJob synchronizationJob = buildSynchronizationJob(FULL, null,
      SynchronizationStatus.OPEN, 0, 0, 0, 0);
    return waitFor(synchronizationJobRepository.save(synchronizationJob));
  }

  private String createOpenSynchronizationJobByUser() {
    SynchronizationJob synchronizationJob = buildSynchronizationJob(USER, USER_ID,
      SynchronizationStatus.OPEN, 0, 0, 0, 0);
    return waitFor(synchronizationJobRepository.save(synchronizationJob));
  }

  private Response createNotValidSynchronizationJobByUser() {
    SynchronizationJob synchronizationJob = buildSynchronizationJob(USER, null,
      SynchronizationStatus.OPEN, 0, 0, 0, 0);
    return postSynchronizationJob(synchronizationJob);
  }

  private Response sendGetSynchronizationJob(String id) {
    return okapiClient.get(format("automated-patron-blocks/synchronization/job/%s", id));
  }

  private Response postSynchronizationJob(SynchronizationJob body) {
    return okapiClient.post("automated-patron-blocks/synchronization/job",
      JsonObject.mapFrom(body).encodePrettily());
  }

  private static void stubLoans(Date dueDate, boolean recall, String itemStatus) {
    wireMock.stubFor(get(urlPathMatching("/loan-storage/loans"))
      .atPriority(5)
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(makeLoanResponseBody(dueDate, recall, randomId(), itemStatus))));
  }

  private static void stubLoansWithEmptyResponse() {
    wireMock.stubFor(get(urlPathMatching("/loan-storage/loans"))
      .atPriority(5)
      .willReturn(aResponse()
      .withStatus(200)
      .withBody(makeEmptyResponseBody("loans"))));
  }

  private static void stubAccounts() {
    wireMock.stubFor(get(urlPathMatching("/accounts.*"))
      .atPriority(5)
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(makeAccountsResponseBody())));
  }

  private static void stubFeeFinesWith404() {
    wireMock.stubFor(get(urlPathMatching("/feefines"))
      .atPriority(5)
      .willReturn(notFound().withStatus(404)));
  }

  private static void stubAccountsWithEmptyResponse() {
    wireMock.stubFor(get(urlPathMatching("/accounts.*"))
      .atPriority(5)
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(makeEmptyResponseBody("accounts"))));
  }

  private static String makeLoanResponseBody(Date dueDate, boolean recall,
    String itemId, String itemStatus) {

      JsonObject loan = new JsonObject()
        .put("id", randomId())
        .put("overdueFinePolicyId", randomId())
        .put("dueDate", new DateTime(dueDate).toString(ISODateTimeFormat.dateTime()))
        .put("dueDateChangedByRecall", recall)
        .put("itemId", itemId)
        .put("userId", USER_ID)
        .put("itemStatus", itemStatus);

      return new JsonObject()
          .put("loans", new JsonArray()
            .add(loan))
          .put("totalRecords", 1)
        .encodePrettily();
  }

  private static String makeAccountsResponseBody() {
    JsonObject account = new JsonObject()
      .put("id", ACCOUNT_ID)
      .put("userId", USER_ID)
      .put("loanId", randomId())
      .put("feeFineId", FEE_FINE_TYPE_ID)
      .put("feeFineType", FEE_FINE_TYPE)
      .put("remaining", 1.0)
      .put("metadata", new JsonObject().put("createdDate", now().toDate()));

    return new JsonObject()
      .put("accounts", new JsonArray()
        .add(account))
      .put("totalRecords", 1)
      .encodePrettily();
  }

  private static String makeEmptyResponseBody(String entityName) {
    return new JsonObject()
      .put(entityName, new JsonArray())
      .put("totalRecords", 0)
      .encodePrettily();
  }

  private Response getUserSummary() {
    return okapiClient.get("user-summary/" + USER_ID);
  }
}
