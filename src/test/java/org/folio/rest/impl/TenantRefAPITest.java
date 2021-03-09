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
  public void deleteTenantShouldNotTryToUnregisterFromPubSub(
    TestContext context) {

    Async async = context.async();

    wireMock.stubFor(delete(urlPathMatching("/pubsub/event-types/\\w+/publishers"))
      .willReturn(aResponse().withStatus(500)));

    try {
      tenantClient.deleteTenantByOperationId(jobId, response -> {
        context.assertEquals(204, response.result().statusCode());

        async.complete();
      });
    } catch (Exception e) {
      context.fail(e);
    }
  }
}
