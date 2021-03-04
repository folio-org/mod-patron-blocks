package org.folio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

import static java.math.BigDecimal.ZERO;
import static java.util.Objects.requireNonNull;

public class MonetaryValue {
  private static final Currency USD = Currency.getInstance("USD");
  private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_EVEN;

  private final BigDecimal amount;
  private final Currency currency;

  public MonetaryValue(BigDecimal amount) {
    this(amount, USD);
  }

  public MonetaryValue(String amount) {
    this(from(amount), USD);
  }

  public MonetaryValue(Double amount) {
    this(from(amount), USD);
  }

  public MonetaryValue(Double amount, Currency currency) {
    this(from(amount), currency);
  }

  public MonetaryValue(BigDecimal amount, Currency currency) {
    this(amount, currency, DEFAULT_ROUNDING);
  }

  public MonetaryValue(BigDecimal amount, Currency currency, RoundingMode rounding) {
    requireNonNull(amount);
    requireNonNull(currency);
    requireNonNull(rounding);
    this.currency = currency;
    this.amount = amount.setScale(currency.getDefaultFractionDigits(), rounding);
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Currency getCurrency() {
    return currency;
  }

  public boolean isZero() {
    return ZERO.compareTo(amount) == 0;
  }

  public boolean isPositive() {
    return ZERO.compareTo(amount) < 0;
  }

  public boolean isNegative() {
    return ZERO.compareTo(amount) > 0;
  }

  public boolean isGreaterThan(MonetaryValue other) {
    return amount.compareTo(other.getAmount()) > 0;
  }

  public boolean isGreaterThanOrEquals(MonetaryValue other) {
    return amount.compareTo(other.getAmount()) >= 0;
  }

  public MonetaryValue subtract(MonetaryValue other) {
    return new MonetaryValue(amount.subtract(other.getAmount()));
  }

  public MonetaryValue add(MonetaryValue other) {
    return new MonetaryValue(amount.add(other.getAmount()));
  }

  public MonetaryValue min(MonetaryValue other) {
    return amount.compareTo(other.getAmount()) <= 0 ? this : other;
  }

  private static BigDecimal from(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  private static BigDecimal from(Double value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }

  public double toDouble() {
    return amount.doubleValue();
  }

  @Override
  public String toString() {
    return amount.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MonetaryValue that = (MonetaryValue) o;

    return Objects.equals(amount, that.amount)
      && Objects.equals(currency, that.currency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(amount, currency);
  }

}
