package org.folio.rest.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.verticle.EventConsumerVerticle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class EventConsumerVerticleDeployerTest {

  @Test
  void initPropagatesDeploymentFailureToHandler(VertxTestContext context) {
    RuntimeException cause = new RuntimeException("Kafka unavailable");

    Vertx mockVertx = mock(Vertx.class);
    when(mockVertx.deployVerticle(any(Verticle.class), any(DeploymentOptions.class)))
      .thenReturn(Future.failedFuture(cause));

    Context mockContext = mock(Context.class);

    new EventConsumerVerticleDeployer().init(mockVertx, mockContext, result -> {
      context.verify(() -> {
        assertThat("handler must be called with a failure", result.failed(), is(true));
        assertThat(result.cause(), is(instanceOf(RuntimeException.class)));
        assertThat(result.cause().getMessage(), is("Kafka unavailable"));
      });
      context.completeNow();
    });
  }

  @Test
  void initSignalsSuccessWhenVerticleDeployed(VertxTestContext context) {
    Vertx mockVertx = mock(Vertx.class);
    when(mockVertx.deployVerticle(any(Verticle.class), any(DeploymentOptions.class)))
      .thenReturn(Future.succeededFuture("deployment-id-123"));

    Context mockContext = mock(Context.class);

    new EventConsumerVerticleDeployer().init(mockVertx, mockContext, result -> {
      context.verify(() -> {
        assertThat("handler must be called with success", result.succeeded(), is(true));
        assertThat(result.result(), is(true));
      });
      context.completeNow();
    });
  }

  @Test
  void deploysEventConsumerVerticleInstance(VertxTestContext context) {
    // Verify the deployer targets exactly EventConsumerVerticle (not some other class)
    Vertx mockVertx = mock(Vertx.class);
    when(mockVertx.deployVerticle(any(EventConsumerVerticle.class), any(DeploymentOptions.class)))
      .thenReturn(Future.succeededFuture("deployment-id"));

    Context mockContext = mock(Context.class);

    new EventConsumerVerticleDeployer().init(mockVertx, mockContext, result -> {
      context.verify(() -> assertThat(result.succeeded(), is(true)));
      context.completeNow();
    });
  }
}
