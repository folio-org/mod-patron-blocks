package org.folio.support;

import static java.lang.System.currentTimeMillis;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.util.Wait.waitFor;
import static org.folio.util.Wait.waitForSize;
import static org.folio.util.Wait.waitForValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.folio.domain.Event;
import org.folio.domain.event.FolioKafkaTopic;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.kafka.services.KafkaTopic;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import io.vertx.core.Vertx;
import io.vertx.kafka.admin.ConsumerGroupDescription;
import io.vertx.kafka.admin.ConsumerGroupListing;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.consumer.OffsetAndMetadata;
import io.vertx.kafka.client.producer.KafkaProducer;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class KafkaTestHelper {

  private static final String CONSUMER_GROUP_ID_PATTERN = "%s\\.mod-patron-blocks-\\d+\\.\\d+\\.\\d+";

  private static KafkaTestHelper INSTANCE;
  private Vertx vertx;
  private KafkaContainer kafkaContainer;
  private SimpleKafkaProducerManager producerManager;
  private KafkaAdminClient adminClient;
  private KafkaConfig kafkaConfig;

  private KafkaTestHelper() {
    start();
  }

  public static KafkaTestHelper getInstance() {
    if (INSTANCE != null) {
      log.info("getInstance:: returning existing instance");
      return INSTANCE;
    }

    INSTANCE = new KafkaTestHelper();
    return INSTANCE;
  }

  private void start() {
    log.info("start:: starting Kafka test helper");
    setSystemProperties();
    log.info("start:: starting Kafka container");
    KafkaContainer container = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.2.0"));
    container.start();
    String host = container.getHost();
    String port = String.valueOf(container.getFirstMappedPort());
    log.info("start:: Kafka container started: host={}, port={}", host, port);
    System.setProperty("kafka-host", host);
    System.setProperty("kafka-port", port);

    this.vertx = Vertx.vertx();
    this.kafkaContainer = container;
    this.kafkaConfig = buildKafkaConfig();
    this.producerManager = new SimpleKafkaProducerManager(vertx, kafkaConfig);
    this.adminClient = createAdminClient();

    Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
  }

  private void stop() {
    if (kafkaContainer == null || !kafkaContainer.isRunning()) {
      log.info("stop:: Kafka container is not running, nothing to stop");
      return;
    }

    log.info("stop:: stopping Kafka container");
    try {
      kafkaContainer.stop();
    } catch (Exception e) {
      log.error("stop:: failed to stop Kafka container", e);
    }

    if (adminClient != null) {
      try {
        adminClient.close();
      } catch (Exception e) {
        log.error("stop:: failed to stop Kafka admin client", e);
      }
    }
  }

  public void createTopics(String tenantId) {
    Arrays.stream(FolioKafkaTopic.values())
      .forEach(topic -> createTopic(topic, tenantId));
  }

  public Map<String, ConsumerGroupDescription> verifyConsumerGroups(
    Map<String, Integer> groupIdToSize) {

    return waitAtMost(30, SECONDS)
      .until(() -> waitFor(
          adminClient.describeConsumerGroups(new ArrayList<>(groupIdToSize.keySet()))),
        groups -> groups.entrySet()
          .stream()
          .collect(toMap(Map.Entry::getKey, e -> e.getValue().getMembers().size()))
          .entrySet()
          .containsAll(groupIdToSize.entrySet())
      );
  }

  public Collection<String> getConsumerGroups(int expectedGroupCount) {
    return waitAtMost(30, SECONDS)
      .until(() -> waitFor(adminClient.listConsumerGroups()), groups -> groups.size() == expectedGroupCount)
      .stream()
      .map(ConsumerGroupListing::getGroupId)
      .toList();
  }

  public Collection<String> getConsumerGroups() {
    return waitFor(adminClient.listConsumerGroups())
      .stream()
      .map(ConsumerGroupListing::getGroupId)
      .toList();
  }

  public Collection<String> getConsumerGroups(String groupIdPattern) {
    return getConsumerGroups()
      .stream()
      .filter(groupId -> groupId.matches(groupIdPattern))
      .toList();
  }

  public Collection<String> getConsumerGroups(String groupIdPattern, int expectedGroupCount) {
    return waitForSize(() -> getConsumerGroups(groupIdPattern), expectedGroupCount);
  }

  public void deleteConsumerGroup(String groupId) {
    if (groupExists(groupId)) {
      waitFor(adminClient.deleteConsumerGroups(List.of(groupId)));
      if (groupExists(groupId)) {
        fail("Failed to delete consumer group: " + groupId);
      }
    }
  }

  public boolean groupExists(String groupId) {
    return waitFor(adminClient.listConsumerGroups())
      .stream()
      .map(ConsumerGroupListing::getGroupId)
      .anyMatch(groupId::equals);
  }

  public List<String> findConsumerGroupIds(String pattern) {
    return waitFor(adminClient.listConsumerGroups())
      .stream()
      .map(ConsumerGroupListing::getGroupId)
      .filter(groupId -> groupId.matches(pattern))
      .toList();
  }

  public String findConsumerGroupId(KafkaTopic topic) {
    return findConsumerGroupId(topic.topicName());
  }

  public String findConsumerGroupId(String eventType) {
    List<String> groupIds = findConsumerGroupIds(String.format(CONSUMER_GROUP_ID_PATTERN, eventType));
    assertThat("Expected exactly one consumer group", groupIds, hasSize(1));
    return groupIds.getFirst();
  }

  public int getOffset(FolioKafkaTopic topic, String tenantId, String consumerGroupId) {
    return getOffset(topic.fullTopicName(tenantId), consumerGroupId);
  }

  @SneakyThrows
  public int getOffset(String topic, String consumerGroupId) {
    Integer offset = waitFor(adminClient.listConsumerGroupOffsets(consumerGroupId)
      .map(partitions -> Optional.ofNullable(partitions.get(new TopicPartition(topic, 0)))
        .map(OffsetAndMetadata::getOffset)
        .map(Long::intValue)
        .orElse(0))); // if topic does not exist yet

    return offset != null ? offset : 0;
  }

  public void publishEvent(Event eventPayload, FolioKafkaTopic topic, String tenantId) {
    publishEvent(eventPayload, topic.fullTopicName(tenantId), tenantId);
  }

  public void publishEventAndWaitUntilConsumed(KafkaTopic topic, String tenantId, Event eventPayload) {
    String consumerGroupId = findConsumerGroupId(topic);
    String fullTopicName = topic.fullTopicName(tenantId);
    int initialOffset = getOffset(fullTopicName, consumerGroupId);
    publishEvent(eventPayload, fullTopicName, tenantId);
    waitForValue(() -> getOffset(fullTopicName, consumerGroupId), initialOffset + 1);
  }

  public void publishEvent(Event event, String topic, String tenantId) {;
    var record = new KafkaProducerRecordBuilder<String, Event>(tenantId)
      .key(randomUUID().toString())
      .value(event)
      .topic(topic)
      .propagateOkapiHeaders(Map.of("X-Okapi-Tenant", tenantId))
      .build();

    var producer = createProducer(topic);
    waitFor(producer.write(record));
    waitFor(producer.close());
  }

  public KafkaProducer<String, String> createProducer(String name) {
    return producerManager.createShared(name);
  }

  public KafkaAdminClient createAdminClient() {
    Properties config = new Properties();
    config.put(BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getKafkaHost() + ":" + kafkaConfig.getKafkaPort());

    return KafkaAdminClient.create(vertx, config);
  }

  public void createTopic(KafkaTopic topic, String tenantId) {
    createTopic(topic.fullTopicName(tenantId));
  }

  public void createTopic(String topic) {
    createTopics(List.of(topic));
  }

  public void createTopics(Collection<String> topics) {
    Set<String> existingTopics = listTopics();
    List<String> nonExistentTopics = topics.stream()
      .filter(not(existingTopics::contains))
      .toList();

    if (nonExistentTopics.isEmpty()) {
      return;
    }

    List<NewTopic> newTopics = nonExistentTopics.stream()
      .map(topic -> new NewTopic(topic, 1, (short) 1))
      .toList();

    waitFor(adminClient.createTopics(newTopics));
    verifyTopicsExist(topics);
  }

  public Set<String> listTopics() {
    return waitFor(adminClient.listTopics());
  }

  public void deleteAllTopics() {
    deleteTopics(listTopics());
  }

  public void deleteTopics(Collection<String> topics) {
    List<String> existingTopics = listTopics()
      .stream()
      .filter(topics::contains)
      .toList();

    waitFor(adminClient.deleteTopics(existingTopics));
    verifyTopicsDoNotExist(topics);
  }

  public void clearTopic(String topic) {
    clearTopics(List.of(topic));
  }

  public void clearAllTopics() {
    clearTopics(listTopics());
  }

  public void clearTopics(Collection<String> topics) {
    if (topics.isEmpty()) {
      return;
    }

    List<String> existingTopics = listTopics()
      .stream()
      .filter(topics::contains)
      .toList();

    deleteTopics(existingTopics);
    createTopics(existingTopics);
  }

  public void verifyTopicExists(String topic) {
    verifyTopicsExist(List.of(topic));
  }

  public void verifyTopicsExist(Collection<KafkaTopic> topics, String tenantId) {
    verifyTopicsExist(topics.stream()
      .map(topic -> topic.fullTopicName(tenantId))
      .toList());
  }

  public void verifyTopicsExist(Collection<String> topics) {
    waitFor(() -> listTopics().containsAll(topics));
  }

  public void verifyTopicDoesNotExist(String topic) {
    verifyTopicsDoNotExist(List.of(topic));
  }

  public void verifyTopicsDoNotExist(Collection<String> topics) {
    waitFor(() -> listTopics().stream().noneMatch(topics::contains));
  }

  public void waitForTopicCount(int expectedCount) {
    waitForSize(this::listTopics, expectedCount);
  }

  private static void setSystemProperties() {
    // Set Kafka consumer to read messages from the beginning of the topic if no offset is present.
    // Helps avoid race condition between consumer and producer in tests.
    System.setProperty("kafka.consumer.auto.offset.reset", "earliest");
  }

  private static KafkaConfig buildKafkaConfig() {
    return KafkaConfig.builder()
      .envId(KafkaEnvironmentProperties.environment())
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .replicationFactor(KafkaEnvironmentProperties.replicationFactor())
      .build();
  }
}
