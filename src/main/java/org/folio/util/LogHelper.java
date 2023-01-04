package org.folio.util;

import static com.google.common.primitives.Ints.min;
import static java.lang.String.format;

import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.PostgresClient;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

public class LogHelper {
  private static final Logger log = LogManager.getLogger(LogHelper.class);
  public static final String R_N_LINE_SEPARATOR = "\\r|\\n";
  public static final String R_LINE_SEPARATOR = "\\r";
  private static final int MAX_OBJECT_LENGTH = 10000;
  private static final int DEFAULT_NUM_OF_LIST_ELEMENTS_TO_LOG = 5;

  public static String logAsJson(Object object) {
    if (object == null) {
      return null;
    }

    try {
      return PostgresClient.pojo2JsonObject(object).encode().substring(0, MAX_OBJECT_LENGTH);
    } catch (JsonProcessingException ex) {
      log.warn("Error logging an object of type {}", object.getClass().getCanonicalName(), ex);
      return null;
    }
  }

  public static String logList(List<? extends Object> list) {
    return logList(list, DEFAULT_NUM_OF_LIST_ELEMENTS_TO_LOG);
  }

  public static String logList(List<? extends Object> list, int numberOfElementsToLog) {
    if (list == null) {
      return "null";
    } else {
      return format("list(size=%d, first %d elements: %s)", list.size(),
        min(list.size(), numberOfElementsToLog), logAsJson(list.subList(0, numberOfElementsToLog)));
    }
  }

  public static String logResponseBody(HttpResponse<Buffer> response) {
    return response.bodyAsString().replaceAll(R_N_LINE_SEPARATOR, R_LINE_SEPARATOR)
      .substring(0, MAX_OBJECT_LENGTH);
  }

  public static Handler<AsyncResult<Response>> loggingResponseHandler(String methodName,
    Handler<AsyncResult<Response>> asyncResultHandler, Logger logger) {

    return responseAsyncResult -> {
      asyncResultHandler.handle(responseAsyncResult);

      Response response = responseAsyncResult.result();
      logger.info("{}:: result: Response code {}, body {}", methodName, response.getStatus(),
        response.readEntity(String.class).substring(0, MAX_OBJECT_LENGTH));
    };
  }
}
