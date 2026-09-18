package org.folio.rest.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.folio.kafka.services.KafkaAdminClientService;
import org.folio.kafka.services.KafkaTopic;
import org.junit.jupiter.api.Test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;

/**
 * Unit tests for the branches inside {@link TenantRefAPI#createKafkaTopicsForTenant}.
 * Integration-level coverage (happy path) is in {@link TenantRefAPITest}.
 */
class TenantRefAPIUnitTest {

  // ---- onSuccess of createKafkaTopics ----------------------------------------

  @Test
  void createKafkaTopicsForTenantSucceeds() {
    KafkaAdminClientService mockService = mock(KafkaAdminClientService.class);
    when(mockService.createKafkaTopics(any(KafkaTopic[].class), anyString()))
      .thenReturn(Future.succeededFuture());

    TenantRefAPI api = new TenantRefAPI() {
      @Override
      KafkaAdminClientService createKafkaAdminClientService(Context ctx) {
        return mockService;
      }
    };

    AtomicReference<AsyncResult<Void>> captured = new AtomicReference<>();
    api.createKafkaTopicsForTenant("test_tenant", mock(Context.class))
      .onComplete(captured::set);

    assertThat("future must complete", captured.get(), is(notNullValue()));
    assertThat(captured.get().succeeded(), is(true));
  }

  // ---- onFailure of createKafkaTopics ----------------------------------------

  @Test
  void createKafkaTopicsForTenantFailsWhenKafkaBrokerUnavailable() {
    KafkaAdminClientService mockService = mock(KafkaAdminClientService.class);
    when(mockService.createKafkaTopics(any(KafkaTopic[].class), anyString()))
      .thenReturn(Future.failedFuture("Kafka broker unavailable"));

    TenantRefAPI api = new TenantRefAPI() {
      @Override
      KafkaAdminClientService createKafkaAdminClientService(Context ctx) {
        return mockService;
      }
    };

    AtomicReference<AsyncResult<Void>> captured = new AtomicReference<>();
    api.createKafkaTopicsForTenant("test_tenant", mock(Context.class))
      .onComplete(captured::set);

    assertThat("future must complete", captured.get(), is(notNullValue()));
    assertThat(captured.get().failed(), is(true));
  }
}
