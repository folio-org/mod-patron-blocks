package org.folio.repository;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.domain.Condition;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.PatronBlockLimit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class PatronBlockLimitsRepositoryTest extends TestBase {

  private static final PatronBlockLimitsRepository repository =
    new PatronBlockLimitsRepository(postgresClient);

  @Before
  public void beforeEach() {
    deleteAllFromTable(PatronBlockLimitsRepository.PATRON_BLOCK_LIMITS_TABLE_NAME);
  }

  @Test
  public void findLimitsForPatronGroup(TestContext context) {
    Async async = context.async();

    String id1 = randomId();
    String id2 = randomId();
    String id3 = randomId();

    String groupId1 = randomId();
    String groupId2 = randomId();

    PatronBlockLimit limit1 = new PatronBlockLimit()
      .withId(id1)
      .withConditionId(Condition.MAX_NUMBER_OF_OVERDUE_ITEMS.getId())
      .withPatronGroupId(groupId1)
      .withValue(1.00);

    PatronBlockLimit limit2 = new PatronBlockLimit()
      .withId(id2)
      .withConditionId(Condition.MAX_NUMBER_OF_LOST_ITEMS.getId())
      .withPatronGroupId(groupId2)
      .withValue(2.00);

    PatronBlockLimit limit3 = new PatronBlockLimit()
      .withId(id3)
      .withConditionId(Condition.MAX_NUMBER_OF_OVERDUE_RECALLS.getId())
      .withPatronGroupId(groupId1)
      .withValue(3.00);

    GenericCompositeFuture.all(List.of(repository.save(limit1), repository.save(limit2), repository.save(limit3)))
      .onFailure(context::fail)
      .onSuccess(result -> repository.findLimitsForPatronGroup(groupId1)
        .onFailure(context::fail)
        .onSuccess(limits -> {
          context.assertEquals(2, limits.size());

          List<String> retrievedLimitIds = limits.stream()
            .map(PatronBlockLimit::getId)
            .collect(Collectors.toList());

          context.assertTrue(retrievedLimitIds.contains(id1));
          context.assertTrue(retrievedLimitIds.contains(id3));
          async.complete();
        }));
  }

}
