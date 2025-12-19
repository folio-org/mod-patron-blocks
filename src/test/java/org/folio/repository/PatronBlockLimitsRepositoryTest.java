package org.folio.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.domain.Condition;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.PatronBlockLimit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

public class PatronBlockLimitsRepositoryTest extends TestBase {

  private PatronBlockLimitsRepository repository;

  @BeforeEach
  public void beforeEach() {
    repository = new PatronBlockLimitsRepository(postgresClient);
    deleteAllFromTable(PatronBlockLimitsRepository.PATRON_BLOCK_LIMITS_TABLE_NAME);
  }

  @Test
  public void findLimitsForPatronGroup(VertxTestContext context) {
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

    Future.all(List.of(repository.save(limit1), repository.save(limit2), repository.save(limit3)))
      .onSuccess(result -> repository.findLimitsForPatronGroup(groupId1)
        .onSuccess(limits -> {
          assertEquals(2, limits.size());

          List<String> retrievedLimitIds = limits.stream()
            .map(PatronBlockLimit::getId)
            .collect(Collectors.toList());

          assertTrue(retrievedLimitIds.contains(id1));
          assertTrue(retrievedLimitIds.contains(id3));

          context.completeNow();
        }))
      .onComplete(r -> context.completeNow());
  }

}
