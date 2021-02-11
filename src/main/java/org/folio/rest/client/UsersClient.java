package org.folio.rest.client;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.User;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class UsersClient extends OkapiClient {
  private static final Logger log = LogManager.getLogger(UsersClient.class);

  public UsersClient(Vertx vertx, Map<String, String> okapiHeaders) {
    super(vertx, okapiHeaders);
  }

  public Future<String> findPatronGroupIdForUser(String userId) {
    return fetchById("users", userId, User.class).compose(user -> {
        String patronGroupId = user.getPatronGroup();
        log.info("Patron group ID for user {} successfully found: {}", userId, patronGroupId);
        return succeededFuture(patronGroupId);
      }
    );
  }
}
