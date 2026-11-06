package org.folio.rest.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.lang.String.format;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.TOKEN;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.folio.exception.EntityNotFoundException;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.core.JsonParseException;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class UsersClientTest extends TestBase {
  private static final String USER_ID = randomId();
  private static final String PATRON_GROUP_ID = randomId();
  private UsersClient usersClient;

  @BeforeEach
  void beforeEach() {
    Map<String, String> okapiHeaders = new HashMap<>();
    okapiHeaders.put(URL, getMockedOkapiUrl());
    okapiHeaders.put(TENANT, TEST_TENANT);
    okapiHeaders.put(TOKEN, OKAPI_TOKEN);

    usersClient = new UsersClient(vertx, okapiHeaders);
  }

  @Test
  void getPatronGroupByExistingUserId(VertxTestContext context) {
    mockUsersResponse(200, new JsonObject()
      .put("id", USER_ID)
      .put("patronGroup", PATRON_GROUP_ID)
      .encodePrettily());

    usersClient.findPatronGroupIdForUser(USER_ID)
      .onFailure(context::failNow)
      .onSuccess(groupId -> {
        assertEquals(PATRON_GROUP_ID, groupId);
        context.completeNow();
      });
  }

  @Test
  void getPatronGroupByNonExistentUserId(VertxTestContext context) {
    String userId = randomId();
    int responseCode = 404;
    String responseBody = "User not found";

    mockUsersResponse(responseCode, responseBody);

    usersClient.findPatronGroupIdForUser(userId)
      .onSuccess(context::failNow)
      .onFailure(throwable -> {
        assertTrue(throwable instanceof EntityNotFoundException);
        assertEquals(format("Failed to fetch %s by ID: %s. Response: %d",
          User.class.getName(), userId, responseCode), throwable.getMessage());
        context.completeNow();
      });
  }

  @Test
  void invalidJsonResponse(VertxTestContext context) {
    mockUsersResponse(200, "not really json");

    usersClient.findPatronGroupIdForUser(randomId())
      .onSuccess(context::failNow)
      .onFailure(throwable -> {
        assertInstanceOf(JsonParseException.class, throwable);
        context.completeNow();
      });
  }

  @Test
  void additionalFieldShouldBeAllowed(VertxTestContext context) {
    mockUsersResponse(200, new JsonObject()
      .put("id", USER_ID)
      .put("patronGroup", PATRON_GROUP_ID)
      .put("additionalNonExistingField", "value")
      .encodePrettily());

    usersClient.findPatronGroupIdForUser(USER_ID)
      .onFailure(context::failNow)
      .onSuccess(groupId -> {
        assertEquals(PATRON_GROUP_ID, groupId);
        context.completeNow();
      });
  }

  @Test
  void findPatronGroupIdsForUsersSuccess() {
    String userId1 = randomId();
    String userId2 = randomId();
    String groupId1 = randomId();
    String groupId2 = randomId();

    JsonObject user1 = new JsonObject().put("id", userId1).put("patronGroup", groupId1);
    JsonObject user2 = new JsonObject().put("id", userId2).put("patronGroup", groupId2);
    JsonObject responseBody = new JsonObject()
      .put("users", new JsonArray().add(user1).add(user2))
      .put("totalRecords", 2);

    wireMock.stubFor(get(urlPathMatching("/users"))
      .willReturn(aResponse().withStatus(200).withBody(responseBody.encodePrettily())));

    Map<String, String> result = waitFor(usersClient.findPatronGroupIdsForUsers(List.of(userId1, userId2)));
    assertEquals(groupId1, result.get(userId1));
    assertEquals(groupId2, result.get(userId2));
  }

  @Test
  void findPatronGroupIdsForUsersMultipleChunks() {
    List<String> userIds = java.util.stream.IntStream.range(0, 85)
      .mapToObj(i -> randomId())
      .toList();

    wireMock.stubFor(get(urlPathMatching("/users"))
      .willReturn(aResponse().withStatus(200).withBody(new JsonObject()
        .put("users", new JsonArray())
        .put("totalRecords", 0).encodePrettily())));

    Map<String, String> result = waitFor(usersClient.findPatronGroupIdsForUsers(userIds));

    assertTrue(result.isEmpty());
    wireMock.verify(2, getRequestedFor(urlPathMatching("/users")));
  }

  @Test
  void findPatronGroupIdsForUsersMissingUserAndMissingGroup() {
    String userIdFound = randomId();
    String userIdNoGroup = randomId();
    String userIdNotFound = randomId();
    String groupId = randomId();

    JsonObject user1 = new JsonObject().put("id", userIdFound).put("patronGroup", groupId);
    JsonObject user2 = new JsonObject().put("id", userIdNoGroup); // no patronGroup
    JsonObject responseBody = new JsonObject()
      .put("users", new JsonArray().add(user1).add(user2))
      .put("totalRecords", 2);

    wireMock.stubFor(get(urlPathMatching("/users"))
      .willReturn(aResponse().withStatus(200).withBody(responseBody.encodePrettily())));

    Map<String, String> result = waitFor(usersClient.findPatronGroupIdsForUsers(
      List.of(userIdFound, userIdNoGroup, userIdNotFound)));

    assertEquals(groupId, result.get(userIdFound));
    assertFalse(result.containsKey(userIdNoGroup));
    assertFalse(result.containsKey(userIdNotFound));
  }

  @Test
  void findPatronGroupIdsForUsersHttpError() {
    String userId1 = randomId();
    String userId2 = randomId();

    wireMock.stubFor(get(urlPathEqualTo("/users"))
      .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

    Future<Map<String, String>> future = usersClient.findPatronGroupIdsForUsers(
      List.of(userId1, userId2));
    waitFor(future);
    assertTrue(future.failed());
  }

  @Test
  void findPatronGroupIdsForUsersEmptyOrNullList() {
    Map<String, String> resultEmpty = waitFor(usersClient.findPatronGroupIdsForUsers(Collections.emptyList()));
    assertTrue(resultEmpty.isEmpty());

    Map<String, String> resultNull = waitFor(usersClient.findPatronGroupIdsForUsers(null));
    assertTrue(resultNull.isEmpty());
  }

  @Test
  void findPatronGroupIdsForUsersInvalidJson() {
    String userId = randomId();

    wireMock.stubFor(get(urlPathEqualTo("/users"))
      .willReturn(aResponse().withStatus(200).withBody("not valid json")));

    Future<Map<String, String>> future = usersClient.findPatronGroupIdsForUsers(List.of(userId));
    waitFor(future);
    assertTrue(future.failed());
  }

  private void mockUsersResponse(int responseStatus, String responseBody) {
    wireMock.stubFor(get(urlPathMatching("/users/.+"))
      .willReturn(aResponse()
        .withStatus(responseStatus)
        .withBody(responseBody)
      ));
  }
}
