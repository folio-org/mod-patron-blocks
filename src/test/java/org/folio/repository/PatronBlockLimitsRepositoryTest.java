package org.folio.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
  void beforeEach() {
    repository = new PatronBlockLimitsRepository(postgresClient);
    deleteAllFromTable(PatronBlockLimitsRepository.PATRON_BLOCK_LIMITS_TABLE_NAME);
  }

  @Test
  void findLimitsForPatronGroup(VertxTestContext context) {
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

    waitFor(Future.all(List.of(repository.save(limit1), repository.save(limit2), repository.save(limit3))));

    List<PatronBlockLimit> limits = waitFor(repository.findLimitsForPatronGroup(groupId1));
    assertEquals(2, limits.size());

    List<String> retrievedLimitIds = limits.stream()
      .map(PatronBlockLimit::getId)
      .collect(Collectors.toList());

    assertTrue(retrievedLimitIds.contains(id1));
    assertTrue(retrievedLimitIds.contains(id3));

    context.completeNow();
  }

  @Test
  void findLimitsForPatronGroups(VertxTestContext context) {
    String id1 = randomId();
    String id2 = randomId();
    String id3 = randomId();

    String groupId1 = randomId();
    String groupId2 = randomId();
    String groupId3 = randomId();

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

    waitFor(Future.all(List.of(repository.save(limit1), repository.save(limit2), repository.save(limit3))));

    Map<String, List<PatronBlockLimit>> limits = waitFor(repository.findLimitsForPatronGroups(List.of(groupId1, groupId2, groupId3)));
    assertEquals(2, limits.size());

    List<String> retrievedLimitIds = limits.values().stream()
      .flatMap(List::stream)
      .map(PatronBlockLimit::getId)
      .toList();

    assertTrue(retrievedLimitIds.contains(id1));
    assertTrue(retrievedLimitIds.contains(id2));
    assertTrue(retrievedLimitIds.contains(id3));

    context.completeNow();
  }

  @Test
  void shouldReturnEmptyListWhenPatronGroupIdsNullOrEmpty(VertxTestContext context) {
    Map<String, List<PatronBlockLimit>> emptyForNull = waitFor(repository.findLimitsForPatronGroups(null));
    assertTrue(emptyForNull.isEmpty());

    Map<String, List<PatronBlockLimit>> emptyForEmpty = waitFor(repository.findLimitsForPatronGroups(Collections.emptyList()));
    assertTrue(emptyForEmpty.isEmpty());

    context.completeNow();
  }

}
