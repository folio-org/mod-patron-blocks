package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.util.LogUtil.asJson;
import static org.folio.util.LogUtil.loggingResponseHandler;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.domain.event.FolioKafkaTopic;
import org.folio.kafka.services.KafkaAdminClientService;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.utils.TenantTool;

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

    super.postTenant(tenantAttributes, headers,
      res -> handleAfterParentTenant(res, loggingHandler, TenantTool.tenantId(headers), context),
      context);
  }

  void handleAfterParentTenant(AsyncResult<Response> res,
    Handler<AsyncResult<Response>> handler, String tenantId, Context context) {

    if (res.failed()) {
      handler.handle(res);
      return;
    }
    log.info("postTenant:: creating Kafka topics for tenant {}", tenantId);
    createKafkaAdminClientService(context)
      .createKafkaTopics(FolioKafkaTopic.values(), tenantId)
      .onSuccess(v -> {
        log.info("postTenant:: Kafka topics created for tenant {}", tenantId);
        handler.handle(res);
      })
      .onFailure(t -> {
        log.error("postTenant:: failed to create Kafka topics for tenant {}", tenantId, t);
        handler.handle(succeededFuture(
          Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(t.getMessage())
            .type("text/plain")
            .build()));
      });
  }

  KafkaAdminClientService createKafkaAdminClientService(Context context) {
    return new KafkaAdminClientService(context.owner());
  }
}
