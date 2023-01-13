package org.folio.repository;

import static org.folio.repository.UserSummaryRepository.USER_SUMMARY_TABLE_NAME;
import static org.junit.Assert.assertFalse;

import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.ItemCheckedOutEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class EventRepositoryTest extends TestBase {
  private final String ITEM_CHECKED_OUT_EVENT_TABLE_NAME = "item_checked_out_event";
  private final EventRepository<ItemCheckedOutEvent> repository = new EventRepository<>(
    postgresClient, ITEM_CHECKED_OUT_EVENT_TABLE_NAME, ItemCheckedOutEvent.class);

  @Before
  public void setUp() {
    resetMocks();
    deleteAllFromTable(USER_SUMMARY_TABLE_NAME);
  }

  @Test
  public void shouldAddUserSummary(TestContext context) {
    Future<Void> result = repository.removeByUserId("''", "''");
    assertFalse(result.succeeded());
  }
}