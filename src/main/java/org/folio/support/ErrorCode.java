package org.folio.support;

import lombok.Getter;

public enum ErrorCode {
  PATRON_GROUP_NOT_FOUND("PATRON_GROUP_NOT_FOUND");

  @Getter
  private final String value;

  ErrorCode(String value) {
    this.value = value;
  }
}
