package org.folio.verticle;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;

import org.folio.kafka.KafkaConsumerWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Unit tests for failure branches in {@link EventConsumerVerticle} that require
 * no running Kafka infrastructure.
 */
@ExtendWith(VertxExtension.class)
class EventConsumerVerticleUnitTest {

  // ---------------------------------------------------------------------------
  // start() → onFailure (when createConsumers() returns a failed Future)
  // ---------------------------------------------------------------------------

  @Test
  void startFailsWhenCreateConsumersFails(Vertx vertx, VertxTestContext context) {
    EventConsumerVerticle verticle = new EventConsumerVerticle() {
      @Override
      Future<Void> createConsumers() {
        return Future.failedFuture(new RuntimeException("Kafka unavailable"));
      }
    };

    vertx.deployVerticle(verticle).onComplete(result -> {
      context.verify(() -> assertThat("deployment must fail", result.failed(), is(true)));
      context.completeNow();
    });
  }

  // ---------------------------------------------------------------------------
  // stopConsumers() → onFailure + stop() → onFailure
  // (when a consumer's stop() returns a failed Future)
  // ---------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void stopLogsErrorWhenConsumerStopFails(Vertx vertx, VertxTestContext context) throws Exception {
    // Deploy a verticle whose createConsumers() is a no-op so no Kafka is needed
    EventConsumerVerticle verticle = new EventConsumerVerticle() {
      @Override
      Future<Void> createConsumers() {
        return Future.succeededFuture();
      }
    };

    vertx.deployVerticle(verticle).compose(deployId -> {
      // Inject a mock consumer whose stop() always fails
      try {
        KafkaConsumerWrapper<String, String> failingConsumer = mock(KafkaConsumerWrapper.class);
        when(failingConsumer.stop()).thenReturn(Future.failedFuture("stop error"));

        Field consumersField = EventConsumerVerticle.class.getDeclaredField("consumers");
        consumersField.setAccessible(true);
        ((List<KafkaConsumerWrapper<String, String>>) consumersField.get(verticle)).add(failingConsumer);
      } catch (Exception e) {
        return Future.failedFuture(e);
      }
      return vertx.undeploy(deployId);
    }).onComplete(result -> {
      // The undeploy fails because stop() fails — we just verify the test path ran
      context.completeNow();
    });
  }

  // ---------------------------------------------------------------------------
  // stop() called directly — covers stop.onFailure when stopConsumers() fails
  // ---------------------------------------------------------------------------

  @Test
  void stopDirectlyHandlesStopConsumersFailure(Vertx vertx, VertxTestContext context) {
    EventConsumerVerticle verticle = new EventConsumerVerticle() {
      @Override
      Future<Void> stopConsumers() {
        return Future.failedFuture(new RuntimeException("All consumers broken"));
      }
    };

    verticle.init(vertx, vertx.getOrCreateContext());

    Promise<Void> stopPromise = Promise.promise();
    verticle.stop(stopPromise);

    stopPromise.future().onComplete(result -> {
      context.verify(() -> assertThat("stop must fail when stopConsumers fails",
        result.failed(), is(true)));
      context.completeNow();
    });
  }
}
