package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static org.folio.util.LogUtil.headersAsString;
import static org.folio.util.LogUtil.loggingResponseHandler;
import static org.folio.util.PostgresUtils.getPostgresClient;
import static org.folio.util.UuidUtil.isUuid;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exception.EntityNotFoundInDbException;
import org.folio.rest.jaxrs.resource.UserSummaryUserId;
import org.folio.service.UserSummaryService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class UserSummaryAPI implements UserSummaryUserId {

  private static final Logger log = LogManager.getLogger(UserSummaryAPI.class);

  @Override
  public void getUserSummaryByUserId(String userId, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    log.debug("getUserSummaryByUserId:: parameters userId: {}, okapiHeaders: {}",
      () -> userId, () -> headersAsString(okapiHeaders));

    Handler<AsyncResult<Response>> loggingHandler = loggingResponseHandler(
      "getUserSummaryByUserId", asyncResultHandler, log);

    if (!isUuid(userId)) {
      loggingHandler.handle(succeededFuture(UserSummaryUserId.GetUserSummaryByUserIdResponse
        .respond400WithTextPlain(format("Invalid user ID: \"%s\"", userId))));
      return;
    }

    new UserSummaryService(getPostgresClient(okapiHeaders, vertxContext.owner())).getByUserId(userId)
      .onSuccess(userSummary -> loggingHandler.handle(succeededFuture(
        UserSummaryUserId.GetUserSummaryByUserIdResponse.respond200WithApplicationJson(userSummary))))
      .onFailure(failure -> {
        if (failure instanceof EntityNotFoundInDbException) {
          loggingHandler.handle(succeededFuture(
            UserSummaryUserId.GetUserSummaryByUserIdResponse.respond404WithTextPlain(
              failure.getLocalizedMessage())));
        }
        else {
          loggingHandler.handle(succeededFuture(
            UserSummaryUserId.GetUserSummaryByUserIdResponse.respond500WithTextPlain(
              failure.getLocalizedMessage())));
        }
      });
  }
}
