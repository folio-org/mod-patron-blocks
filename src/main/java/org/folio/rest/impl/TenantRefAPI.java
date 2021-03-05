package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.core.Response;

import org.folio.HttpStatus;
import org.folio.domain.EventType;
import org.folio.rest.client.PubsubClient;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.util.pubsub.PubSubClientUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class TenantRefAPI extends TenantAPI {
  private static final Logger log = LoggerFactory.getLogger(TenantRefAPI.class);

  @Override
  public void postTenant(TenantAttributes tenantAttributes, Map<String, String> headers,
    Handler<AsyncResult<Response>> handler, Context context) {

    log.info("postTenant called with tenant attributes: {}", JsonObject.mapFrom(tenantAttributes));

    super.postTenant(tenantAttributes, headers, res -> {
      if (res.failed()) {
        handler.handle(res);
        return;
      }

      PubSubClientUtils.registerModule(new OkapiConnectionParams(headers, context.owner()))
        .whenComplete((result, throwable) -> {
          if (isTrue(result) && throwable == null) {
            log.info("postTenant executed successfully");
            handler.handle(succeededFuture(PostTenantResponse
              .respond201WithApplicationJson(EMPTY)));
          } else {
            log.error("postTenant execution failed", throwable);
            handler.handle(succeededFuture(PostTenantResponse
              .respond500WithTextPlain(throwable.getLocalizedMessage())));
          }
        });
    }, context);
  }
}
