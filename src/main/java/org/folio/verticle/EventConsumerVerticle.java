package org.folio.verticle;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.folio.domain.Event;
import org.folio.domain.event.EventMapper;
import org.folio.domain.event.FolioKafkaTopic;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.kafka.services.KafkaTopic;
import org.folio.rest.handlers.EventHandler;
import org.folio.rest.handlers.FeeFineBalanceChangedEventHandler;
import org.folio.rest.jaxrs.model.FeeFineBalanceChangedEvent;
import org.folio.rest.jaxrs.model.ItemAgedToLostEvent;
import org.folio.rest.jaxrs.model.ItemCheckedInEvent;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.folio.rest.jaxrs.model.ItemClaimedReturnedEvent;
import org.folio.rest.jaxrs.model.ItemDeclaredLostEvent;
import org.folio.rest.jaxrs.model.LoanClosedEvent;
import org.folio.rest.jaxrs.model.LoanDueDateChangedEvent;
import org.folio.util.pubsub.support.PomReader;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.log4j.Log4j2;
import tools.jackson.core.JacksonException;

@Log4j2
public class EventConsumerVerticle extends AbstractVerticle {

  public static final String MODULE_ID = String.format("%s-%s",
    PomReader.INSTANCE.getModuleName(), PomReader.INSTANCE.getVersion());
  private static final int DEFAULT_LOAD_LIMIT = 5;
  private static final String TENANT_ID_PATTERN = "\\w+";

  private final List<KafkaConsumerWrapper<String, String>> consumers = new ArrayList<>();
  private KafkaConfig kafkaConfig;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    this.kafkaConfig = buildKafkaConfig();
  }

  @Override
  public void start(Promise<Void> promise) {
    log.info("start:: starting verticle");

    createConsumers()
      .onSuccess(v -> log.info("start:: verticle started"))
      .onFailure(t -> log.error("start:: verticle start failed", t))
      .onComplete(promise);
  }

  @Override
  public void stop(Promise<Void> promise) {
    log.info("stop:: stopping verticle");

    stopConsumers()
      .onSuccess(v -> log.info("stop:: verticle stopped"))
      .onFailure(t -> log.error("stop:: verticle stop failed", t))
      .onComplete(promise);
  }

  private Future<Void> stopConsumers() {
    log.info("stopConsumers:: stopping consumers");

    return Future.all(
      consumers.stream()
        .map(KafkaConsumerWrapper::stop)
        .toList())
      .onSuccess(v -> log.info("stopConsumers:: event consumers stopped"))
      .onFailure(t -> log.error("stopConsumers:: failed to stop event consumers", t))
      .mapEmpty();
  }

  private Future<Void> createConsumers() {
    log.info("createConsumers:: creating consumers");
    return Future.all(List.of(
      createConsumer(FolioKafkaTopic.ITEM_CHECKED_OUT, ItemCheckedOutEvent.class, EventHandler::new),
      createConsumer(FolioKafkaTopic.ITEM_CHECKED_IN, ItemCheckedInEvent.class, EventHandler::new),
      createConsumer(FolioKafkaTopic.ITEM_DECLARED_LOST, ItemDeclaredLostEvent.class, EventHandler::new),
      createConsumer(FolioKafkaTopic.ITEM_AGED_TO_LOST, ItemAgedToLostEvent.class, EventHandler::new),
      createConsumer(FolioKafkaTopic.ITEM_CLAIMED_RETURNED, ItemClaimedReturnedEvent.class, EventHandler::new),
      createConsumer(FolioKafkaTopic.LOAN_DUE_DATE_CHANGED, LoanDueDateChangedEvent.class, EventHandler::new),
      createConsumer(FolioKafkaTopic.LOAN_CLOSED, LoanClosedEvent.class, EventHandler::new),
      createConsumer(FolioKafkaTopic.FEE_FINE_BALANCE_CHANGED, FeeFineBalanceChangedEvent.class,
        FeeFineBalanceChangedEventHandler::new)
    )).mapEmpty();
  }

  private <E extends Event> Future<Void> createConsumer(KafkaTopic topic, Class<E> eventType,
    BiFunction<List<KafkaHeader>, Vertx, EventHandler<E>> handlerFactory) {

    log.info("createConsumer:: creating consumer for topic {}", topic.topicName());

    var consumer = KafkaConsumerWrapper.<String, String>builder()
      .context(context)
      .vertx(vertx)
      .kafkaConfig(kafkaConfig)
      .loadLimit(DEFAULT_LOAD_LIMIT)
      .globalLoadSensor(new GlobalLoadSensor())
      .subscriptionDefinition(buildSubscriptionDefinition(topic))
      .processRecordErrorHandler((t, r) -> log.error("Failed to process event with key '{}'", r.key(), t))
      .build();

    return consumer.start(rec -> handleEvent(rec, eventType, handlerFactory), MODULE_ID)
      .onSuccess(v -> consumers.add(consumer))
      .onFailure(t -> log.error("createConsumer:: failed to create consumer for topic {}", topic.topicName(), t));
  }

  private static SubscriptionDefinition buildSubscriptionDefinition(KafkaTopic topic) {
    return SubscriptionDefinition.builder()
      .eventType(topic.topicName())
      .subscriptionPattern(topic.fullTopicName(TENANT_ID_PATTERN))
      .build();
  }

  private <E extends Event> Future<String> handleEvent(KafkaConsumerRecord<String, String> kafkaRecord,
    Class<E> eventType, BiFunction<List<KafkaHeader>, Vertx, EventHandler<E>> handlerFactory) {

    return deserializeEvent(kafkaRecord, eventType)
      .compose(event -> handlerFactory.apply(kafkaRecord.headers(), vertx).handle(event));
  }

  private static <E extends Event> Future<E> deserializeEvent(KafkaConsumerRecord<String, String> kafkaRecord,
    Class<E> eventType) {

    E event;
    try {
      event = EventMapper.toEvent(kafkaRecord, eventType);
    } catch (Exception e) {
      return failedFuture(e);
    }
    return succeededFuture(event);
  }

  private static KafkaConfig buildKafkaConfig() {
    log.info("buildKafkaConfig:: building Kafka config");

    KafkaConfig config = KafkaConfig.builder()
      .envId(KafkaEnvironmentProperties.environment())
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .replicationFactor(KafkaEnvironmentProperties.replicationFactor())
      .build();

    log.debug("buildKafkaConfig:: {}", config);
    return config;
  }

}
