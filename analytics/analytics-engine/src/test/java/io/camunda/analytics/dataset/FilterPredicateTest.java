/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.dataset.FilterPredicate.Operator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The value-shape rules of {@link FilterPredicate} itself (construction and validation). */
final class FilterPredicateTest {

  @Test
  void shouldCarryNoValueOnAPresenceCheck() {
    // when a presence predicate is built (a blank wire value is tolerated and normalized)
    for (final FilterPredicate predicate :
        List.of(
            FilterPredicate.isNull("field"),
            FilterPredicate.notNull("field"),
            new FilterPredicate("field", Operator.IS_NULL, ""),
            new FilterPredicate("field", Operator.NOT_NULL, "  "))) {
      // then it carries no comparison value
      assertThat(predicate.value()).isNull();
    }
  }

  @Test
  void shouldRejectAValueOnAPresenceCheck() {
    // when / then — a meaningful value on a presence check is a declaration error
    assertThatThrownBy(() -> new FilterPredicate("field", Operator.IS_NULL, "42"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("filter on 'field'")
        .hasMessageContaining("IS_NULL")
        .hasMessageContaining("'42'");
  }

  @Test
  void shouldRequireAValueOnEveryOtherOperator() {
    // when / then — every comparing operator requires a declared value
    assertThatThrownBy(() -> new FilterPredicate("field", Operator.GT, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldSplitAndTrimTheInList() {
    // given the shared param-list convention (comma-separated, trimmed, blanks dropped)
    assertThat(FilterPredicate.in("field", "a, b ,,c").inValues()).containsExactly("a", "b", "c");
  }

  @Test
  void shouldValidateOrderingValuesAsNumbers() {
    // given well-formed bounds in both numeric forms
    FilterPredicate.greaterThan("field", "42").validateValue();
    FilterPredicate.lessOrEqual("field", "41.5").validateValue();
    FilterPredicate.greaterOrEqual("field", "-7").validateValue();

    // when / then — a non-numeric or non-finite bound is rejected with filter context
    for (final String bad : List.of("fast", "", "NaN", "Infinity")) {
      assertThatThrownBy(() -> FilterPredicate.lessThan("field", bad).validateValue())
          .as("bound '%s'", bad)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("filter on 'field'")
          .hasMessageContaining("LT");
    }
  }
}
