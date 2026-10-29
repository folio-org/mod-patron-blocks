package org.folio.support;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Collection;

public record CqlQuery(String query) {

  public static final String MULTIPLE_VALUES_DELIMITER = " or ";
  public static final String EXACT_MATCH_ANY_QUERY_TEMPLATE = "%s==(%s)";

  public static CqlQuery exactMatchAny(String index, Collection<String> values) {
    if (isBlank(index)) {
      throw new IllegalArgumentException("Index cannot be blank");
    }
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("Values cannot be null or empty");
    }

    String joinedValues = values.stream()
      .map(value -> "\"" + value + "\"")
      .collect(joining(MULTIPLE_VALUES_DELIMITER));

    return new CqlQuery(format(EXACT_MATCH_ANY_QUERY_TEMPLATE, index, joinedValues));
  }

  @Override
  public String toString() {
    return query;
  }
}
