package org.folio.rest.client;

import static io.vertx.core.Future.succeededFuture;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.User;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class UsersClient extends BulkDownloadClient<User> {
  private static final Logger log = LogManager.getLogger(UsersClient.class);

  public UsersClient(Vertx vertx, Map<String, String> okapiHeaders) {
    super("/users", "users", User.class, vertx, okapiHeaders);
  }

  public Future<String> findPatronGroupIdForUser(String userId) {
    return fetchById("users", userId, User.class).compose(user -> {
        String patronGroupId = user.getPatronGroup();
        log.info("Patron group ID for user {} successfully found: {}", userId, patronGroupId);
        return succeededFuture(patronGroupId);
      }
    );
  }

  public Future<Map<String, String>> findPatronGroupIdsForUsers(Collection<String> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      log.info("findPatronGroupIdsForUsers:: list of user IDs is null or empty");
      return succeededFuture(new HashMap<>());
    }

    return getByIds(userIds)
      .map(users -> users.stream()
        .filter(user -> user.getPatronGroup() != null)
        .collect(Collectors.toMap(User::getId, User::getPatronGroup)));
  }
}
