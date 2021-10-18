package org.folio.rest;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

/**
 * Test that shaded jar and Dockerfile work.
 *
 * <p>Test that module installation and module upgrade work.
 */
public class ApiIT {

 private static final Logger LOGGER = LoggerFactory.getLogger(ApiIT.class);

  private static final Network network = Network.newNetwork();

  @ClassRule
  public static final GenericContainer<?> module =
    new GenericContainer<>(
      new ImageFromDockerfile("mod-patron-blocks").withFileFromPath(".", Path.of(".")))
    .withNetwork(network)
    .withExposedPorts(8081)
    .withEnv("DB_HOST", "postgres")
    .withEnv("DB_PORT", "5432")
    .withEnv("DB_USERNAME", "username")
    .withEnv("DB_PASSWORD", "password")
    .withEnv("DB_DATABASE", "postgres");

  @ClassRule
  public static final PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>("postgres:12-alpine")
    .withNetwork(network)
    .withNetworkAliases("postgres")
    .withExposedPorts(5432)
    .withUsername("username")
    .withPassword("password")
    .withDatabaseName("postgres");

  @ClassRule
  public static final MockServerContainer okapi =
    new MockServerContainer(DockerImageName.parse("mockserver/mockserver:mockserver-5.11.2"))
    .withNetwork(network)
    .withNetworkAliases("okapi")
    .withExposedPorts(1080);

  @BeforeClass
  public static void beforeClass() {
    RestAssured.reset();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://" + module.getHost() + ":" + module.getFirstMappedPort();
    RestAssured.requestSpecification = new RequestSpecBuilder()
        .addHeader("X-Okapi-Url", "http://okapi:1080")
        .addHeader("X-Okapi-Tenant", "testtenant")
      .setContentType(ContentType.JSON)
      .build();

    var mockServerClient = new MockServerClient(okapi.getHost(), okapi.getServerPort());
    mockServerClient.when(request("/pubsub/.*")).respond(response().withStatusCode(201));

    module.followOutput(new Slf4jLogConsumer(LOGGER).withSeparateOutputStreams());
  }

  @Test
  public void health() {
    when().
      get("/admin/health").
    then().
      statusCode(200).
      body(is("\"OK\""));
  }

  private void postTenant(JsonObject body) {
    String location =
        given().
          body(body.encodePrettily()).
        when().
          post("/_/tenant").
        then().
          statusCode(201).
        extract().
          header("Location");

    when().
      get(location + "?wait=30000").
    then().
      statusCode(200).
      body("complete", is(true));
  }

  @Test
  public void installAndUpgrade() {
    postTenant(new JsonObject().put("module_to", "999999.0.0"));
    // migrate from 0.0.0, migration should be idempotent
    postTenant(new JsonObject().put("module_to", "999999.0.0").put("module_from", "0.0.0"));

    // smoke test
    given().
      body(new JsonObject()
          .put("userId", "11111111-1111-4444-8888-111111111111")
          .put("loanId", "22222222-2222-4444-8888-222222222222")
          .put("dueDate", "2020-12-31T23:59:59Z")
          .encodePrettily()).
    when().
      post("/automated-patron-blocks/handlers/item-checked-out").
    then().
      statusCode(204);

    await().untilAsserted(() ->
      when().
        get("/user-summary/11111111-1111-4444-8888-111111111111").
      then().
        statusCode(200).
        body("openLoans[0].loanId", is("22222222-2222-4444-8888-222222222222")));

    // upsert with optimistic locking (MODPATBLK-102)
    given().
      body(new JsonObject()
          .put("userId", "11111111-1111-4444-8888-111111111111")
          .put("loanId", "22222222-2222-4444-8888-222222222222")
          .put("dueDate", "2021-02-15T12:00:00")
          .put("dueDateChangedByRecall", false)
          .encodePrettily()).
    when().
      post("/automated-patron-blocks/handlers/loan-due-date-changed").
    then().
      statusCode(204);

    await().untilAsserted(() ->
      when().
        get("/user-summary/11111111-1111-4444-8888-111111111111").
      then().
        statusCode(200).
        body("openLoans[0].dueDate", startsWith("2021-02-15T12:00:00")));
  }

}
