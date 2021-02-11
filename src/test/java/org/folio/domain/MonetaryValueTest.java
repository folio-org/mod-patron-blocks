package org.folio.domain;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigDecimal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

@RunWith(JUnitParamsRunner.class)
public class MonetaryValueTest {

  @Test(expected = NullPointerException.class)
  public void stringConstructorThrowsExceptionWhenAmountIsNull() {
    new MonetaryValue((String) null);
  }

  @Test(expected = NullPointerException.class)
  public void doubleConstructorThrowsExceptionWhenAmountIsNull() {
    new MonetaryValue((Double) null);
  }

  @Test(expected = NullPointerException.class)
  public void bigDecimalConstructorThrowsExceptionWhenAmountIsNull() {
    new MonetaryValue((BigDecimal) null);
  }

  @Test
  @Parameters({ "0", "0.0", "0.00", "0.000", "0.005", "0.000000000000001" })
  public void monetaryValueIsZero(String value) {
    assertTrue(new MonetaryValue(value).isZero());
    assertTrue(new MonetaryValue("-" + value).isZero());
  }

  @Test
  @Parameters({ "1", "0.006", "0.0051", "0.0050000000000001" })
  public void monetaryValueIsNotZero(String value) {
    assertFalse(new MonetaryValue(value).isZero());
    assertFalse(new MonetaryValue("-" + value).isZero());
  }

  @Test
  @Parameters({ "1", "0.1", "0.01", "0.006", "0.0051", "0.0050000000000001" })
  public void monetaryValueIsPositive(String value) {
    assertTrue(new MonetaryValue(value).isPositive());
  }

  @Test
  @Parameters({ "-1", "0", "0.00", "0.000", "0.005", "0.000999999" })
  public void monetaryValueIsNotPositive(String value) {
    assertFalse(new MonetaryValue(value).isPositive());
  }

  @Test
  @Parameters({ "-1", "-0.1", "-0.01", "-0.006", "-0.0051", "-0.0050000000000001" })
  public void monetaryValueIsNegative(String value) {
    assertTrue(new MonetaryValue(value).isNegative());
  }

  @Test
  @Parameters({ "1", "0", "0.00", "0.000", "0.005", "-0.005", "0.000000000001", "-0.000000000001" })
  public void monetaryValueIsNotNegative(String value) {
    assertFalse(new MonetaryValue(value).isNegative());
  }

  @Test
  @Parameters({
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
    "-0.0150000000001, -0.02",
  })
  public void toStringTest(String source, String expectedResult) {
    assertEquals(expectedResult, new MonetaryValue(source).toString());
  }

  @Test
  @Parameters({"0", "0.0", "0.00", "0.01", "0.1"})
  public void shouldBeGreaterThanIncomingValue(String value) {
    MonetaryValue monetaryValue = new MonetaryValue(0.5);
    assertTrue(monetaryValue.isGreaterThan(new MonetaryValue(value)));
  }

  @Test
  @Parameters({"0", "0.0", "0.00", "0.01", "0.1"})
  public void shouldBeGreaterOrEqualsThanIncomingValue(String value) {
    MonetaryValue monetaryValue = new MonetaryValue(0.1);
    assertTrue(monetaryValue.isGreaterThanOrEquals(new MonetaryValue(value)));
  }

  @Test
  public void shouldCorrectlySubtractOneValueFromAnother() {
    MonetaryValue subtractResult = new MonetaryValue(0.05).subtract(new MonetaryValue(0.01));
    assertEquals(new MonetaryValue(0.04), subtractResult);
  }

  @Test
  public void shouldCorrectlyAddOneValueToAnother() {
    MonetaryValue addResult = new MonetaryValue(0.05).add(new MonetaryValue(0.01));
    assertEquals(new MonetaryValue(0.06), addResult);
  }

  @Test
  public void shouldReturnTheMinValueBetweenTwoMonetaryValues() {
    MonetaryValue minResult = new MonetaryValue(0.05).min(new MonetaryValue(0.04));
    assertEquals(new MonetaryValue(0.04), minResult);
  }
}
