package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;
import static org.folio.repository.PatronBlockLimitsRepository.PATRON_BLOCK_LIMITS_TABLE_NAME;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.joda.time.DateTime.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.folio.domain.Condition;
import org.folio.repository.PatronBlockConditionsRepository;
import org.folio.repository.PatronBlockLimitsRepository;
import org.folio.repository.UserSummaryRepository;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.AutomatedPatronBlock;
import org.folio.rest.jaxrs.model.AutomatedPatronBlocks;
import org.folio.rest.jaxrs.model.AutomatedPatronBlocksBatchRequest;
import org.folio.rest.jaxrs.model.AutomatedPatronBlocksBatchResponse;
import org.folio.rest.jaxrs.model.AutomatedPatronBlocksBatchResult;
import org.folio.rest.jaxrs.model.OpenFeeFine;
import org.folio.rest.jaxrs.model.OpenLoan;
import org.folio.rest.jaxrs.model.PatronBlockCondition;
import org.folio.rest.jaxrs.model.PatronBlockLimit;
import org.folio.rest.jaxrs.model.UserSummary;
import org.folio.rest.utils.EntityBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
public class AutomatedPatronBlocksBatchAPITest extends TestBase {

  private static final String BATCH_PATRON_BLOCKS_URL = "automated-patron-blocks/batch";

  private final PatronBlockLimitsRepository limitsRepository =
    new PatronBlockLimitsRepository(postgresClient);
  private final UserSummaryRepository summaryRepository =
    new UserSummaryRepository(postgresClient);
  private final PatronBlockConditionsRepository conditionsRepository =
    new PatronBlockConditionsRepository(postgresClient);

  @BeforeEach
  void beforeEach() {
    super.resetMocks();
    deleteAllFromTable(PATRON_BLOCK_LIMITS_TABLE_NAME);
    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
  }

  @Test
  void shouldReturn200ForValidSinglePatronRequestWithNoSummary() {
    String userId = randomId();
    AutomatedPatronBlocksBatchRequest request = new AutomatedPatronBlocksBatchRequest()
      .withUserIds(singletonList(userId));

    getBatchPatronBlocks(request)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body("totalRecords", equalTo(1))
      .body("results", hasSize(1))
      .body("results[0].userId", equalTo(userId))
      .body("results[0].status", equalTo("SUCCESS"))
      .body("results[0].automatedPatronBlocks", empty());
  }

  @Test
  void shouldDeduplicateUserIdsInRequest() {
    String userId = randomId();
    AutomatedPatronBlocksBatchRequest request = new AutomatedPatronBlocksBatchRequest()
      .withUserIds(List.of(userId, userId));

    getBatchPatronBlocks(request)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body("totalRecords", equalTo(1))
      .body("results", hasSize(1))
      .body("results[0].userId", equalTo(userId))
      .body("results[0].status", equalTo("SUCCESS"))
      .body("results[0].automatedPatronBlocks", empty());
  }

  @Test
  void shouldReturn200For1000Patrons() {
    List<String> userIds = IntStream.range(0, 1000)
      .mapToObj(i -> randomId())
      .toList();

    AutomatedPatronBlocksBatchRequest request = new AutomatedPatronBlocksBatchRequest()
      .withUserIds(userIds);

    getBatchPatronBlocks(request)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body("totalRecords", equalTo(1000))
      .body("results", hasSize(1000));
  }

  @Test
  void shouldRejectRequestWithEmptyUserIds() {
    AutomatedPatronBlocksBatchRequest request = new AutomatedPatronBlocksBatchRequest()
      .withUserIds(new ArrayList<>());

    getBatchPatronBlocks(request)
      .then()
      .statusCode(422)
      .contentType(ContentType.JSON)
      .body("errors[0].message", containsString("size must be between 1 and 1000"));
  }

  @Test
  void shouldRejectRequestWithMissingUserIds() {
    getBatchPatronBlocks(new JsonObject())
      .then()
      .statusCode(422)
      .contentType(ContentType.JSON)
      .body("errors[0].message", anyOf(containsString("must not be null"),
        containsString("size must be between 1 and 1000")));
  }

  @Test
  void shouldRejectRequestWithInvalidUuid() {
    AutomatedPatronBlocksBatchRequest request = new AutomatedPatronBlocksBatchRequest()
      .withUserIds(List.of("not-a-uuid"));

    getBatchPatronBlocks(request)
      .then()
      .statusCode(422)
      .contentType(ContentType.JSON)
      .body("errors[0].message", containsString("User IDs must match UUID format"));
  }

  @Test
  void shouldRejectRequestWithMoreThan1000UserIds() {
    List<String> userIds = IntStream.range(0, 1001)
      .mapToObj(i -> randomId())
      .toList();

    AutomatedPatronBlocksBatchRequest request = new AutomatedPatronBlocksBatchRequest()
      .withUserIds(userIds);

    getBatchPatronBlocks(request)
      .then()
      .statusCode(422)
      .contentType(ContentType.JSON)
      .body("errors[0].message", containsString("size must be between 1 and 1000"));
  }

  @Test
  void shouldReturnBlocksForMultiplePatronsWithSinglePatronParity() {
    Condition condition = Condition.MAX_NUMBER_OF_ITEMS_CHARGED_OUT;
    String conditionMessage = "Maximum number of items charged out limit reached";
    updateCondition(condition, true, true, true, conditionMessage);

    String patronGroupId = randomId();
    createLimit(condition, patronGroupId, 1.0);

    String userId1 = randomId();
    String userId2 = randomId();
    String userId3 = randomId();

    OpenLoan loan1 = EntityBuilder.buildLoan(false, false, now().plusHours(1).toDate());
    OpenLoan loan2 = EntityBuilder.buildLoan(false, false, now().plusHours(2).toDate());
    createSummary(userId1, Collections.emptyList(), List.of(loan1, loan2));
    createSummary(userId2, Collections.emptyList(), Collections.emptyList());

    mockSingleUserResponse(userId1, patronGroupId);
    mockSingleUserResponse(userId2, patronGroupId);
    mockBatchUsersResponse(Map.of(userId1, patronGroupId, userId2, patronGroupId));

    Response singleResp1 = sendSinglePatronRequest(userId1);
    assertEquals(200, singleResp1.statusCode());
    AutomatedPatronBlocks singleBlocks1 = new JsonObject(singleResp1.asString())
      .mapTo(AutomatedPatronBlocks.class);
    assertEquals(1, singleBlocks1.getAutomatedPatronBlocks().size());

    Response singleResp2 = sendSinglePatronRequest(userId2);
    assertEquals(200, singleResp2.statusCode());
    AutomatedPatronBlocks singleBlocks2 = new JsonObject(singleResp2.asString())
      .mapTo(AutomatedPatronBlocks.class);
    assertTrue(singleBlocks2.getAutomatedPatronBlocks().isEmpty());

    Response singleResp3 = sendSinglePatronRequest(userId3);
    assertEquals(200, singleResp3.statusCode());
    AutomatedPatronBlocks singleBlocks3 = new JsonObject(singleResp3.asString())
      .mapTo(AutomatedPatronBlocks.class);
    assertTrue(singleBlocks3.getAutomatedPatronBlocks().isEmpty());

    AutomatedPatronBlocksBatchRequest batchRequest = new AutomatedPatronBlocksBatchRequest()
      .withUserIds(List.of(userId1, userId2, userId3));
    Response batchResp = getBatchPatronBlocks(batchRequest);
    assertEquals(200, batchResp.statusCode());

    AutomatedPatronBlocksBatchResponse batchResponse = new JsonObject(batchResp.asString())
      .mapTo(AutomatedPatronBlocksBatchResponse.class);
    assertEquals(3, batchResponse.getTotalRecords());
    assertEquals(3, batchResponse.getResults().size());

    AutomatedPatronBlocksBatchResult result1 = batchResponse.getResults().get(0);
    assertEquals(userId1, result1.getUserId());
    assertEquals(AutomatedPatronBlocksBatchResult.Status.SUCCESS, result1.getStatus());
    assertEquals(1, result1.getAutomatedPatronBlocks().size());

    AutomatedPatronBlock singleBlock = singleBlocks1.getAutomatedPatronBlocks().get(0);
    AutomatedPatronBlock batchBlock = result1.getAutomatedPatronBlocks().get(0);
    assertEquals(singleBlock.getPatronBlockConditionId(), batchBlock.getPatronBlockConditionId());
    assertEquals(singleBlock.getBlockBorrowing(), batchBlock.getBlockBorrowing());
    assertEquals(singleBlock.getBlockRenewals(), batchBlock.getBlockRenewals());
    assertEquals(singleBlock.getBlockRequests(), batchBlock.getBlockRequests());
    assertEquals(singleBlock.getMessage(), batchBlock.getMessage());

    AutomatedPatronBlocksBatchResult result2 = batchResponse.getResults().get(1);
    assertEquals(userId2, result2.getUserId());
    assertEquals(AutomatedPatronBlocksBatchResult.Status.SUCCESS, result2.getStatus());
    assertTrue(result2.getAutomatedPatronBlocks().isEmpty());

    AutomatedPatronBlocksBatchResult result3 = batchResponse.getResults().get(2);
    assertEquals(userId3, result3.getUserId());
    assertEquals(AutomatedPatronBlocksBatchResult.Status.SUCCESS, result3.getStatus());
    assertTrue(result3.getAutomatedPatronBlocks().isEmpty());
  }

  @Test
  void shouldHandleIsolatedPatronFailureWhenUserNotFoundInModUsers() {
    String userId1 = randomId();
    String userId2 = randomId();
    String patronGroupId = randomId();

    createSummary(userId1, Collections.emptyList(), Collections.emptyList());
    createSummary(userId2, Collections.emptyList(), Collections.emptyList());

    JsonObject user1 = new JsonObject().put("id", userId1).put("patronGroup", patronGroupId);
    JsonObject responseBody = new JsonObject()
      .put("users", new JsonArray().add(user1))
      .put("totalRecords", 1);

    wireMock.stubFor(get(urlPathEqualTo("/users"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(responseBody.encodePrettily())));

    AutomatedPatronBlocksBatchRequest request = new AutomatedPatronBlocksBatchRequest()
      .withUserIds(List.of(userId1, userId2));

    getBatchPatronBlocks(request)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body("totalRecords", equalTo(2))
      .body("results", hasSize(2))
      .body("results[0].userId", equalTo(userId1))
      .body("results[0].status", equalTo("SUCCESS"))
      .body("results[0].automatedPatronBlocks", empty())
      .body("results[1].userId", equalTo(userId2))
      .body("results[1].status", equalTo("ERROR"))
      .body("results[1].error.code", equalTo("PATRON_GROUP_NOT_FOUND"))
      .body("results[1].error.message", equalTo("Failed to find patron group for user " + userId2));
  }

  @Test
  void shouldBoundOutboundCallsToModUsers() {
    List<String> userIds = IntStream.range(0, 5)
      .mapToObj(i -> randomId())
      .toList();

    userIds.forEach(id -> createSummary(id, Collections.emptyList(), Collections.emptyList()));

    String patronGroupId = randomId();
    JsonArray usersArray = new JsonArray();
    userIds.forEach(id -> usersArray.add(new JsonObject()
      .put("id", id)
      .put("patronGroup", patronGroupId)));
    JsonObject responseBody = new JsonObject()
      .put("users", usersArray)
      .put("totalRecords", 5);

    wireMock.stubFor(get(urlPathEqualTo("/users"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(responseBody.encodePrettily())));

    AutomatedPatronBlocksBatchRequest request = new AutomatedPatronBlocksBatchRequest()
      .withUserIds(userIds);

    Response response = getBatchPatronBlocks(request);
    assertEquals(200, response.statusCode());

    AutomatedPatronBlocksBatchResponse batchResponse = new JsonObject(response.asString())
      .mapTo(AutomatedPatronBlocksBatchResponse.class);
    assertEquals(5, batchResponse.getTotalRecords());
    assertEquals(5, batchResponse.getResults().size());
    batchResponse.getResults().forEach(result ->
      assertEquals(AutomatedPatronBlocksBatchResult.Status.SUCCESS, result.getStatus()));

    wireMock.verify(1, getRequestedFor(urlPathEqualTo("/users")));
  }

  private Response sendSinglePatronRequest(String userId) {
    return okapiClient.get("automated-patron-blocks/" + userId);
  }

  private void mockSingleUserResponse(String userId, String patronGroupId) {
    String mockResponse = new JsonObject()
      .put("id", userId)
      .put("patronGroup", patronGroupId)
      .encodePrettily();

    wireMock.stubFor(get(urlPathEqualTo("/users/" + userId))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(mockResponse)));
  }

  private void mockBatchUsersResponse(Map<String, String> userGroupMap) {
    JsonArray usersArray = new JsonArray();
    userGroupMap.forEach((userId, groupId) -> {
      JsonObject userJson = new JsonObject().put("id", userId);
      if (groupId != null) {
        userJson.put("patronGroup", groupId);
      }
      usersArray.add(userJson);
    });

    JsonObject responseBody = new JsonObject()
      .put("users", usersArray)
      .put("totalRecords", usersArray.size());

    wireMock.stubFor(get(urlPathEqualTo("/users"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(responseBody.encodePrettily())));
  }

  private void updateCondition(Condition condition, boolean blockBorrowing, boolean blockRenewals,
    boolean blockRequests, String message) {

    PatronBlockCondition patronBlockCondition =
      waitFor(conditionsRepository.get(condition.getId()))
        .orElseThrow(() -> new IllegalArgumentException("Invalid condition ID"))
        .withBlockBorrowing(blockBorrowing)
        .withBlockRenewals(blockRenewals)
        .withBlockRequests(blockRequests)
        .withMessage(message);

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

  private Response getBatchPatronBlocks(AutomatedPatronBlocksBatchRequest request) {
    return getBatchPatronBlocks(JsonObject.mapFrom(request));
  }

  private Response getBatchPatronBlocks(JsonObject requestBody) {
    return okapiClient.post(BATCH_PATRON_BLOCKS_URL, requestBody.encode());
  }

}
