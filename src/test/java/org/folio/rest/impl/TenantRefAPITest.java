package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.folio.rest.TestBase;
import org.junit.jupiter.api.Test;

import io.vertx.junit5.VertxTestContext;

public class TenantRefAPITest extends TestBase {

  @Test
  void postTenantShouldFailWhenRegistrationInPubsubFailed(VertxTestContext context) {
    wireMock.stubFor(post(urlPathMatching("/pubsub/.+"))
      .willReturn(aResponse().withStatus(500).withBody("Module registration failed")));

    try {
      tenantClient.postTenant(getTenantAttributes(), response -> {
        assertEquals(500, response.result().statusCode());

        assertTrue(response.result().bodyAsString().contains(
          "EventDescriptor was not registered for eventType"));

        context.completeNow();
      });
    } catch (Exception e) {
      context.failNow(e);
    }
  }

  @Test
  void deleteTenantShouldNotTryToUnregisterFromPubSub(
    VertxTestContext context) {

    wireMock.stubFor(delete(urlPathMatching("/pubsub/event-types/\\w+/publishers"))
      .willReturn(aResponse().withStatus(500)));

    try {
      tenantClient.deleteTenantByOperationId(jobId, response -> {
        assertEquals(204, response.result().statusCode());

        context.completeNow();
      });
    } catch (Exception e) {
      context.failNow(e);
    }
  }
}
