package org.folio.repository;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.PatronBlockLimit;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Future;

public class PatronBlockLimitsRepository extends BaseRepository<PatronBlockLimit> {
  private static final Logger log = LogManager.getLogger(PatronBlockLimitsRepository.class);
  public static final String PATRON_BLOCK_LIMITS_TABLE_NAME = "patron_block_limits";
  private static final String PATRON_GROUP_ID_FIELD = "'patronGroupId'";
  private static final String OPERATION_EQUALS = "=";

  public PatronBlockLimitsRepository(PostgresClient pgClient) {
    super(pgClient, PATRON_BLOCK_LIMITS_TABLE_NAME, PatronBlockLimit.class);
  }

  public Future<List<PatronBlockLimit>> findLimitsForPatronGroup(String patronGroupId) {
    log.debug("findLimitsForPatronGroup:: parameters patronGroupId: {}", patronGroupId);
    Criterion criterion = new Criterion(new Criteria()
      .addField(PATRON_GROUP_ID_FIELD)
      .setOperation(OPERATION_EQUALS)
      .setVal(patronGroupId)
      .setJSONB(true));

    return get(criterion);
  }

  public Future<String> save(PatronBlockLimit limit) {
    return save(limit, limit.getId());
  }
}
