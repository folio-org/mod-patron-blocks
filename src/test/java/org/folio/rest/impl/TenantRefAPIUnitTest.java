package org.folio.rest.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import javax.ws.rs.core.Response;

import org.folio.kafka.services.KafkaAdminClientService;
import org.folio.kafka.services.KafkaTopic;
import org.junit.jupiter.api.Test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;

/**
 * Unit tests for the branches inside {@link TenantRefAPI#handleAfterParentTenant}.
 * Integration-level coverage (happy path) is in {@link TenantRefAPITest}.
 */
class TenantRefAPIUnitTest {

  // ---- res.failed() branch -----------------------------------------------

  @Test
  void handleAfterParentTenantForwardsResultWhenParentFailed() {
    AtomicReference<AsyncResult<Response>> captured = new AtomicReference<>();
    RuntimeException cause = new RuntimeException("DB initialisation failed");

    new TenantRefAPI().handleAfterParentTenant(
      Future.failedFuture(cause), captured::set, "test_tenant", null);

    assertThat("handler must be invoked", captured.get(), is(notNullValue()));
    assertThat(captured.get().failed(), is(true));
    assertThat(captured.get().cause(), is(sameInstance(cause)));
  }

  // ---- onFailure of createKafkaTopics branch -------------------------------

  @Test
  void handleAfterParentTenantCallsHandlerEvenWhenKafkaTopicsCreationFails() {
    KafkaAdminClientService mockService = mock(KafkaAdminClientService.class);
    when(mockService.createKafkaTopics(any(KafkaTopic[].class), anyString()))
      .thenReturn(Future.failedFuture("Kafka broker unavailable"));

    TenantRefAPI api = new TenantRefAPI() {
      @Override
      KafkaAdminClientService createKafkaAdminClientService(Context ctx) {
        return mockService;
      }
    };

    Response mockResponse = mock(Response.class);
    when(mockResponse.getStatus()).thenReturn(201);
    AsyncResult<Response> superSucceeded = Future.succeededFuture(mockResponse);

    AtomicReference<AsyncResult<Response>> captured = new AtomicReference<>();
    api.handleAfterParentTenant(superSucceeded, captured::set, "test_tenant", mock(Context.class));

    assertThat("handler must be invoked despite Kafka failure", captured.get(), is(notNullValue()));
    // The original succeeded response is forwarded even on Kafka failure
    assertThat(captured.get().succeeded(), is(true));
    assertThat(captured.get().result(), is(sameInstance(mockResponse)));
  }

  // ---- onSuccess of createKafkaTopics branch (unit-level) ------------------

  @Test
  void handleAfterParentTenantCallsHandlerWhenKafkaTopicsCreationSucceeds() {
    KafkaAdminClientService mockService = mock(KafkaAdminClientService.class);
    when(mockService.createKafkaTopics(any(KafkaTopic[].class), anyString()))
      .thenReturn(Future.succeededFuture());

    TenantRefAPI api = new TenantRefAPI() {
      @Override
      KafkaAdminClientService createKafkaAdminClientService(Context ctx) {
        return mockService;
      }
    };

    Response mockResponse = mock(Response.class);
    when(mockResponse.getStatus()).thenReturn(201);
    AsyncResult<Response> superSucceeded = Future.succeededFuture(mockResponse);

    AtomicReference<AsyncResult<Response>> captured = new AtomicReference<>();
    api.handleAfterParentTenant(superSucceeded, captured::set, "test_tenant", mock(Context.class));

    assertThat("handler must be invoked", captured.get(), is(notNullValue()));
    assertThat(captured.get().succeeded(), is(true));
    assertThat(captured.get().result(), is(sameInstance(mockResponse)));
  }
}
