package org.folio.rest.client;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exception.HttpFailureException;
import org.folio.util.StringUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.type.CollectionType;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.client.HttpResponse;

public class BulkDownloadClient<T> extends OkapiClient {
  private static final Logger log = LogManager.getLogger(BulkDownloadClient.class);
  private static final String PATH_TEMPLATE = "%s?limit=%d&query=%s";
  private static final String SORT_BY_ID = " sortBy id";
  private static final String DEFAULT_QUERY = "cql.allRecords=1";

  private final String path;
  private final String arrayName;
  private final Class<T> valueType;

  public BulkDownloadClient(String path, String arrayName, Class<T> valueType, Vertx vertx,
    Map<String, String> headers) {

    super(vertx, headers);
    this.path = path;
    this.arrayName = arrayName;
    this.valueType = valueType;
  }

  public Future<List<T>> fetchPage(String query, int pageSize) {
    String fullQuery = defaultIfBlank(query, DEFAULT_QUERY) + SORT_BY_ID;
    String encodedFullQuery = StringUtil.urlEncode(fullQuery);
    String fullPath = String.format(PATH_TEMPLATE, path, pageSize, encodedFullQuery);

    log.info("Attempting to fetch a page of {} {}...", pageSize, arrayName);

    Promise<HttpResponse<Buffer>> promise = Promise.promise();
    getAbs(fullPath).send(promise);

    return promise.future().compose(response -> {
      int responseStatus = response.statusCode();
      if (responseStatus != 200) {
        String errorMessage = String.format("Failed to fetch %s. Response: %d %s",
          arrayName, responseStatus, response.bodyAsString().replaceAll(R_N_LINE_SEPARATOR, R_LINE_SEPARATOR));
        log.error(errorMessage);
        return failedFuture(new HttpFailureException(errorMessage));
      } else {
        try {
          CollectionType javaType = objectMapper.getTypeFactory()
            .constructCollectionType(List.class, valueType);
          JsonArray jsonArray = response.bodyAsJsonObject().getJsonArray(arrayName);
          List<T> result = objectMapper.readValue(jsonArray.encode(), javaType);
          log.info("Successfully fetched {} {} from: {}", result.size(), arrayName, path);
          return succeededFuture(result);
        } catch (JsonProcessingException e) {
          log.error("Failed to parse JSON response from: {}", path);
          return failedFuture(e);
        }
      }
    });
  }

}
