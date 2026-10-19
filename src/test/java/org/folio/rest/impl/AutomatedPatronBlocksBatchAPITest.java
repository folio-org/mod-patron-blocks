package org.folio.rest.impl;

import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.AutomatedPatronBlocksBatchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
public class AutomatedPatronBlocksBatchAPITest extends TestBase {

  private static final String BATCH_PATRON_BLOCKS_URL = "automated-patron-blocks/batch";

  @Test
  void shouldReturn501ForValidSinglePatronRequest() {
    AutomatedPatronBlocksBatchRequest request = new AutomatedPatronBlocksBatchRequest()
      .withUserIds(singletonList(randomId()));

    getBatchPatronBlocks(request)
      .then()
      .statusCode(501)
      .contentType(ContentType.TEXT)
      .body(equalTo("Not implemented"));
  }

  @Test
  void shouldReturn501ForValidRequestWithDuplicates() {
    String userId = randomId();
    AutomatedPatronBlocksBatchRequest request = new AutomatedPatronBlocksBatchRequest()
      .withUserIds(List.of(userId, userId, randomId()));

    getBatchPatronBlocks(request)
      .then()
      .statusCode(501)
      .contentType(ContentType.TEXT)
      .body(equalTo("Not implemented"));
  }

  @Test
  void shouldReturn501ForValidRequestWith1000Patrons() {
    List<String> userIds = IntStream.range(0, 1000)
      .mapToObj(i -> randomId())
      .toList();

    AutomatedPatronBlocksBatchRequest request = new AutomatedPatronBlocksBatchRequest()
      .withUserIds(userIds);

    getBatchPatronBlocks(request)
      .then()
      .statusCode(501)
      .contentType(ContentType.TEXT)
      .body(equalTo("Not implemented"));
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

  private Response getBatchPatronBlocks(AutomatedPatronBlocksBatchRequest request) {
    return getBatchPatronBlocks(JsonObject.mapFrom(request));
  }

  private Response getBatchPatronBlocks(JsonObject requestBody) {
    return okapiClient.post(BATCH_PATRON_BLOCKS_URL, requestBody.encode());
  }

}
