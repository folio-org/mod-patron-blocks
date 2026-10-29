package org.folio.repository;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.groupingBy;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.PatronBlockLimit;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.support.CqlQuery;

import io.vertx.core.Future;

public class PatronBlockLimitsRepository extends BaseRepository<PatronBlockLimit> {
  private static final Logger log = LogManager.getLogger(PatronBlockLimitsRepository.class);
  public static final String PATRON_BLOCK_LIMITS_TABLE_NAME = "patron_block_limits";
  private static final String PATRON_GROUP_ID_FIELD = "patronGroupId";
  private static final String OPERATION_EQUALS = "=";
  private static final int DEFAULT_LIMITS_PER_GROUP = 100;

  public PatronBlockLimitsRepository(PostgresClient pgClient) {
    super(pgClient, PATRON_BLOCK_LIMITS_TABLE_NAME, PatronBlockLimit.class);
  }

  public Future<List<PatronBlockLimit>> findLimitsForPatronGroup(String patronGroupId) {
    log.debug("findLimitsForPatronGroup:: parameters patronGroupId: {}", patronGroupId);
    Criterion criterion = new Criterion(new Criteria()
      .addField("'" + PATRON_GROUP_ID_FIELD + "'")
      .setOperation(OPERATION_EQUALS)
      .setVal(patronGroupId)
      .setJSONB(true));

    return get(criterion);
  }

  public Future<Map<String, List<PatronBlockLimit>>> findLimitsForPatronGroups(Collection<String> patronGroupIds) {
    log.debug("findLimitsForPatronGroups:: patronGroupIds count: {}",
      patronGroupIds != null ? patronGroupIds.size() : 0);

    if (patronGroupIds == null || patronGroupIds.isEmpty()) {
      return succeededFuture(new HashMap<>());
    }

    CqlQuery query = CqlQuery.exactMatchAny("patronGroupId", new HashSet<>(patronGroupIds));

    return get(query.query(), 0, DEFAULT_LIMITS_PER_GROUP * patronGroupIds.size())
      .map(limits -> limits.stream().collect(groupingBy(PatronBlockLimit::getPatronGroupId)));
  }

  public Future<String> save(PatronBlockLimit limit) {
    return save(limit, limit.getId());
  }
}
