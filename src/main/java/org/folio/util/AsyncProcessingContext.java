package org.folio.util;

import static java.lang.String.format;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AsyncProcessingContext {
  private static final Logger log = LogManager.getLogger(AsyncProcessingContext.class);

  protected abstract String getName();

  public void logFailedValidationError(String failedStep, String additionalMessage) {
    String message = format("Context '%s' is invalid, failed to perform '%s'", getName(),
      failedStep);

    if (additionalMessage != null) {
      message += format(". %s", additionalMessage);
    }

    log.warn("logFailedValidationError:: {}", message);
  }

  public void logFailedValidationError(String failedStep) {
    logFailedValidationError(failedStep, null);
  }
}
