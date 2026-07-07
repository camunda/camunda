/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dimension.Utf8View;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Parity of the byte compare for UTF-8-carried string fields (ADR 0008): a {@link Utf8View} field
 * must match exactly like the same value carried as a {@code String} — which itself matches the
 * reference semantics {@code String.valueOf(value).equals(filter.value())} (pinned by {@link
 * CompiledFilterTest}).
 */
final class CompiledFilterUtf8Test {

  private record Case(String fieldValue, String filterValue) {}

  private static final List<Case> PARITY_CASES =
      List.of(
          new Case("order", "order"),
          new Case("order", "ORDER"),
          new Case("42", "42"),
          new Case("", ""),
          new Case(" ", " "),
          new Case("Ω-café-日本-🚀", "Ω-café-日本-🚀"),
          new Case("Ω-café-日本-🚀", "Ω-café-日本-🚁"),
          new Case("ab", "ab"));

  @Test
  void shouldMatchAUtf8ViewFieldExactlyLikeTheStringField() {
    for (final Case parity : PARITY_CASES) {
      // given the same logical value carried as a String and as its UTF-8 view
      final Fact asString = fact(parity.fieldValue());
      final Fact asView = fact(Utf8View.of(parity.fieldValue()));

      for (final FilterPredicate predicate :
          List.of(
              FilterPredicate.equals("field", parity.filterValue()),
              FilterPredicate.notEquals("field", parity.filterValue()))) {
        // when both meet the pre-encoded filter
        final CompiledFilter filter = new CompiledFilter(predicate);

        // then the byte compare agrees with the string compare
        assertThat(filter.matches(asView))
            .as("parity of %s under %s", parity, predicate)
            .isEqualTo(filter.matches(asString));
      }
    }
  }

  private static Fact fact(final Object fieldValue) {
    return Fact.builder(FactType.PROCESS_INSTANCE)
        .field("field", fieldValue)
        .eventTime(1L)
        .source(1, 1L)
        .build();
  }
}
