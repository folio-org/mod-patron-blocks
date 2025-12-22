package org.folio.util;

import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.validation.ValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class UuidHelperTest {
  private static final String VALID_UUID = "901d2ff8-7efb-4014-a9da-e1dc544402bc";
  private static final String INVALID_UUID = "901d2ff8-7efb-haha-a9da-e1dc544402bc";
  private static final String RANDOM_STRING = "not even close";
  private static final String EMPTY_STRING = "";

  static Stream<Arguments> shouldPass() {
    return Stream.of(
      Arguments.of(VALID_UUID, true),
      Arguments.of(VALID_UUID, false),
      Arguments.of(null, false)
    );
  }

  @ParameterizedTest
  @MethodSource
  void shouldPass(String uuid, boolean isRequired) {
    UuidHelper.validateUUID(uuid, isRequired);
  }

  static Stream<Arguments> shouldFail() {
    return Stream.of(
      Arguments.of(INVALID_UUID, true),
      Arguments.of(INVALID_UUID, false),
      Arguments.of(RANDOM_STRING, true),
      Arguments.of(RANDOM_STRING, false),
      Arguments.of(EMPTY_STRING, true),
      Arguments.of(EMPTY_STRING, false),
      Arguments.of(null, true)
    );
  }

  @ParameterizedTest
  @MethodSource
  void shouldFail(String uuid, boolean isRequired) {
    assertThrows(ValidationException.class, () -> UuidHelper.validateUUID(uuid, isRequired));
  }
}
