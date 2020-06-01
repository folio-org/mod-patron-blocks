package org.folio.util;

import javax.validation.ValidationException;

public class UuidHelper {
  private UuidHelper() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static void validateUUID(String uuid, boolean isRequired) {
    if ((isRequired || uuid != null) && !UuidUtil.isUuid(uuid)) {
      String message = String.format("Invalid %sUUID: \"%s\"", isRequired ? "required " : "", uuid);
      throw new ValidationException(message);
    }
  }
}