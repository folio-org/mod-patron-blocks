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
import io.vertx.core.Future;
import io.vertx.core.Handler;

public class TenantRefAPI extends TenantAPI {
  private static final Logger log = LogManager.getLogger(TenantRefAPI.class);

  @Override
  public void postTenant(TenantAttributes tenantAttributes, Map<String, String> headers,
    Handler<AsyncResult<Response>> handler, Context context) {

    log.debug("postTenant:: parameters tenantAttributes: {}, headers: {}",
      () -> asJson(tenantAttributes), () -> asJson(headers));

    Handler<AsyncResult<Response>> loggingHandler = loggingResponseHandler("postTenant", handler, log);
    String tenantId = TenantTool.tenantId(headers);

    super.postTenantSync(tenantAttributes, headers, context)
      .compose(res -> {
        if (res.getStatus() != 204) {
          return succeededFuture(res);
        }
        return createKafkaTopicsForTenant(tenantId, context).map(v -> res);
      })
      .recover(t -> {
        log.error("postTenant:: unexpected error during tenant initialization for {}", tenantId, t);
        return succeededFuture(PostTenantResponse.respond500WithTextPlain(t.getMessage()));
      })
      .onComplete(loggingHandler);
  }

  Future<Void> createKafkaTopicsForTenant(String tenantId, Context context) {
    log.info("postTenant:: creating Kafka topics for tenant {}", tenantId);
    return createKafkaAdminClientService(context)
      .createKafkaTopics(FolioKafkaTopic.values(), tenantId)
      .onSuccess(v -> log.info("postTenant:: Kafka topics created for tenant {}", tenantId))
      .onFailure(t -> log.error("postTenant:: failed to create Kafka topics for tenant {}", tenantId, t));
  }

  KafkaAdminClientService createKafkaAdminClientService(Context context) {
    return new KafkaAdminClientService(context.owner());
  }
}
