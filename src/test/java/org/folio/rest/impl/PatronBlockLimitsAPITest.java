package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.folio.test.util.TestUtil.readFile;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.PatronBlockLimit;
import org.folio.rest.jaxrs.model.PatronBlockLimits;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.restassured.http.Header;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class PatronBlockLimitsAPITest extends TestBase {

  private static final String PATRON_BLOCK_LIMITS_URL = "/patron-block-limits/";
  private static final String PATRON_BLOCK_LIMITS = "patron-block-limits";
  private static final Header USER_ID = new Header(XOkapiHeaders.USER_ID, "111111111");
  private static final String LIMIT_MAX_OUTSTANDING_FEEFINE_BALANCE_ID = "1de95200-72e4-4967-bdf8-257fb7559539";

  @After
  public void tearDown() {
    PatronBlockLimits response = getWithStatus(PATRON_BLOCK_LIMITS_URL, SC_OK)
      .as(PatronBlockLimits.class);
    List<PatronBlockLimit> patronBlockLimits = response.getPatronBlockLimits();
    if (!patronBlockLimits.isEmpty()) {
      patronBlockLimits.forEach(entity -> deleteWithStatus(
        PATRON_BLOCK_LIMITS_URL + entity.getId(), SC_NO_CONTENT));
    }
  }

  @Test
  public void shouldReturnAllPatronBlockLimits() throws IOException, URISyntaxException {
    postAllLimits();
    PatronBlockLimits response = getWithStatus(PATRON_BLOCK_LIMITS_URL, SC_OK)
      .as(PatronBlockLimits.class);
    assertThat(response.getTotalRecords(), is(6));
  }

  @Test
  public void shouldReturnPatronBlockLimitByPatronBlockLimitId() throws IOException, URISyntaxException {
    postAllLimits();
    PatronBlockLimit response = getWithStatus(PATRON_BLOCK_LIMITS_URL
      + LIMIT_MAX_OUTSTANDING_FEEFINE_BALANCE_ID, SC_OK)
      .as(PatronBlockLimit.class);

    assertThat(response.getPatronGroupId(), equalTo("e5b45031-a202-4abb-917b-e1df9346fe2c"));
    assertThat(response.getConditionId(), equalTo("cf7a0d5f-a327-4ca1-aa9e-dc55ec006b8a"));
    assertThat(response.getValue(), equalTo(10.4));
  }

  @Test
  public void cannotCreatePatronBlockLimitWithInvalidIntegerLimit()
    throws IOException, URISyntaxException {

    String patronBlockLimit = readFile(PATRON_BLOCK_LIMITS
      + "/limit_max_number_of_lost_items_invalid_limit.json");
    PatronBlockLimit actualLimit = postWithStatus(PATRON_BLOCK_LIMITS_URL,
      patronBlockLimit, SC_UNPROCESSABLE_ENTITY, USER_ID)
      .as(PatronBlockLimit.class);

    String message = getErrorMessage(actualLimit);
    assertThat(message, is("Must be blank or an integer from 0 to 999999"));
  }

  @Test
  public void shouldCreatePatronBlockLimitWithZeroValue()
    throws IOException, URISyntaxException {

    String patronBlockLimit = readFile(PATRON_BLOCK_LIMITS
      + "/limit_max_outstanding_feefine_balance_zero_value_limit.json");
    PatronBlockLimit actualLimit = postWithStatus(PATRON_BLOCK_LIMITS_URL,
      patronBlockLimit, SC_CREATED, USER_ID)
      .as(PatronBlockLimit.class);

    assertThat(actualLimit.getAdditionalProperties().get("errors"), nullValue());
  }

  @Test
  public void cannotCreatePatronBlockLimitWithDoubleLimitOutOfRange()
    throws IOException, URISyntaxException {

    String patronBlockLimit = readFile(PATRON_BLOCK_LIMITS
      + "/limit_max_outstanding_feefine_balance_limit_out_of_range.json");
    PatronBlockLimit actualLimit = postWithStatus(PATRON_BLOCK_LIMITS_URL,
      patronBlockLimit, SC_UNPROCESSABLE_ENTITY, USER_ID)
      .as(PatronBlockLimit.class);

    String message = getErrorMessage(actualLimit);
    assertThat(message, is("Must be blank or a number from 0.00 to 999999.99"));
  }

  @Test
  public void shouldUpdatePatronBlockLimit() throws IOException, URISyntaxException {
    postAllLimits();
    String patronBlockLimit = readFile(PATRON_BLOCK_LIMITS
      + "/limit_max_outstanding_feefine_balance_with_updated_value.json");

    putWithStatus(PATRON_BLOCK_LIMITS_URL
      + LIMIT_MAX_OUTSTANDING_FEEFINE_BALANCE_ID, patronBlockLimit, SC_NO_CONTENT, USER_ID);
    PatronBlockLimit response = getWithStatus(PATRON_BLOCK_LIMITS_URL
      + LIMIT_MAX_OUTSTANDING_FEEFINE_BALANCE_ID, SC_OK)
      .as(PatronBlockLimit.class);

    assertThat(response.getValue(), equalTo(20.4));
  }

  @Test
  public void shouldUpdatePatronBlockLimitWithZeroValue() throws IOException, URISyntaxException {
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
