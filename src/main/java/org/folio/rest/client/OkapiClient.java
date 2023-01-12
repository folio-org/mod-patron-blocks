package org.folio.rest.client;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.TOKEN;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.folio.rest.client.WebClientProvider.getWebClient;
import static org.folio.util.LogUtil.bodyAsString;

import java.util.Map;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exception.EntityNotFoundException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class OkapiClient {
  private static final Logger log = LogManager.getLogger(OkapiClient.class);

  static final ObjectMapper objectMapper = new ObjectMapper();
  private final WebClient webClient;
  private final String okapiUrl;
  private final String tenant;
  private final String token;

  public OkapiClient(Vertx vertx, Map<String, String> okapiHeaders) {
    CaseInsensitiveMap<String, String> headers = new CaseInsensitiveMap<>(okapiHeaders);
    this.webClient = getWebClient(vertx);
    okapiUrl = headers.get(URL);
    tenant = headers.get(TENANT);
    token = headers.get(TOKEN);
  }

  HttpRequest<Buffer> getAbs(String path) {
    return webClient.requestAbs(HttpMethod.GET, okapiUrl + path)
      .putHeader(ACCEPT, APPLICATION_JSON)
      .putHeader(URL, okapiUrl)
      .putHeader(TENANT, tenant)
      .putHeader(TOKEN, token);
  }

  HttpRequest<Buffer> postAbs(String path) {
    return webClient.requestAbs(HttpMethod.POST, okapiUrl + path)
      .putHeader(ACCEPT, APPLICATION_JSON)
      .putHeader(URL, okapiUrl)
      .putHeader(TENANT, tenant)
      .putHeader(TOKEN, token);
  }

  <T> Future<T> fetchById(String pathToEntity, String id, Class<T> responseType) {
    log.debug("fetchById:: parameters pathToEntity: {}, id: {}, responseType: {}",
      pathToEntity, id, responseType);
    Promise<HttpResponse<Buffer>> promise = Promise.promise();
    String path = format("/%s/%s", pathToEntity, id);

    getAbs(path).send(promise);

    return promise.future().compose(response -> {
      int responseStatus = response.statusCode();
      if (responseStatus != 200) {
        String errorMessage = format("Failed to fetch %s by ID: %s. " +
            "Response: %d %s", responseType.getName(), id, responseStatus,
          bodyAsString(response));
        log.warn("fetchById:: {}", errorMessage);
        return failedFuture(new EntityNotFoundException(errorMessage));
      } else {
        try {
          T fetchedObject = objectMapper.readValue(response.bodyAsString(), responseType);
          log.info("fetchById:: Fetched by ID: {}. Response body: {}", () -> path,
            () -> bodyAsString(response));
          return succeededFuture(fetchedObject);
        } catch (JsonProcessingException e) {
          int statusCode = response.statusCode();
          String responseBody = bodyAsString(response);
          log.warn("fetchById:: Failed to parse response from {}. Status code: {}, " +
              "response body: {}", path, statusCode, responseBody, e);
          return failedFuture(e);
        }
      }
    });
  }

  public Future<JsonObject> getMany(String path, int limit, int offset) {
    log.debug("getMany:: parameters path: {}, limit: {}, offset: {}", path, limit, offset);
    Promise<HttpResponse<Buffer>> promise = Promise.promise();
    HttpRequest<Buffer> request = getAbs(path)
      .addQueryParam("limit", String.valueOf(limit))
      .addQueryParam("offset", String.valueOf(offset));
    request.send(promise);

    return promise.future().compose(response -> {
      int responseStatus = response.statusCode();
      if (responseStatus != 200) {
        log.warn("getMany:: Failed to fetch entities by path: {}. Response: {} {}",
          () -> path, () -> responseStatus, () -> bodyAsString(response));
      }
      log.info("getMany:: Fetched from {}. Response body: {}", () -> path,
        () -> bodyAsString(response));
      return succeededFuture(response.bodyAsJsonObject());
    });
  }

  protected <T> Future<T> fetchAll(String path, Class<T> responseType) {
    log.debug("fetchAll:: parameters path: {}, responseType: {}", path, responseType);
    Promise<HttpResponse<Buffer>> promise = Promise.promise();
    getAbs(path).send(promise);

    return promise.future().compose(response -> {
      int responseStatus = response.statusCode();
      if (responseStatus != 200) {
        String errorMessage = format("fetchAll:: Failed to fetch %s. Response: %d %s",
          responseType.getName(), responseStatus, bodyAsString(response));
        log.warn(errorMessage);
        return failedFuture(new EntityNotFoundException(errorMessage));
      } else {
        try {
          T fetchedObject = objectMapper.readValue(response.bodyAsString(), responseType);
          log.info("fetchAll:: Fetched from {}. Response body: {}", () -> path,
            () -> bodyAsString(response));
          return succeededFuture(fetchedObject);
        } catch (JsonProcessingException e) {
          int statusCode = response.statusCode();
          String responseBody = bodyAsString(response);
          log.warn("fetchAll:: Failed to parse response from {}. Status code: {}, " +
            "response body: {}", path, statusCode, responseBody, e);
          return failedFuture(e);
        }
      }
    });
  }
}
