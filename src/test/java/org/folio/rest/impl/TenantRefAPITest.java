package org.folio.rest.impl;

import java.util.Arrays;

import org.folio.domain.event.FolioKafkaTopic;
import org.folio.rest.TestBase;
import org.junit.jupiter.api.Test;

import io.vertx.junit5.VertxTestContext;

public class TenantRefAPITest extends TestBase {

  @Test
  void postTenantCreatesKafkaTopics(VertxTestContext context) {
    kafkaHelper.verifyTopicsExist(Arrays.asList(FolioKafkaTopic.values()), TEST_TENANT);
    context.completeNow();
  }
}
