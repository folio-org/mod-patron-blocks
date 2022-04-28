package org.folio.repository;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.util.UuidHelper.randomId;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.UserSummary;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Future;

public class UserSummaryRepository extends BaseRepository<UserSummary> {

  private static final Logger log = LogManager.getLogger(UserSummaryRepository.class);

  public static final String USER_SUMMARY_TABLE_NAME = "user_summary";
  private static final String USER_ID_FIELD = "'userId'";
  private static final String OPERATION_EQUALS = "=";
  private static final String FIND_SUMMARY_BY_FEE_FINE_ID_QUERY_TEMPLATE =
    "openFeesFines == \"*\\\"feeFineId\\\": \\\"%s\\\"*\"";

  public UserSummaryRepository(PostgresClient pgClient) {
    super(pgClient, USER_SUMMARY_TABLE_NAME, UserSummary.class);
  }

  public Future<String> upsert(UserSummary entity) {
    return super.upsert(entity, entity.getId());
  }

  public Future<String> save(UserSummary entity) {
    return super.save(entity, entity.getId());
  }

  public Future<Boolean> update(UserSummary entity) {
    return super.update(entity, entity.getId());
  }

  public Future<UserSummary> findByUserIdOrBuildNew(String userId) {
    return getByUserId(userId)
      .map(summary -> summary.orElseGet(() -> buildEmptyUserSummary(userId)));
  }

  public Future<Optional<UserSummary>> findByFeeFineId(String feeFineId) {
    String query = String.format(FIND_SUMMARY_BY_FEE_FINE_ID_QUERY_TEMPLATE, feeFineId);

    return get(query, 0, 1)
      .map(result -> result.stream().findFirst());
  }

  public Future<Optional<UserSummary>> getByUserId(String userId) {
    return this.get(buildCriterion(userId))
      .compose(results -> {
        if (results.isEmpty()) {
          return succeededFuture(Optional.empty());
        }

        return succeededFuture(Optional.ofNullable(results.get(0)));
      });
  }

  public Future<Boolean> deleteByUserId(String userId) {
    log.info("Deleting summary for user {}", userId);

    return delete(buildCriterion(userId));
  }

  private UserSummary buildEmptyUserSummary(String userId) {
    return new UserSummary()
      .withId(randomId())
      .withUserId(userId);
  }

  private static Criterion buildCriterion(String userId) {
    return new Criterion(new Criteria()
      .addField(USER_ID_FIELD)
      .setOperation(OPERATION_EQUALS)
      .setVal(userId)
      .setJSONB(true));
  }

}
