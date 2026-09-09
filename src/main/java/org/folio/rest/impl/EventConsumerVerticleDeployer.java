package org.folio.rest.impl;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.resource.interfaces.PostDeployVerticle;
import org.folio.verticle.EventConsumerVerticle;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * Deploys the {@link EventConsumerVerticle} once the main verticle has finished
 * starting up. This is picked up automatically by {@code RestVerticle} which scans
 * the {@code org.folio.rest.impl} package for {@link PostDeployVerticle} implementations.
 */
public class EventConsumerVerticleDeployer implements PostDeployVerticle {

  private static final Logger log = LogManager.getLogger(EventConsumerVerticleDeployer.class);

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    String verticleClassName = EventConsumerVerticle.class.getSimpleName();
    log.info("init:: deploying {}", verticleClassName);

    vertx.deployVerticle(new EventConsumerVerticle(), new DeploymentOptions())
      .onSuccess(id -> {
        log.info("init:: {} deployed with id {}", verticleClassName, id);
        handler.handle(succeededFuture(true));
      })
      .onFailure(t -> {
        log.error("init:: failed to deploy {}", verticleClassName, t);
        handler.handle(failedFuture(t));
      });
  }
}
