package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import org.folio.rest.TestBase;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.util.UUID;

@RunWith(VertxUnitRunner.class)
public class TenantRefAPITest extends TestBase {

  @Test
  public void postTenantShouldFailWhenRegistrationInPubsubFailed(TestContext context) {
    Async async = context.async();

    wireMock.stubFor(post(urlPathMatching("/pubsub/.+"))
      .willReturn(aResponse().withStatus(500).withBody("Module registration failed")));

    try {
      tenantClient.postTenant(getTenantAttributes(), response -> {
        context.assertEquals(500, response.result().statusCode());

        context.assertTrue(response.result().bodyAsString().contains(
          "EventDescriptor was not registered for eventType"));

        async.complete();
      });
    } catch (Exception e) {
      context.fail(e);
    }
  }

  @Test
  public void deleteTenantShouldSucceedWhenSuccessfullyUnsubscribedFromPubSub(TestContext context) {
    Async async = context.async();

    wireMock.stubFor(delete(urlPathMatching("/pubsub/event-types/.+/subscribers"))
      .willReturn(aResponse().withStatus(204)));

    wireMock.stubFor(delete(urlPathMatching("/pubsub/event-types/.+/publishers"))
      .willReturn(aResponse().withStatus(204)));

    try {
      tenantClient.deleteTenantByOperationId(jobId, response -> {
        context.assertEquals(204, response.result().statusCode());
        async.complete();
      });
    } catch (Exception e) {
      context.fail(e);
    }
  }

  @Test
  public void deleteTenantShouldFailWhenFailedToUnsubscribeFromPubSub(TestContext context) {
    Async async = context.async();

    wireMock.stubFor(delete(urlPathEqualTo("/pubsub/event-types/ITEM_CHECKED_IN/subscribers"))
      .atPriority(1)
      .willReturn(aResponse().withStatus(500)));
    wireMock.stubFor(delete(urlPathEqualTo("/pubsub/event-types/ITEM_CHECKED_OUT/subscribers"))
      .atPriority(1)
      .willReturn(aResponse().withStatus(400)));
    wireMock.stubFor(delete(urlPathMatching("/pubsub/event-types/\\w+/subscribers"))
      .atPriority(10)
      .willReturn(aResponse().withStatus(204)));

    try {
      tenantClient.deleteTenantByOperationId(jobId, response -> {
        context.assertEquals(500, response.result().statusCode());

        context.assertTrue(response.result().bodyAsString()
          .startsWith("Failed to unregister. Event types:"));

        async.complete();
      });
    } catch (Exception e) {
      context.fail(e);
    }
  }

  @Test
  public void deleteTenantShouldFailWhenFailedToUnregisterPublishersFromPubSub(
    TestContext context) {

    Async async = context.async();

    wireMock.stubFor(delete(urlPathEqualTo("/pubsub/event-types/ITEM_CHECKED_IN/publishers"))
      .atPriority(1)
      .willReturn(aResponse().withStatus(500)));
    wireMock.stubFor(delete(urlPathEqualTo("/pubsub/event-types/ITEM_CHECKED_OUT/publishers"))
      .atPriority(1)
      .willReturn(aResponse().withStatus(400)));
    wireMock.stubFor(delete(urlPathMatching("/pubsub/event-types/\\w+/publishers"))
      .atPriority(10)
      .willReturn(aResponse().withStatus(204)));

    try {
      tenantClient.deleteTenantByOperationId(jobId, response -> {
        context.assertEquals(500, response.result().statusCode());

        context.assertTrue(response.result().bodyAsString()
          .startsWith("Failed to unregister. Event types:"));

        async.complete();
      });
    } catch (Exception e) {
      context.fail(e);
    }
  }

}
