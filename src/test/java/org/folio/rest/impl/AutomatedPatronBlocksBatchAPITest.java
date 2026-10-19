package org.folio.rest.impl;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.AutomatedPatronBlocksBatchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
public class AutomatedPatronBlocksBatchAPITest extends TestBase {

  private static final String BATCH_URL = "automated-patron-blocks/batch";

  @Test
  void shouldReturn501ForValidSinglePatronRequest() {
    String userId = UUID.randomUUID().toString();
    AutomatedPatronBlocksBatchRequest request = new AutomatedPatronBlocksBatchRequest()
      .withUserIds(Collections.singletonList(userId));

    postBatch(request)
      .then()
      .statusCode(501)
      .contentType(ContentType.TEXT)
      .body(equalTo("Not implemented"));
  }

  @Test
  void shouldReturn501ForValidRequestWithDuplicates() {
    String userId = UUID.randomUUID().toString();
    AutomatedPatronBlocksBatchRequest request = new AutomatedPatronBlocksBatchRequest()
      .withUserIds(List.of(userId, userId, UUID.randomUUID().toString()));

    postBatch(request)
      .then()
      .statusCode(501)
      .contentType(ContentType.TEXT)
      .body(equalTo("Not implemented"));
  }

  @Test
  void shouldReturn501ForValidRequestWith1000Patrons() {
    List<String> userIds = IntStream.range(0, 1000)
      .mapToObj(i -> UUID.randomUUID().toString())
      .collect(Collectors.toList());

    AutomatedPatronBlocksBatchRequest request = new AutomatedPatronBlocksBatchRequest()
      .withUserIds(userIds);

    postBatch(request)
      .then()
      .statusCode(501)
      .contentType(ContentType.TEXT)
      .body(equalTo("Not implemented"));
  }

  @Test
  void shouldRejectRequestWithEmptyUserIds() {
    JsonObject request = new JsonObject().put("userIds", new JsonArray());

    postBatch(request)
      .then()
      .statusCode(422)
      .contentType(ContentType.JSON)
      .body("errors[0].message", containsString("size must be between 1 and 1000"));
  }

  @Test
  void shouldRejectRequestWithMissingUserIds() {
    JsonObject request = new JsonObject();

    postBatch(request)
      .then()
      .statusCode(422)
      .contentType(ContentType.JSON)
      .body("errors[0].message", anyOf(containsString("must not be null"),
        containsString("size must be between 1 and 1000")));
  }

  @Test
  void shouldRejectRequestWithInvalidUuid() {
    JsonObject request = new JsonObject().put("userIds", new JsonArray().add("not-a-valid-uuid"));

    postBatch(request)
      .then()
      .statusCode(422)
      .contentType(ContentType.JSON)
      .body("errors[0].message", containsString("User IDs must match UUID format"));
  }

  @Test
  void shouldRejectRequestWithMoreThan1000UserIds() {
    List<String> userIds = IntStream.range(0, 1001)
      .mapToObj(i -> UUID.randomUUID().toString())
      .collect(Collectors.toList());

    JsonObject request = new JsonObject().put("userIds", new JsonArray(userIds));

    postBatch(request)
      .then()
      .statusCode(422)
      .contentType(ContentType.JSON)
      .body("errors[0].message", containsString("size must be between 1 and 1000"));
  }


  private Response postBatch(Object body) {
    String payload;
    if (body instanceof String) {
      payload = (String) body;
    } else if (body instanceof JsonObject) {
      payload = ((JsonObject) body).encode();
    } else {
      payload = JsonObject.mapFrom(body).encode();
    }
    return okapiClient.post(BATCH_URL, payload);
  }
}
