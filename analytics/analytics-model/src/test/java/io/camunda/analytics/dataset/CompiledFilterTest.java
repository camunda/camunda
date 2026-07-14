/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves the pre-typed comparison keeps exact equality parity with the reference semantics {@code
 * String.valueOf(value).equals(filter.value())} across the typed fast paths and their fallbacks.
 */
final class CompiledFilterTest {

  /** A field value paired with a filter value, checked against the reference semantics. */
  private record Case(Object fieldValue, String filterValue) {}

  private static final List<Case> PARITY_CASES =
      List.of(
          // typed numeric fast path: long/int field values against a canonical numeric filter
          new Case(42L, "42"),
          new Case(42, "42"),
          new Case(43L, "42"),
          new Case(-7L, "-7"),
          // non-canonical numeric filter values must fall back to the string compare
          new Case(42L, "042"),
          new Case(42L, "+42"),
          new Case(42L, "42 "),
          // typed boolean fast path and its strict-canonical fallback
          new Case(true, "true"),
          new Case(false, "true"),
          new Case(true, "TRUE"),
          new Case(false, "false"),
          // strings compare as before, without a String.valueOf copy
          new Case("42", "42"),
          new Case("order", "order"),
          new Case("order", "ORDER"),
          // types with no fast path fall back to the reference compare
          new Case(42.0d, "42"),
          new Case(42.0d, "42.0"));

  @Test
  void shouldMatchExactlyLikeTheReferenceStringComparison() {
    for (final Case parity : PARITY_CASES) {
      // given the reference (pre-compilation) semantics for both operators
      final boolean referenceEqual =
          String.valueOf(parity.fieldValue()).equals(parity.filterValue());

      // when the same value meets the pre-typed filter
      final Fact fact = fact(parity.fieldValue());
      final boolean equalsMatch =
          new CompiledFilter(FilterPredicate.equals("field", parity.filterValue())).matches(fact);
      final boolean notEqualsMatch =
          new CompiledFilter(FilterPredicate.notEquals("field", parity.filterValue()))
              .matches(fact);

      // then EQUALS and NOT_EQUALS agree with the reference
      assertThat(equalsMatch).as("EQUALS parity for %s", parity).isEqualTo(referenceEqual);
      assertThat(notEqualsMatch).as("NOT_EQUALS parity for %s", parity).isEqualTo(!referenceEqual);
    }
  }

  @Test
  void shouldTreatAnAbsentFieldAsNeverEqual() {
    // given a fact without the filtered field (absent and null coincide in a Fact)
    final Fact fact = Fact.builder(FactType.PROCESS_INSTANCE).eventTime(1L).source(1, 1L).build();

    // when / then — EQUALS never matches, NOT_EQUALS always does (the reference null semantics)
    assertThat(new CompiledFilter(FilterPredicate.equals("field", "42")).matches(fact)).isFalse();
    assertThat(new CompiledFilter(FilterPredicate.notEquals("field", "42")).matches(fact)).isTrue();
  }

  private static Fact fact(final Object fieldValue) {
    return Fact.builder(FactType.PROCESS_INSTANCE)
        .field("field", fieldValue)
        .eventTime(1L)
        .source(1, 1L)
        .build();
  }
}
