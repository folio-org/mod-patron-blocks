package org.folio.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.FileReader;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.entity.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.awaitility.Awaitility;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.folio.HttpStatus;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.utils.OkapiClient;
import org.folio.rest.utils.PomUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

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
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.client.HttpResponse;

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

  protected static Vertx vertx;
  protected static OkapiClient okapiClient;
  protected static TenantClient tenantClient;
  protected static PostgresClient postgresClient;

  protected static String jobId;

  @ClassRule
  public static WireMockRule wireMock = new WireMockRule(
    new WireMockConfiguration().dynamicPort());

  @BeforeClass
  public static void beforeAll(final TestContext context) throws Exception {
    Async async = context.async();

    vertx = Vertx.vertx();
    okapiClient = new OkapiClient(getMockedOkapiUrl(), OKAPI_TENANT, OKAPI_TOKEN);
    tenantClient = new TenantClient(getMockedOkapiUrl(), OKAPI_TENANT, OKAPI_TOKEN);

    PostgresClient.setPostgresTester(new PostgresTesterContainer());

    mockEndpoints();

    DeploymentOptions deploymentOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", OKAPI_PORT));

    vertx.deployVerticle(RestVerticle.class.getName(), deploymentOptions, deployment -> {
      try {
        tenantClient.postTenant(getTenantAttributes(), postResult -> {
          if (postResult.failed()) {
            log.error(postResult.cause());
            return;
          }

          final HttpResponse<Buffer> postResponse = postResult.result();
          assertThat(postResponse.statusCode(), is(HttpStatus.HTTP_CREATED.toInt()));

          jobId = postResponse.bodyAsJson(TenantJob.class).getId();

          postgresClient = PostgresClient.getInstance(vertx, OKAPI_TENANT);

          tenantClient.getTenantByOperationId(jobId, GET_TENANT_TIMEOUT_MS, getResult -> {
            if (getResult.failed()) {
              log.error(getResult.cause());
              return;
            }

            final HttpResponse<Buffer> getResponse = getResult.result();
            assertThat(getResponse.statusCode(), is(HttpStatus.HTTP_OK.toInt()));
            assertThat(getResponse.bodyAsJson(TenantJob.class).getComplete(), is(true));

            async.complete();
          });

        });
      } catch (Exception e) {
        context.fail(e);
      }
    });
  }

  @AfterClass
  public static void afterAll(final TestContext context) {
    deleteTenant(tenantClient);
    Async async = context.async();
    vertx.close(context.asyncAssertSuccess(res -> {
      PostgresClient.stopPostgresTester();
      async.complete();
    }));
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

  @Before
  public void resetMocks() {
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
    final Parameter loadReferenceParameter = new Parameter()
      .withKey("loadReference").withValue("true");

    return new TenantAttributes()
      .withModuleFrom(format("%s-0.0.1", MODULE_NAME))
      .withModuleTo(format("%s-%s", MODULE_NAME, PomUtils.getModuleVersion()))
      .withParameters(Collections.singletonList(loadReferenceParameter));
  }

  protected void deleteAllFromTable(String tableName) {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    postgresClient.delete(tableName, new Criterion(), result -> future.complete(null));
    try {
      future.get(5, TimeUnit.SECONDS);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private static String generateOkapiToken() {
    final String payload = new JsonObject()
      .put("user_id", randomId())
      .put("tenant", OKAPI_TENANT)
      .put("sub", "admin")
      .toString();

    return format("1.%s.3", Base64.getEncoder()
      .encodeToString(payload.getBytes()));
  }

  protected static String getMockedOkapiUrl() {
    return "http://localhost:" + wireMock.port();
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
