package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.folio.test.util.TestUtil.readFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.PatronBlockLimit;
import org.folio.rest.jaxrs.model.PatronBlockLimits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;

public class PatronBlockLimitsAPITest extends TestBase {

  private static final String PATRON_BLOCK_LIMITS_URL = "/patron-block-limits/";
  private static final String PATRON_BLOCK_LIMITS = "patron-block-limits";
  private static final Header USER_ID = new Header(XOkapiHeaders.USER_ID, "111111111");
  private static final String LIMIT_MAX_OUTSTANDING_FEEFINE_BALANCE_ID = "1de95200-72e4-4967-bdf8-257fb7559539";

  @AfterEach
  void tearDown() {
    PatronBlockLimits response = getWithStatus(PATRON_BLOCK_LIMITS_URL, SC_OK)
      .as(PatronBlockLimits.class);
    List<PatronBlockLimit> patronBlockLimits = response.getPatronBlockLimits();
    if (!patronBlockLimits.isEmpty()) {
      patronBlockLimits.forEach(entity -> deleteWithStatus(
        PATRON_BLOCK_LIMITS_URL + entity.getId(), SC_NO_CONTENT));
    }
  }

  @Test
  void shouldReturnAllPatronBlockLimits() throws IOException, URISyntaxException {
    postAllLimits();
    PatronBlockLimits response = getWithStatus(PATRON_BLOCK_LIMITS_URL, SC_OK)
      .as(PatronBlockLimits.class);
    assertEquals(6, response.getTotalRecords());
  }

  @Test
  void shouldReturnPatronBlockLimitByPatronBlockLimitId() throws IOException, URISyntaxException {
    postAllLimits();
    PatronBlockLimit response = getWithStatus(PATRON_BLOCK_LIMITS_URL
      + LIMIT_MAX_OUTSTANDING_FEEFINE_BALANCE_ID, SC_OK)
      .as(PatronBlockLimit.class);

    assertEquals("e5b45031-a202-4abb-917b-e1df9346fe2c", response.getPatronGroupId());
    assertEquals("cf7a0d5f-a327-4ca1-aa9e-dc55ec006b8a", response.getConditionId());
    assertEquals(10.4, response.getValue());
  }

  @Test
  void cannotCreatePatronBlockLimitWithInvalidIntegerLimit()
    throws IOException, URISyntaxException {

    String patronBlockLimit = readFile(PATRON_BLOCK_LIMITS
      + "/limit_max_number_of_lost_items_invalid_limit.json");
    PatronBlockLimit actualLimit = postWithStatus(PATRON_BLOCK_LIMITS_URL,
      patronBlockLimit, SC_UNPROCESSABLE_ENTITY, USER_ID)
      .as(PatronBlockLimit.class);

    String message = getErrorMessage(actualLimit);
    assertEquals("Must be blank or an integer from 0 to 999999", message);
  }

  @Test
  void shouldCreatePatronBlockLimitWithZeroValue()
    throws IOException, URISyntaxException {

    String patronBlockLimit = readFile(PATRON_BLOCK_LIMITS
      + "/limit_max_outstanding_feefine_balance_zero_value_limit.json");
    PatronBlockLimit actualLimit = postWithStatus(PATRON_BLOCK_LIMITS_URL,
      patronBlockLimit, SC_CREATED, USER_ID)
      .as(PatronBlockLimit.class);

    assertNull(actualLimit.getAdditionalProperties().get("errors"));
  }

  @Test
  void cannotCreatePatronBlockLimitWithDoubleLimitOutOfRange()
    throws IOException, URISyntaxException {

    String patronBlockLimit = readFile(PATRON_BLOCK_LIMITS
      + "/limit_max_outstanding_feefine_balance_limit_out_of_range.json");
    PatronBlockLimit actualLimit = postWithStatus(PATRON_BLOCK_LIMITS_URL,
      patronBlockLimit, SC_UNPROCESSABLE_ENTITY, USER_ID)
      .as(PatronBlockLimit.class);

    String message = getErrorMessage(actualLimit);
    assertEquals("Must be blank or a number from 0.00 to 999999.99", message);
  }

  @Test
  void shouldUpdatePatronBlockLimit() throws IOException, URISyntaxException {
    postAllLimits();
    String patronBlockLimit = readFile(PATRON_BLOCK_LIMITS
      + "/limit_max_outstanding_feefine_balance_with_updated_value.json");

    putWithStatus(PATRON_BLOCK_LIMITS_URL
      + LIMIT_MAX_OUTSTANDING_FEEFINE_BALANCE_ID, patronBlockLimit, SC_NO_CONTENT, USER_ID);
    PatronBlockLimit response = getWithStatus(PATRON_BLOCK_LIMITS_URL
      + LIMIT_MAX_OUTSTANDING_FEEFINE_BALANCE_ID, SC_OK)
      .as(PatronBlockLimit.class);

    assertEquals(20.4, response.getValue());
  }

  @Test
  void shouldUpdatePatronBlockLimitWithZeroValue() throws IOException, URISyntaxException {
    postAllLimits();
    String patronBlockLimit = readFile(PATRON_BLOCK_LIMITS
      + "/limit_max_outstanding_feefine_balance_zero_value_limit.json");

    putWithStatus(PATRON_BLOCK_LIMITS_URL + LIMIT_MAX_OUTSTANDING_FEEFINE_BALANCE_ID,
      patronBlockLimit, SC_NO_CONTENT, USER_ID);
  }

  private void postAllLimits() throws IOException, URISyntaxException {
    List<String> limits = new ArrayList<>();
    limits.add(readFile(PATRON_BLOCK_LIMITS + "/limit_max_number_of_items_charged_out.json"));
    limits.add(readFile(PATRON_BLOCK_LIMITS + "/limit_max_number_of_lost_items.json"));
    limits.add(readFile(PATRON_BLOCK_LIMITS + "/limit_max_number_of_overdue_items.json"));
    limits.add(readFile(PATRON_BLOCK_LIMITS + "/limit_max_number_of_overdue_recalls.json"));
    limits.add(readFile(PATRON_BLOCK_LIMITS + "/limit_max_outstanding_feefine_balance.json"));
    limits.add(readFile(PATRON_BLOCK_LIMITS + "/limit_recall_overdue_by_max_number_of_days.json"));

    limits.forEach(limit -> postWithStatus(PATRON_BLOCK_LIMITS_URL, limit, SC_CREATED, USER_ID)
      .as(PatronBlockLimit.class));
  }

  private String getErrorMessage(PatronBlockLimit response) {
    List<Map<String, Object>> errors =
      (List<Map<String, Object>>) response.getAdditionalProperties().get("errors");
    return (String) errors.get(0).get("message");
  }
}
