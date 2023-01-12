package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.repository.PatronBlockLimitsRepository.PATRON_BLOCK_LIMITS_TABLE_NAME;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;
import static org.folio.util.LogUtil.asJson;
import static org.folio.util.LogUtil.headersAsString;
import static org.folio.util.LogUtil.loggingResponseHandler;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.domain.MonetaryValue;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.PatronBlockLimit;
import org.folio.rest.jaxrs.resource.PatronBlockLimits;
import org.folio.rest.persist.PgUtil;

import com.google.common.collect.ImmutableList;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class PatronBlockLimitsAPI implements PatronBlockLimits {

  private static final Logger log = LogManager.getLogger(PatronBlockLimitsAPI.class);
  // IDs come from predefined data (see resources/db_scripts/populate-patron-block-conditions.sql/*)
  private static final List<String> CONDITIONS_IDS_WITH_DOUBLE_VALUE_TYPE =
    ImmutableList.of("cf7a0d5f-a327-4ca1-aa9e-dc55ec006b8a");
  private static final String VALUE_FIELD = "value";
  private static final double MIN_DOUBLE_LIMIT = 0.0;
  private static final double MAX_DOUBLE_LIMIT = 999999.99;
  private static final int MIN_INT_LIMIT = 0;
  private static final int MAX_INT_LIMIT = 999999;

  @Validate
  @Override
  public void getPatronBlockLimits(int offset, int limit, String query, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    log.debug("getPatronBlockLimits:: parameters offset: {}, limit: {}, query: {}, lang: {}, " +
        "okapiHeaders: {}", () -> offset, () -> limit, () -> query, () -> lang,
      () -> headersAsString(okapiHeaders));

    PgUtil.get(PATRON_BLOCK_LIMITS_TABLE_NAME, PatronBlockLimit.class,
      org.folio.rest.jaxrs.model.PatronBlockLimits.class, query, offset, limit,
      okapiHeaders, vertxContext, GetPatronBlockLimitsResponse.class,
      loggingResponseHandler("getPatronBlockLimits", asyncResultHandler, log));
  }

  @Validate
  @Override
  public void postPatronBlockLimits(String lang, PatronBlockLimit entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    log.debug("postPatronBlockLimits:: parameters lang: {}, entity: {}, okapiHeaders: {}",
      () -> lang, () -> asJson(entity), () -> headersAsString(okapiHeaders));

    Errors errors = validateEntity(entity);
    if (errors != null) {
      log.info("postPatronBlockLimits:: entity is invalid. Errors: {}", () -> asJson(errors));
      asyncResultHandler.handle(succeededFuture(PostPatronBlockLimitsResponse
          .respond422WithApplicationJson(errors)));
      return;
    }

    PgUtil.post(PATRON_BLOCK_LIMITS_TABLE_NAME, entity, okapiHeaders, vertxContext,
      PostPatronBlockLimitsResponse.class,
      loggingResponseHandler("postPatronBlockLimits", asyncResultHandler, log));
  }

  @Validate
  @Override
  public void putPatronBlockLimitsByPatronBlockLimitId(String patronBlockLimitId,
    String lang, PatronBlockLimit entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    log.debug("putPatronBlockLimitsByPatronBlockLimitId:: parameters patronBlockLimitId: {}, " +
      "lang: {}, entity: {}, okapiHeaders: {}", () -> patronBlockLimitId, () -> lang,
      () -> asJson(entity), () -> headersAsString(okapiHeaders));

    Errors errors = validateEntity(entity);
    if (errors != null) {
      log.info("putPatronBlockLimitsByPatronBlockLimitId:: entity is invalid. Errors: {}",
        () -> asJson(errors));
      asyncResultHandler.handle(succeededFuture(PutPatronBlockLimitsByPatronBlockLimitIdResponse
          .respond422WithApplicationJson(errors)));
      return;
    }

    PgUtil.put(PATRON_BLOCK_LIMITS_TABLE_NAME, entity, patronBlockLimitId, okapiHeaders,
      vertxContext, PutPatronBlockLimitsByPatronBlockLimitIdResponse.class,
      loggingResponseHandler("putPatronBlockLimitsByPatronBlockLimitId", asyncResultHandler, log));
  }

  @Validate
  @Override
  public void getPatronBlockLimitsByPatronBlockLimitId(String patronBlockLimitId,
    String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    log.debug("getPatronBlockLimitsByPatronBlockLimitId:: parameters patronBlockLimitId: {}, " +
        "lang: {}, okapiHeaders: {}", () -> patronBlockLimitId, () -> lang,
      () -> headersAsString(okapiHeaders));

    PgUtil.getById(PATRON_BLOCK_LIMITS_TABLE_NAME, PatronBlockLimit.class, patronBlockLimitId,
      okapiHeaders, vertxContext, GetPatronBlockLimitsByPatronBlockLimitIdResponse.class,
      loggingResponseHandler("getPatronBlockLimitsByPatronBlockLimitId", asyncResultHandler, log));
  }

  @Validate
  @Override
  public void deletePatronBlockLimitsByPatronBlockLimitId(String patronBlockLimitId,
    String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    log.debug("deletePatronBlockLimitsByPatronBlockLimitId:: parameters patronBlockLimitId: {}, " +
      "lang: {}, okapiHeaders: {}", () -> patronBlockLimitId, () -> lang,
      () -> headersAsString(okapiHeaders));

    PgUtil.deleteById(PATRON_BLOCK_LIMITS_TABLE_NAME, patronBlockLimitId, okapiHeaders,
      vertxContext, DeletePatronBlockLimitsByPatronBlockLimitIdResponse.class,
      loggingResponseHandler("deletePatronBlockLimitsByPatronBlockLimitId", asyncResultHandler, log));
  }

  private Errors validateEntity(PatronBlockLimit entity) {
    Double limit = entity.getValue();
    if (limit == null) {
      return null;
    }
    return CONDITIONS_IDS_WITH_DOUBLE_VALUE_TYPE.contains(entity.getConditionId())
      ? validateRangeForDoubleValueType(limit)
      : validateRangeForIntegerValueType(limit);
  }

  private Errors validateRangeForDoubleValueType(Double limit) {
    if (limit >= MIN_DOUBLE_LIMIT && limit <= MAX_DOUBLE_LIMIT) {
      return null;
    }
    return createValidationErrorMessage(VALUE_FIELD, limit.toString(),
      "Must be blank or a number from " + new MonetaryValue(MIN_DOUBLE_LIMIT) + " to "
        + new MonetaryValue(MAX_DOUBLE_LIMIT));
  }

  private Errors validateRangeForIntegerValueType(Double limit) {
    boolean isInt = limit % 1 == 0;
    if (isInt && limit >= MIN_INT_LIMIT && limit <= MAX_INT_LIMIT) {
      return null;
    }
    return createValidationErrorMessage(VALUE_FIELD, limit.toString(),
      "Must be blank or an integer from " + MIN_INT_LIMIT + " to " + MAX_INT_LIMIT);
  }
}
