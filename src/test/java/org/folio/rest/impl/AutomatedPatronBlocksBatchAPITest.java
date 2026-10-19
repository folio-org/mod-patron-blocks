package org.folio.rest.impl;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.AutomatedPatronBlock;
import org.folio.rest.jaxrs.model.AutomatedPatronBlocks;
import org.folio.rest.jaxrs.model.AutomatedPatronBlocksBatchRequest;
import org.folio.rest.jaxrs.model.AutomatedPatronBlocksBatchResponse;
import org.folio.rest.jaxrs.model.AutomatedPatronBlocksBatchResult;
import org.folio.rest.jaxrs.model.Error;
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
      .body("errors[0].message", anyOf(containsString("must not be null"), containsString("size must be between 1 and 1000")));
  }

  @Test
  void shouldRejectRequestWithInvalidUuid() {
    JsonObject request = new JsonObject().put("userIds", new JsonArray().add("not-a-valid-uuid"));

    postBatch(request)
      .then()
      .statusCode(422)
      .contentType(ContentType.JSON)
      .body("errors[0].message", containsString("must match"));
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

  @Test
  void shouldValidateSampleResponseAndModels() throws Exception {
    String samplePath = "/ramls/examples/automated-patron-blocks-batch-response.sample";
    try (InputStream is = getClass().getResourceAsStream(samplePath)) {
      if (is != null) {
        String sampleJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        JsonObject json = new JsonObject(sampleJson);
        assertTrue(json.containsKey("results"));
        assertTrue(json.containsKey("totalRecords"));
        JsonArray results = json.getJsonArray("results");
        assertFalse(results.isEmpty());
      }
    }

    AutomatedPatronBlocksBatchResult successResultWithBlocks = new AutomatedPatronBlocksBatchResult()
      .withUserId(UUID.randomUUID().toString())
      .withStatus(AutomatedPatronBlocksBatchResult.Status.SUCCESS)
      .withAutomatedPatronBlocks(List.of(new AutomatedPatronBlock()
        .withPatronBlockConditionId(UUID.randomUUID().toString())
        .withBlockBorrowing(true)
        .withBlockRenewals(false)
        .withBlockRequests(false)
        .withMessage("Block message")));

    AutomatedPatronBlocksBatchResult successResultNoBlocks = new AutomatedPatronBlocksBatchResult()
      .withUserId(UUID.randomUUID().toString())
      .withStatus(AutomatedPatronBlocksBatchResult.Status.SUCCESS)
      .withAutomatedPatronBlocks(Collections.emptyList());

    AutomatedPatronBlocksBatchResult errorResult = new AutomatedPatronBlocksBatchResult()
      .withUserId(UUID.randomUUID().toString())
      .withStatus(AutomatedPatronBlocksBatchResult.Status.ERROR)
      .withError(new Error().withMessage("User not found").withCode("NOT_FOUND"));

    AutomatedPatronBlocksBatchResponse response = new AutomatedPatronBlocksBatchResponse()
      .withResults(List.of(successResultWithBlocks, successResultNoBlocks, errorResult))
      .withTotalRecords(3);

    JsonObject responseJson = JsonObject.mapFrom(response);
    assertTrue(responseJson.containsKey("results"));
    assertEquals(3, responseJson.getInteger("totalRecords"));
  }

  @Test
  void shouldPreserveExistingSinglePatronEndpoint() {
    String userId = UUID.randomUUID().toString();
    String emptyBlocksResponse = toJson(new AutomatedPatronBlocks());

    okapiClient.get("automated-patron-blocks/" + userId)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body(equalTo(emptyBlocksResponse));
  }

  @Test
  void shouldVerifyModuleDescriptorConfiguration() throws Exception {
    File mdFile = new File("target/ModuleDescriptor.json");
    if (!mdFile.exists()) {
      mdFile = new File("descriptors/ModuleDescriptor-template.json");
    }
    assertTrue(mdFile.exists(), "ModuleDescriptor file should exist");

    String mdContent = Files.readString(mdFile.toPath(), StandardCharsets.UTF_8);
    JsonObject mdJson = new JsonObject(mdContent);

    JsonArray provides = mdJson.getJsonArray("provides");
    assertNotNull(provides);
    JsonObject automatedPatronBlocksInterface = provides.stream()
      .map(JsonObject.class::cast)
      .filter(i -> "automated-patron-blocks".equals(i.getString("id")))
      .findFirst()
      .orElseThrow(() -> new AssertionError("automated-patron-blocks interface not found"));

    assertEquals("0.2", automatedPatronBlocksInterface.getString("version"));

    JsonArray handlers = automatedPatronBlocksInterface.getJsonArray("handlers");
    assertNotNull(handlers);
    boolean hasBatchRoute = handlers.stream()
      .map(JsonObject.class::cast)
      .anyMatch(h -> "/automated-patron-blocks/batch".equals(h.getString("pathPattern"))
        && h.getJsonArray("methods").contains("POST")
        && h.getJsonArray("permissionsRequired").contains("patron-blocks.automated-patron-blocks.batch.post"));
    assertTrue(hasBatchRoute, "Batch route handler should be present with correct permissions");

    JsonArray permissionSets = mdJson.getJsonArray("permissionSets");
    assertNotNull(permissionSets);
    boolean hasPermission = permissionSets.stream()
      .map(JsonObject.class::cast)
      .anyMatch(p -> "patron-blocks.automated-patron-blocks.batch.post".equals(p.getString("permissionName")));
    assertTrue(hasPermission, "Permission patron-blocks.automated-patron-blocks.batch.post should be declared");
  }
}
