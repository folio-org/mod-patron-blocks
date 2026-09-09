package org.folio.rest.impl;

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

    super.postTenant(tenantAttributes, headers, res -> {
      if (res.failed()) {
        loggingHandler.handle(res);
        return;
      }
      String tenantId = TenantTool.tenantId(headers);
      log.info("postTenant:: creating Kafka topics for tenant {}", tenantId);
      new KafkaAdminClientService(context.owner())
        .createKafkaTopics(FolioKafkaTopic.values(), tenantId)
        .onSuccess(v -> {
          log.info("postTenant:: Kafka topics created for tenant {}", tenantId);
          loggingHandler.handle(res);
        })
        .onFailure(t -> {
          log.error("postTenant:: failed to create Kafka topics for tenant {}", tenantId, t);
          loggingHandler.handle(res);
        });
    }, context);
  }
}
