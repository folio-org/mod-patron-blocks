package org.folio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

public class MonetaryValueTest {

  @Test
  void stringConstructorThrowsExceptionWhenAmountIsNull() {
    assertThrows(NullPointerException.class, () -> new MonetaryValue((String) null));
  }

  @Test
  void doubleConstructorThrowsExceptionWhenAmountIsNull() {
    assertThrows(NullPointerException.class, () -> new MonetaryValue((Double) null));
  }

  @Test
  void bigDecimalConstructorThrowsExceptionWhenAmountIsNull() {
    assertThrows(NullPointerException.class, () -> new MonetaryValue((BigDecimal) null));
  }

  @ParameterizedTest
  @ValueSource(strings = { "0", "0.0", "0.00", "0.000", "0.005", "0.000000000000001" })
  void monetaryValueIsZero(String value) {
    assertTrue(new MonetaryValue(value).isZero());
    assertTrue(new MonetaryValue("-" + value).isZero());
  }

  @ParameterizedTest
  @ValueSource(strings = { "1", "0.006", "0.0051", "0.0050000000000001" })
  void monetaryValueIsNotZero(String value) {
    assertFalse(new MonetaryValue(value).isZero());
    assertFalse(new MonetaryValue("-" + value).isZero());
  }

  @ParameterizedTest
  @ValueSource(strings = { "1", "0.1", "0.01", "0.006", "0.0051", "0.0050000000000001" })
  void monetaryValueIsPositive(String value) {
    assertTrue(new MonetaryValue(value).isPositive());
  }

  @ParameterizedTest
  @ValueSource(strings = { "-1", "0", "0.00", "0.000", "0.005", "0.000999999" })
  void monetaryValueIsNotPositive(String value) {
    assertFalse(new MonetaryValue(value).isPositive());
  }

  @ParameterizedTest
  @ValueSource(strings = { "-1", "-0.1", "-0.01", "-0.006", "-0.0051", "-0.0050000000000001" })
  void monetaryValueIsNegative(String value) {
    assertTrue(new MonetaryValue(value).isNegative());
  }

  @ParameterizedTest
  @ValueSource(strings = { "1", "0", "0.00", "0.000", "0.005", "-0.005", "0.000000000001", "-0.000000000001" })
  void monetaryValueIsNotNegative(String value) {
    assertFalse(new MonetaryValue(value).isNegative());
  }

  @ParameterizedTest
  @CsvSource({
    "0, 0.00",
    "0.0, 0.00",
    "0.00, 0.00",
    "0.000, 0.00",
    "-0, 0.00",
    "-0.0, 0.00",
    "-0.00, 0.00",
    "-0.000, 0.00",
    "1, 1.00",
    "0.1, 0.10",
    "0.01, 0.01",
    "0.001, 0.00",
    "-1, -1.00",
    "-0.1, -0.10",
    "-0.01, -0.01",
    "-0.001, 0.00",
    "0.005, 0.00",
    "0.0051, 0.01",
    "0.0050000000001, 0.01",
    "-0.005, 0.00",
    "-0.0051, -0.01",
    "-0.0050000000001, -0.01",
    "0.015, 0.02",
    "0.0149, 0.01",
    "0.0150000000001, 0.02",
    "-0.015, -0.02",
    "-0.0149, -0.01",
    "-0.0150000000001, -0.02"
  })
  void toStringTest(String source, String expectedResult) {
    assertEquals(expectedResult, new MonetaryValue(source).toString());
  }

  @ParameterizedTest
  @ValueSource(strings = { "0", "0.0", "0.00", "0.01", "0.1" })
  void shouldBeGreaterThanIncomingValue(String value) {
    MonetaryValue monetaryValue = new MonetaryValue(0.5);
    assertTrue(monetaryValue.isGreaterThan(new MonetaryValue(value)));
  }

  @ParameterizedTest
  @ValueSource(strings = { "0", "0.0", "0.00", "0.01", "0.1" })
  void shouldBeGreaterOrEqualsThanIncomingValue(String value) {
    MonetaryValue monetaryValue = new MonetaryValue(0.1);
    assertTrue(monetaryValue.isGreaterThanOrEquals(new MonetaryValue(value)));
  }

  @Test
  void shouldCorrectlySubtractOneValueFromAnother() {
    MonetaryValue subtractResult = new MonetaryValue(0.05).subtract(new MonetaryValue(0.01));
    assertEquals(new MonetaryValue(0.04), subtractResult);
  }

  @Test
  void shouldCorrectlyAddOneValueToAnother() {
    MonetaryValue addResult = new MonetaryValue(0.05).add(new MonetaryValue(0.01));
    assertEquals(new MonetaryValue(0.06), addResult);
  }

  @Test
  void shouldReturnTheMinValueBetweenTwoMonetaryValues() {
    MonetaryValue minResult = new MonetaryValue(0.05).min(new MonetaryValue(0.04));
    assertEquals(new MonetaryValue(0.04), minResult);
  }
}
