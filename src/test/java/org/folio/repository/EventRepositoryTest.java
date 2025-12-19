package org.folio.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

public class EventRepositoryTest extends TestBase {
  private final String ITEM_CHECKED_OUT_EVENT_TABLE_NAME = "item_checked_out_event";
  private EventRepository<ItemCheckedOutEvent> repository;

  @BeforeEach
  public void setUp() {
    resetMocks();

    repository = new EventRepository<>(postgresClient, ITEM_CHECKED_OUT_EVENT_TABLE_NAME,
      ItemCheckedOutEvent.class);

    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
  }

  @Test
  public void shouldAddUserSummary() {
    Future<Void> result = repository.removeByUserId("''", "''");
    assertFalse(result.succeeded());
  }
}