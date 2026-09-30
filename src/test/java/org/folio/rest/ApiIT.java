package org.folio.rest;

import static org.awaitility.Awaitility.await;
import static org.folio.domain.event.FolioKafkaTopic.ITEM_CHECKED_OUT;
import static org.folio.domain.event.FolioKafkaTopic.LOAN_DUE_DATE_CHANGED;
import static org.folio.rest.utils.EntityBuilder.buildItemCheckedOutEvent;
import static org.folio.rest.utils.EntityBuilder.buildLoanDueDateChangedEvent;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

/**
 * Test that shaded jar and Dockerfile work.
 *
 * <p>Test that module installation and module upgrade work.
 */
public class ApiIT extends TestBase {

  @Test
  void health() {
    okapiClient.get("/admin/health")
      .then()
      .statusCode(200)
      .body(is("\"OK\""));
  }

  private void postTenant(JsonObject body) {
    String location =
      okapiClient.post("/_/tenant", body.encodePrettily())
        .then()
        .statusCode(201)
        .extract()
        .header("Location");

    okapiClient.get(location + "?wait=30000")
      .then()
      .statusCode(200) // getting job record succeeds
      .body("complete", is(true)) // job is complete
      .body("error", is(nullValue())); // job has succeeded without error
  }

  @Test
  void installAndUpgrade() {
    postTenant(new JsonObject().put("module_to", "mod_patron_blocks-999999.0.0"));
    // migrate from 0.0.0, migration should be idempotent
    postTenant(new JsonObject().put("module_to", "mod_patron_blocks-999999.0.0").put("module_from", "mod_patron_blocks-0.0.0"));

    // smoke test: publish an item-checked-out event via Kafka and verify it is processed
    String userId = "11111111-1111-4444-8888-111111111111";
    String loanId = "22222222-2222-4444-8888-222222222222";

    kafkaHelper.publishEventAndWaitUntilConsumed(ITEM_CHECKED_OUT, TEST_TENANT,
      buildItemCheckedOutEvent(userId, loanId,
        Date.from(Instant.parse("2020-12-31T23:59:59Z"))));

    await().untilAsserted(() ->
      okapiClient.get("/user-summary/" + userId)
        .then()
        .statusCode(200)
        .body("openLoans[0].loanId", is(loanId))
    );

    // upsert with optimistic locking (MODPATBLK-102)
    kafkaHelper.publishEventAndWaitUntilConsumed(LOAN_DUE_DATE_CHANGED, TEST_TENANT,
      buildLoanDueDateChangedEvent(userId, loanId,
        Date.from(LocalDateTime.parse("2021-02-15T12:00:00").atZone(ZoneOffset.UTC).toInstant()),
        false));

    await().untilAsserted(() ->
      okapiClient.get("/user-summary/" + userId)
        .then()
        .statusCode(200)
        .body("openLoans[0].dueDate", startsWith("2021-02-15T12:00:00")));
  }

}
