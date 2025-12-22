package org.folio.rest.impl;

import static java.lang.String.format;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.hamcrest.core.IsEqual.equalTo;

import org.folio.repository.UserSummaryRepository;
import org.folio.rest.TestBase;
import org.folio.rest.handlers.EventHandler;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

public class UserSummaryAPITest extends TestBase {
  private UserSummaryRepository userSummaryRepository;
  private EventHandler<ItemCheckedOutEvent> itemCheckedOutEventHandler;

  @BeforeEach
  void beforeEach() {
    super.resetMocks();

    userSummaryRepository = new UserSummaryRepository(postgresClient);
    itemCheckedOutEventHandler = new EventHandler<>(postgresClient);

    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
    deleteAllFromTable(ITEM_CHECKED_OUT_EVENT_TABLE_NAME);
  }

  @Test
  void shouldReturn400WhenCalledWithInvalidUserId() {
    sendRequest("invalid")
      .then()
      .statusCode(400)
      .contentType(ContentType.TEXT)
      .body(equalTo("Invalid user ID: \"invalid\""));
  }

  @Test
  void shouldReturn404WhenUserSummaryDoesNotExist() {
    String userId = randomId();

    sendRequest(userId)
      .then()
      .statusCode(404)
      .contentType(ContentType.TEXT)
      .body(equalTo(format("User summary for user ID %s not found", userId)));
  }

  @Test
  void shouldReturn200WhenUserSummaryExistsAndIsValid() {
    String userId = randomId();

    waitFor(itemCheckedOutEventHandler.handle(
      buildItemCheckedOutEvent(userId, randomId(), null)));

    UserSummary userSummary = waitFor(userSummaryRepository.getByUserId(userId)).get();

    sendRequest(userId)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body(equalTo(toJson(userSummary)));
  }

  private Response sendRequest(String userId) {
    return okapiClient.get("user-summary/" + userId);
  }
}
