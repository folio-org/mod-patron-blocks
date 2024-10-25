//package org.folio.rest;
//
//import io.vertx.core.json.JsonObject;
//import io.vertx.ext.unit.junit.VertxUnitRunner;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//
//import static org.awaitility.Awaitility.await;
//import static org.hamcrest.Matchers.is;
//import static org.hamcrest.Matchers.nullValue;
//import static org.hamcrest.Matchers.startsWith;
//
///**
// * Test that shaded jar and Dockerfile work.
// *
// * <p>Test that module installation and module upgrade work.
// */
//@RunWith(VertxUnitRunner.class)
//public class ApiIT extends TestBase {
//
//  @Test
//  public void health() {
//    okapiClient.get("/admin/health")
//      .then()
//      .statusCode(200)
//      .body(is("\"OK\""));
//  }
//
//  private void postTenant(JsonObject body) {
//    String location =
//      okapiClient.post("/_/tenant", body.encodePrettily())
//        .then()
//        .statusCode(201)
//        .extract()
//        .header("Location");
//
//    okapiClient.get(location + "?wait=30000")
//      .then()
//      .statusCode(200) // getting job record succeeds
//      .body("complete", is(true)) // job is complete
//      .body("error", is(nullValue())); // job has succeeded without error
//  }
//
//  @Test
//  public void installAndUpgrade() {
//    postTenant(new JsonObject().put("module_to", "999999.0.0"));
//    // migrate from 0.0.0, migration should be idempotent
//    postTenant(new JsonObject().put("module_to", "999999.0.0").put("module_from", "0.0.0"));
//
//    // smoke test
//    String checkoutBody = new JsonObject()
//      .put("userId", "11111111-1111-4444-8888-111111111111")
//      .put("loanId", "22222222-2222-4444-8888-222222222222")
//      .put("dueDate", "2020-12-31T23:59:59Z")
//      .encodePrettily();
//    okapiClient.post("/automated-patron-blocks/handlers/item-checked-out", checkoutBody)
//      .then()
//      .statusCode(204);
//
//    await().untilAsserted(() ->
//      okapiClient.get("/user-summary/11111111-1111-4444-8888-111111111111")
//        .then()
//        .statusCode(200)
//        .body("openLoans[0].loanId", is("22222222-2222-4444-8888-222222222222"))
//    );
//
//    // upsert with optimistic locking (MODPATBLK-102)
//    String dueDateChangedBody = new JsonObject()
//      .put("userId", "11111111-1111-4444-8888-111111111111")
//      .put("loanId", "22222222-2222-4444-8888-222222222222")
//      .put("dueDate", "2021-02-15T12:00:00")
//      .put("dueDateChangedByRecall", false)
//      .encodePrettily();
//    okapiClient.post("/automated-patron-blocks/handlers/loan-due-date-changed", dueDateChangedBody)
//      .then()
//      .statusCode(204);
//
//    await().untilAsserted(() ->
//      okapiClient.get("/user-summary/11111111-1111-4444-8888-111111111111")
//        .then()
//        .statusCode(200)
//        .body("openLoans[0].dueDate", startsWith("2021-02-15T12:00:00")));
//  }
//
//}
