/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.FilterPredicate.Operator;
import io.camunda.analytics.dimension.Utf8View;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the semantics of the non-equality operators (the {@link FilterPredicate} javadoc table):
 * ordering matches numbers only, {@code IN} is membership by canonical rendering (typed for
 * numerics, byte-wise for UTF-8-carried strings), presence checks see the null bucket. Equality
 * parity stays pinned by {@link CompiledFilterTest} / {@link CompiledFilterUtf8Test}.
 */
final class CompiledFilterOperatorsTest {

  /** One semantics row: a field value meeting {@code operator}/{@code filterValue}. */
  private record Case(Object fieldValue, Operator operator, String filterValue, boolean expected) {}

  private static final List<Case> ORDERING_CASES =
      List.of(
          // integral field vs integral bound: one exact long compare
          new Case(41L, Operator.LT, "42", true),
          new Case(42L, Operator.LT, "42", false),
          new Case(42L, Operator.LE, "42", true),
          new Case(43L, Operator.LE, "42", false),
          new Case(43L, Operator.GT, "42", true),
          new Case(42L, Operator.GT, "42", false),
          new Case(42L, Operator.GE, "42", true),
          new Case(41L, Operator.GE, "42", false),
          new Case(-8L, Operator.LT, "-7", true),
          // the other integral carriers compare the same way
          new Case(42, Operator.GE, "42", true),
          new Case((short) 41, Operator.LT, "42", true),
          new Case((byte) 7, Operator.GT, "42", false),
          // double field vs integral bound compares as doubles
          new Case(41.5d, Operator.LT, "42", true),
          new Case(42.5d, Operator.GT, "42", true),
          new Case(42.0d, Operator.GE, "42", true),
          new Case(42.0f, Operator.LE, "42", true),
          // fractional bound (decimal point): parsed once to a double
          new Case(42L, Operator.GT, "41.5", true),
          new Case(41L, Operator.GT, "41.5", false),
          new Case(41.4d, Operator.LT, "41.5", true),
          // non-canonical but parseable integral bounds still order ("+42", "042")
          new Case(43L, Operator.GT, "+42", true),
          new Case(41L, Operator.LT, "042", true),
          // a non-numeric field value never matches — even a numeric-looking string
          new Case("43", Operator.GT, "42", false),
          new Case("41", Operator.LT, "42", false),
          new Case(true, Operator.GT, "0", false),
          // an unparseable declared bound never matches (validation rejects it at admission)
          new Case(42L, Operator.GT, "fast", false),
          new Case(42L, Operator.LT, "fast", false));

  private static final List<Case> IN_CASES =
      List.of(
          // string field: plain membership
          new Case("invoice", Operator.IN, "invoice,order", true),
          new Case("credit", Operator.IN, "invoice,order", false),
          // elements are trimmed (the shared param-list convention)
          new Case("order", Operator.IN, "invoice, order", true),
          new Case("order", Operator.IN, " order ", true),
          // integral field: typed membership over the canonical-long elements
          new Case(42L, Operator.IN, "41,42,43", true),
          new Case(40L, Operator.IN, "41,42,43", false),
          new Case(42, Operator.IN, "41, 42", true),
          new Case(-7L, Operator.IN, "-7", true),
          // a non-canonical element never equals a canonical rendering...
          new Case(42L, Operator.IN, "042", false),
          // ...but matches the string field carrying it verbatim
          new Case("042", Operator.IN, "042", true),
          // boolean and double fields: membership by their canonical rendering
          new Case(true, Operator.IN, "true,false", true),
          new Case(false, Operator.IN, "true", false),
          new Case(42.5d, Operator.IN, "42.5", true),
          new Case(42.0d, Operator.IN, "42", false));

  @Test
  void shouldOrderNumericFieldsAgainstThePreParsedBound() {
    for (final Case row : ORDERING_CASES) {
      // given / when / then — see the semantics table
      assertThat(matches(row.operator(), row.filterValue(), row.fieldValue()))
          .as("ordering semantics of %s", row)
          .isEqualTo(row.expected());
    }
  }

  @Test
  void shouldNeverMatchOrderingOnAnAbsentField() {
    // given a fact without the filtered field (the null bucket)
    final Fact fact = factWithoutField();

    // when / then — an absent field never satisfies any ordering operator
    for (final Operator operator : List.of(Operator.LT, Operator.LE, Operator.GT, Operator.GE)) {
      assertThat(new CompiledFilter(new FilterPredicate("field", operator, "42")).matches(fact))
          .as("absent field under %s", operator)
          .isFalse();
    }
  }

  @Test
  void shouldMatchListMembershipByCanonicalRendering() {
    for (final Case row : IN_CASES) {
      // given / when / then — see the semantics table
      assertThat(matches(row.operator(), row.filterValue(), row.fieldValue()))
          .as("IN semantics of %s", row)
          .isEqualTo(row.expected());
    }
  }

  @Test
  void shouldMatchAUtf8ViewFieldInAListExactlyLikeTheStringField() {
    // given string-carried and byte-carried forms of the same field values
    final List<Case> parity =
        List.of(
            new Case("invoice", Operator.IN, "invoice,order", true),
            new Case("credit", Operator.IN, "invoice,order", false),
            new Case("Ω-café-日本-🚀", Operator.IN, "Ω-café-日本-🚀,other", true),
            new Case("Ω-café-日本-🚁", Operator.IN, "Ω-café-日本-🚀,other", false),
            new Case("order", Operator.IN, "invoice, order", true));

    for (final Case row : parity) {
      // when the same logical value meets the pre-encoded member set as String and as Utf8View
      final boolean asString = matches(row.operator(), row.filterValue(), row.fieldValue());
      final boolean asView =
          matches(row.operator(), row.filterValue(), Utf8View.of((String) row.fieldValue()));

      // then the byte-wise probe agrees with the string probe (and the expected outcome)
      assertThat(asView).as("Utf8View parity of %s", row).isEqualTo(asString);
      assertThat(asView).isEqualTo(row.expected());
    }
  }

  @Test
  void shouldNeverMatchListMembershipOnAnAbsentField() {
    // given a fact without the filtered field
    final Fact fact = factWithoutField();

    // when / then — absent never matches IN
    assertThat(new CompiledFilter(FilterPredicate.in("field", "a,b")).matches(fact)).isFalse();
  }

  @Test
  void shouldCheckFieldPresence() {
    // given a fact with the field (in each carrier) and one without it
    final Fact absent = factWithoutField();
    final List<Object> present = List.of("invoice", Utf8View.of("invoice"), 42L, true, 42.5d);

    // when / then — IS_NULL matches exactly the null bucket, NOT_NULL exactly its complement
    final CompiledFilter isNull = new CompiledFilter(FilterPredicate.isNull("field"));
    final CompiledFilter notNull = new CompiledFilter(FilterPredicate.notNull("field"));
    assertThat(isNull.matches(absent)).isTrue();
    assertThat(notNull.matches(absent)).isFalse();
    for (final Object fieldValue : present) {
      assertThat(isNull.matches(fact(fieldValue))).as("IS_NULL on %s", fieldValue).isFalse();
      assertThat(notNull.matches(fact(fieldValue))).as("NOT_NULL on %s", fieldValue).isTrue();
    }
  }

  private static boolean matches(
      final Operator operator, final String filterValue, final Object fieldValue) {
    return new CompiledFilter(new FilterPredicate("field", operator, filterValue))
        .matches(fact(fieldValue));
  }

  private static Fact fact(final Object fieldValue) {
    return Fact.builder(FactType.PROCESS_INSTANCE)
        .field("field", fieldValue)
        .eventTime(1L)
        .source(1, 1L)
        .build();
  }

  private static Fact factWithoutField() {
    return Fact.builder(FactType.PROCESS_INSTANCE).eventTime(1L).source(1, 1L).build();
  }
}
