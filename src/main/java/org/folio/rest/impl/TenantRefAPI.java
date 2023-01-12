package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.folio.util.LogUtil.asJson;
import static org.folio.util.LogUtil.loggingResponseHandler;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.util.pubsub.PubSubClientUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class TenantRefAPI extends TenantAPI {
  private static final Logger log = LogManager.getLogger(TenantRefAPI.class);

  @Override
  public void postTenant(TenantAttributes tenantAttributes, Map<String, String> headers,
    Handler<AsyncResult<Response>> handler, Context context) {

    log.debug("postTenant:: parameters tenantAttributes: {}, headers: {}",
      () -> asJson(tenantAttributes), () -> asJson(headers));

    Handler<AsyncResult<Response>> loggingHandler = loggingResponseHandler(
      "postTenant", handler, log);

    super.postTenant(tenantAttributes, headers, res -> {
      if (res.failed()) {
        loggingHandler.handle(res);
        return;
      }
      PubSubClientUtils.registerModule(new OkapiConnectionParams(headers, context.owner()))
        .whenComplete((result, throwable) -> {
          if (isTrue(result) && throwable == null) {
            loggingHandler.handle(res);
          } else {
            loggingHandler.handle(succeededFuture(PostTenantResponse
              .respond500WithTextPlain(throwable.getLocalizedMessage())));
          }
        });
    }, context);
  }
}
