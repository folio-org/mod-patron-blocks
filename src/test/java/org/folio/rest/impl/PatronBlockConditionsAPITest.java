package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.folio.test.util.TestUtil.readFile;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.PatronBlockCondition;
import org.folio.rest.jaxrs.model.PatronBlockConditions;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.restassured.http.Header;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class PatronBlockConditionsAPITest extends TestBase {

  private static final String MAX_NUMBER_OF_LOST_ITEMS_CONDITION_ID = "72b67965-5b73-4840-bc0b-be8f3f6e047e";
  private static final String MAX_NUMBER_OF_LOST_ITEMS_NON_EXISTENT_ID = "72b67965-5b73-4840-bc0b-be8f3f6e084d";
  private static final Header USER_ID = new Header(XOkapiHeaders.USER_ID, "111111111");
  private static final String PATRON_BLOCK_CONDITIONS_URL = "/patron-block-conditions/";
  private static final String PATRON_BLOCK_CONDITIONS = "patron-block-conditions";
  private static final int NUMBER_OF_PREDEFINED_CONDITIONS = 6;

  @Test
  public void shouldReturnAllConditions() {
    PatronBlockConditions conditions = getWithStatus(PATRON_BLOCK_CONDITIONS_URL, SC_OK)
      .as(PatronBlockConditions.class);
    assertThat(conditions.getTotalRecords(), equalTo(NUMBER_OF_PREDEFINED_CONDITIONS));
  }

  @Test
  public void shouldReturnMaxNumberOfLostItemsCondition() {
    PatronBlockCondition patronBlockCondition = getWithStatus(PATRON_BLOCK_CONDITIONS_URL
      + MAX_NUMBER_OF_LOST_ITEMS_CONDITION_ID, SC_OK)
      .as(PatronBlockCondition.class);
    assertThat(patronBlockCondition.getName(), equalTo("Maximum number of lost items"));
  }

  @Test
  public void shouldUpdateMaxNumberOfLostItemsCondition()
    throws IOException, URISyntaxException {

    String updatedMaxNumberOfLostItemsCondition = readFile(PATRON_BLOCK_CONDITIONS
      + "/max_number_of_lost_items_updated.json");

    putWithStatus(PATRON_BLOCK_CONDITIONS_URL
      + MAX_NUMBER_OF_LOST_ITEMS_CONDITION_ID, updatedMaxNumberOfLostItemsCondition, SC_NO_CONTENT, USER_ID);
    PatronBlockCondition updatedCondition = getWithStatus(PATRON_BLOCK_CONDITIONS_URL
      + MAX_NUMBER_OF_LOST_ITEMS_CONDITION_ID, SC_OK).as(PatronBlockCondition.class);

    assertThat(updatedCondition.getBlockBorrowing(), equalTo(true));
    assertThat(updatedCondition.getBlockRenewals(), equalTo(true));
    assertThat(updatedCondition.getBlockRequests(), equalTo(true));
    assertThat(updatedCondition.getMessage(),
      equalTo("Maximum number of lost items has been reached"));
  }

  @Test
  public void cannotUpdateMaxNumberOfLostItemsConditionWithNonExistentId()
    throws IOException, URISyntaxException {

    String updatedConditionWithNonExistentId = readFile(PATRON_BLOCK_CONDITIONS
      + "/max_number_of_lost_items_non_existent_id.json");

    putWithStatus(PATRON_BLOCK_CONDITIONS_URL
        + MAX_NUMBER_OF_LOST_ITEMS_NON_EXISTENT_ID, updatedConditionWithNonExistentId,
      SC_NOT_FOUND, USER_ID);
    PatronBlockConditions conditions = getWithStatus(PATRON_BLOCK_CONDITIONS_URL, SC_OK)
      .as(PatronBlockConditions.class);
    assertThat(conditions.getTotalRecords(), equalTo(NUMBER_OF_PREDEFINED_CONDITIONS));
  }

  @Test
  public void cannotUpdateMaxNumberOfLostItemWithNoMessage()
    throws IOException, URISyntaxException {

    String maxNumberOfLostItems = readFile(PATRON_BLOCK_CONDITIONS
      + "/max_number_of_lost_items_no_message.json");
    PatronBlockCondition response = putWithStatus(PATRON_BLOCK_CONDITIONS_URL
      + MAX_NUMBER_OF_LOST_ITEMS_CONDITION_ID, maxNumberOfLostItems, SC_UNPROCESSABLE_ENTITY, USER_ID)
      .as(PatronBlockCondition.class);
    List<Map<String, Object>> errors = (List<Map<String, Object>>) response.getAdditionalProperties().get("errors");
    String message = (String) errors.get(0).get("message");
    assertThat(message, is("Message to be displayed is a required field if one or more blocked actions selected"));
  }

  @Test
  public void cannotUpdateMaxNumberOfLostItemWithMessageAndNoFlagSetToTrue()
    throws IOException, URISyntaxException {

    String maxNumberOfLostItems = readFile(PATRON_BLOCK_CONDITIONS
      + "/max_number_of_lost_items_no_flat_set_to_true.json");
    PatronBlockCondition response = putWithStatus(PATRON_BLOCK_CONDITIONS_URL
      + MAX_NUMBER_OF_LOST_ITEMS_CONDITION_ID, maxNumberOfLostItems, SC_UNPROCESSABLE_ENTITY, USER_ID)
      .as(PatronBlockCondition.class);
    List<Map<String, Object>> errors = (List<Map<String, Object>>) response.getAdditionalProperties().get("errors");
    String message = (String) errors.get(0).get("message");
    assertThat(message, is("One or more blocked actions must be selected for message to be displayed to be used"));
  }

  @Test
  public void cannotDeletePredefinedCondition() {
    deleteWithStatus(PATRON_BLOCK_CONDITIONS_URL + MAX_NUMBER_OF_LOST_ITEMS_CONDITION_ID,
      SC_BAD_REQUEST);
  }

  @Test
  public void cannotPostNewCondition()
    throws IOException, URISyntaxException {

    String maxNumberOfLostItems = readFile(PATRON_BLOCK_CONDITIONS
      + "/max_number_of_lost_items_no_flat_set_to_true.json");
    postWithStatus(PATRON_BLOCK_CONDITIONS_URL, maxNumberOfLostItems,
      SC_BAD_REQUEST, USER_ID);
  }
}
