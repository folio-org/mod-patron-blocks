package org.folio.rest.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.lang.String.format;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.TOKEN;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.folio.exception.EntityNotFoundException;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.core.JsonParseException;

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
    okapiHeaders.put(TENANT, OKAPI_TENANT);
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

  private void mockUsersResponse(int responseStatus, String responseBody) {
    wireMock.stubFor(get(urlPathMatching("/users/.+"))
      .willReturn(aResponse()
        .withStatus(responseStatus)
        .withBody(responseBody)
      ));
  }
}
