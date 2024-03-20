package org.folio.util;

import java.util.UUID;

import jakarta.validation.ValidationException;

public class UuidHelper {
  private UuidHelper() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static void validateUUID(String uuid, boolean isRequired) {
    if ((isRequired || uuid != null) && !UuidUtil.isUuid(uuid)) {
      String message = String.format("Invalid UUID: \"%s\"",  uuid);
      throw new ValidationException(message);
    }
  }

  public static String randomId() {
    return UUID.randomUUID().toString();
  }
}
