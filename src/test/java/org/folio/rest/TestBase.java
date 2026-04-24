package org.folio.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.entity.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Awaitility;
import org.folio.HttpStatus;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.utils.EventClient;
import org.folio.rest.utils.OkapiClient;
import org.folio.rest.utils.PomUtils;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class TestBase {
  protected static final Logger log = LogManager.getLogger(TestBase.class);

  protected static final String MODULE_NAME = "mod_patron_blocks";
  protected static final int OKAPI_PORT = NetworkUtils.nextFreePort();
  protected static final String OKAPI_URL = "http://localhost:" + OKAPI_PORT;
  protected static final String OKAPI_TENANT = "test_tenant";
  protected static final String OKAPI_TOKEN = generateOkapiToken();
  private static final Header JSON_CONTENT_TYPE_HEADER =
    new Header("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
  private static final int GET_TENANT_TIMEOUT_MS = 10000;

  protected static final String USER_SUMMARY_TABLE_NAME = "user_summary";
  protected static final String USER_SUMMARY_LOCK_TABLE_NAME = "user_summary_lock";
  protected static final String SYNCHRONIZATION_JOBS_TABLE_NAME = "synchronization_jobs";
  protected static final String ITEM_CHECKED_OUT_EVENT_TABLE_NAME = "item_checked_out_event";
  protected static final String ITEM_CHECKED_IN_EVENT_TABLE_NAME = "item_checked_in_event";
  protected static final String ITEM_DECLARED_LOST_EVENT_TABLE_NAME = "item_declared_lost_event";
  protected static final String ITEM_CLAIMED_RETURNED_EVENT_TABLE_NAME = "item_claimed_returned_event";
  protected static final String ITEM_AGED_TO_LOST_EVENT_TABLE_NAME = "item_aged_to_lost_event";
  protected static final String LOAN_DUE_DATE_CHANGED_EVENT_TABLE_NAME = "loan_due_date_changed_event";
  protected static final String FEE_FINE_BALANCE_CHANGED_EVENT_TABLE_NAME = "fee_fine_balance_changed_event";

  protected static Vertx vertx;
  protected static OkapiClient okapiClient;
  protected static TenantClient tenantClient;
  protected static PostgresClient postgresClient;
  protected static EventClient eventClient;

  protected static String jobId;

  @RegisterExtension
  protected static WireMockExtension wireMock = WireMockExtension.newInstance()
    .options(new WireMockConfiguration().dynamicPort().dynamicHttpsPort())
    .build();

  @BeforeAll
  static void beforeAll(final VertxTestContext context) {
    vertx = Vertx.vertx();
    okapiClient = new OkapiClient(getMockedOkapiUrl(), OKAPI_TENANT, OKAPI_TOKEN);
    tenantClient = new TenantClient(getMockedOkapiUrl(), OKAPI_TENANT, OKAPI_TOKEN);

    var postgresContainer = new PostgresTesterContainer();
    postgresContainer.start("database", "username", "password");
    PostgresClient.setPostgresTester(postgresContainer);
    waitForPostgres();

    eventClient = new EventClient(okapiClient);

    mockEndpoints();

    DeploymentOptions deploymentOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", OKAPI_PORT));

    vertx.deployVerticle(RestVerticle.class.getName(), deploymentOptions)
      .onSuccess(deployment -> {
        try {
          tenantClient.postTenant(getTenantAttributes(), postResult -> {
            if (postResult.failed()) {
              log.error(postResult.cause());
              return;
            }

            HttpResponse<Buffer> postResponse = postResult.result();
            //assertEquals(HttpStatus.HTTP_CREATED.toInt(), postResponse.statusCode());
            if (postResponse.statusCode() != HttpStatus.HTTP_CREATED.toInt()) {
              context.failNow("Tenant API failed with status: " + postResponse.statusCode());
              return;
            }

            jobId = postResponse.bodyAsJson(TenantJob.class).getId();

            postgresClient = PostgresClient.getInstance(vertx, OKAPI_TENANT);

            tenantClient.getTenantByOperationId(jobId, GET_TENANT_TIMEOUT_MS, getResult -> {
              if (getResult.failed()) {
                log.error(getResult.cause());
                return;
              }

              HttpResponse<Buffer> getResponse = getResult.result();
              assertEquals(HttpStatus.HTTP_OK.toInt(), getResponse.statusCode());
              assertTrue(getResponse.bodyAsJson(TenantJob.class).getComplete());

              context.completeNow();
            });

          });
        } catch (Exception e) {
          context.failNow(e);
        }
      });
  }

  @AfterAll
  static void afterAll(VertxTestContext context) {
    deleteTenant(tenantClient);
    vertx.close().onComplete(res -> {
      PostgresClient.stopPostgresTester();
      context.completeNow();
    });
  }

  static void deleteTenant(TenantClient tenantClient) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    tenantClient.deleteTenantByOperationId(jobId, deleted -> {
      if (deleted.failed()) {
        future.completeExceptionally(new RuntimeException("Failed to delete tenant"));
        return;
      }
      future.complete(null);
    });
  }

  @BeforeEach
  protected void resetMocks() {
    mockEndpoints();
  }

  private static void mockEndpoints() {
    wireMock.resetAll();

    wireMock.stubFor(post(urlEqualTo("/pubsub/event-types"))
      .atPriority(100)
      .willReturn(created()));

    wireMock.stubFor(post(urlEqualTo("/pubsub/event-types?"))
      .atPriority(100)
      .willReturn(created()));

    wireMock.stubFor(post(urlMatching("/pubsub/event-types/declare/(publisher|subscriber)"))
      .atPriority(100)
      .willReturn(created()));

    // forward everything to Okapi
    wireMock.stubFor(any(anyUrl())
      .atPriority(Integer.MAX_VALUE)
      .willReturn(aResponse().proxiedFrom(OKAPI_URL)));
  }

  protected static TenantAttributes getTenantAttributes() {
    Parameter loadReferenceParameter = new Parameter()
      .withKey("loadReference").withValue("true");

    return new TenantAttributes()
      .withModuleFrom(format("%s-0.0.1", MODULE_NAME))
      .withModuleTo(format("%s-%s", MODULE_NAME, PomUtils.getModuleVersion()))
      .withParameters(Collections.singletonList(loadReferenceParameter));
  }

  protected void deleteAllFromTable(String tableName) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    postgresClient.delete(tableName, new Criterion(), result -> future.complete(null));
    try {
      future.get(5, TimeUnit.SECONDS);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private static String generateOkapiToken() {
    String payload = new JsonObject()
      .put("user_id", randomId())
      .put("tenant", OKAPI_TENANT)
      .put("sub", "admin")
      .toString();

    return format("1.%s.3", Base64.getEncoder()
      .encodeToString(payload.getBytes()));
  }

  protected static String getMockedOkapiUrl() {
    return "http://localhost:" + wireMock.getPort();
  }

  protected static String randomId() {
    return UUID.randomUUID().toString();
  }

  protected static <T> T waitFor(Future<T> future) {
    return waitFor(future, 3);
  }

  protected static <T> T waitFor(Future<T> future, int waitForSeconds) {
    Awaitility.await()
      .atMost(waitForSeconds, TimeUnit.SECONDS)
      .until(future::isComplete);

    return future.result();
  }

  private static void waitForPostgres() {
    PostgresClient pgClient = PostgresClient.getInstance(vertx);
    String query = "SELECT 1";
    AtomicBoolean isReady = new AtomicBoolean();
    await()
      .atMost(120, TimeUnit.SECONDS)
      .pollInterval(3, TimeUnit.SECONDS)
      .alias("Is Postgres Up?")
      .until(() -> {
        System.out.println("checking to see if postgres is up");

        vertx.runOnContext((at) -> pgClient.select(query)
          .onSuccess(ar -> isReady.set(true)));

        return isReady.get();
      });

    if (!isReady.get()) {
      throw new RuntimeException("Could not connect to postgres");
    }
  }

  protected static <T> void awaitUntil(Callable<T> supplier, Matcher<? super T> matcher) {
    Awaitility.await().atMost(5, SECONDS).until(supplier, matcher);
  }

  protected static String toJson(Object event) {
    return JsonObject.mapFrom(event).encodePrettily();
  }

  protected RequestSpecification getRequestSpecification() {
    return (new RequestSpecBuilder())
      .addHeader("X-Okapi-Tenant", OKAPI_TENANT)
      .addHeader("X-Okapi-Token", OKAPI_TOKEN)
      .addHeader("X-Okapi-Url", OKAPI_URL)
      .setBaseUri(OKAPI_URL)
      .setPort(OKAPI_PORT)
      .log(LogDetail.ALL)
      .build();
  }

  protected ExtractableResponse<Response> getWithStatus(String resourcePath, int expectedStatus) {
    return RestAssured.given()
      .spec(getRequestSpecification())
      .when()
      .get(resourcePath, new Object[0])
      .then()
      .log()
      .ifValidationFails()
      .statusCode(expectedStatus)
      .extract();
  }

  protected ExtractableResponse<Response> putWithStatus(String resourcePath, String putBody, int expectedStatus, Header... headers) {
    return RestAssured.given()
      .spec(getRequestSpecification())
      .header(JSON_CONTENT_TYPE_HEADER)
      .headers(new Headers(headers))
      .body(putBody)
      .when()
      .put(resourcePath, new Object[0])
      .then()
      .log()
      .ifValidationFails()
      .statusCode(expectedStatus)
      .extract();
  }

  protected ExtractableResponse<Response> postWithStatus(String resourcePath, String postBody, int expectedStatus, Header... headers) {
    return RestAssured.given()
      .spec(getRequestSpecification())
      .header(JSON_CONTENT_TYPE_HEADER)
      .headers(new Headers(headers))
      .body(postBody)
      .when()
      .post(resourcePath, new Object[0])
      .then()
      .log()
      .ifValidationFails()
      .statusCode(expectedStatus)
      .extract();
  }

  protected ExtractableResponse<Response> deleteWithStatus(String resourcePath, int expectedStatus) {
    return RestAssured.given()
      .spec(getRequestSpecification())
      .when()
      .delete(resourcePath, new Object[0]).then()
      .log()
      .ifValidationFails()
      .statusCode(expectedStatus)
      .extract();
  }
}
