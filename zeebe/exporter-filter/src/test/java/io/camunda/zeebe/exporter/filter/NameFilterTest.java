/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NameFilterTest {

  @Test
  void shouldAcceptAllNamesWhenNoRules() {
    // given
    final var filter = new NameFilter(List.of(), List.of());

    // when / then
    assertThat(filter.accept("foo")).isTrue();
    assertThat(filter.accept("bar")).isTrue();
    assertThat(filter.accept("any_name")).isTrue();
  }

  @Test
  void shouldAcceptOnlyNamesMatchingExactInclusion() {
    // given
    final var inclusionRules = List.of(new NameFilterRule(NameFilterRule.Type.EXACT, "foo"));
    final var filter = new NameFilter(inclusionRules, List.of());

    // when / then
    assertThat(filter.accept("foo")).isTrue();
    assertThat(filter.accept("bar")).isFalse();
    assertThat(filter.accept("foobar")).isFalse();
  }

  @Test
  void shouldApplyStartsWithInclusionRule() {
    // given
    final var inclusionRules = List.of(new NameFilterRule(NameFilterRule.Type.STARTS_WITH, "foo"));
    final var filter = new NameFilter(inclusionRules, List.of());

    // when / then
    assertThat(filter.accept("foo")).isTrue();
    assertThat(filter.accept("fooVar")).isTrue();
    assertThat(filter.accept("foobar")).isTrue();

    assertThat(filter.accept("barfoo")).isFalse();
    assertThat(filter.accept("other")).isFalse();
  }

  @Test
  void shouldApplyEndsWithInclusionRule() {
    // given
    final var inclusionRules =
        List.of(new NameFilterRule(NameFilterRule.Type.ENDS_WITH, "_suffix"));
    final var filter = new NameFilter(inclusionRules, List.of());

    // when / then
    assertThat(filter.accept("var_suffix")).isTrue();
    assertThat(filter.accept("foo_suffix")).isTrue();

    assertThat(filter.accept("suffix_foo")).isFalse();
    assertThat(filter.accept("foo")).isFalse();
  }

  @Test
  void shouldRejectNamesMatchingExactExclusionOnly() {
    // given: no inclusion, only exclusion
    final var exclusionRules = List.of(new NameFilterRule(NameFilterRule.Type.EXACT, "secret"));
    final var filter = new NameFilter(List.of(), exclusionRules);

    // when / then
    assertThat(filter.accept("secret")).isFalse();
    assertThat(filter.accept("public")).isTrue();
  }

  @Test
  void shouldRejectNamesMatchingStartsWithExclusionOnly() {
    // given
    final var exclusionRules =
        List.of(new NameFilterRule(NameFilterRule.Type.STARTS_WITH, "debug_"));
    final var filter = new NameFilter(List.of(), exclusionRules);

    // when / then
    assertThat(filter.accept("debug_var")).isFalse();
    assertThat(filter.accept("debug_foo")).isFalse();

    assertThat(filter.accept("nodebug")).isTrue();
    assertThat(filter.accept("prod_var")).isTrue();
  }

  @Test
  void shouldRejectNamesMatchingEndsWithExclusionOnly() {
    // given
    final var exclusionRules = List.of(new NameFilterRule(NameFilterRule.Type.ENDS_WITH, "_tmp"));
    final var filter = new NameFilter(List.of(), exclusionRules);

    // when / then
    assertThat(filter.accept("var_tmp")).isFalse();
    assertThat(filter.accept("foo_tmp")).isFalse();

    assertThat(filter.accept("tmp_var")).isTrue();
    assertThat(filter.accept("var")).isTrue();
  }

  @Test
  void shouldApplyInclusionThenExclusion() {
    // given: name must start with "foo", but anything ending with "_debug" is excluded
    final var inclusionRules = List.of(new NameFilterRule(NameFilterRule.Type.STARTS_WITH, "foo"));
    final var exclusionRules = List.of(new NameFilterRule(NameFilterRule.Type.ENDS_WITH, "_debug"));

    final var filter = new NameFilter(inclusionRules, exclusionRules);

    // when / then
    assertThat(filter.accept("fooVar")).isTrue(); // included, not excluded
    assertThat(filter.accept("foo_debug")).isFalse(); // included AND excluded -> rejected
    assertThat(filter.accept("barVar")).isFalse(); // not included -> rejected early
  }

  @Test
  void shouldShortCircuitOnFailedInclusionEvenIfExclusionWouldAllow() {
    // given: include EXACT "foo"; exclude EXACT "bar"
    final var inclusionRules = List.of(new NameFilterRule(NameFilterRule.Type.EXACT, "foo"));
    final var exclusionRules = List.of(new NameFilterRule(NameFilterRule.Type.EXACT, "bar"));

    final var filter = new NameFilter(inclusionRules, exclusionRules);

    // when / then
    // "baz" does not match inclusion -> immediately false, regardless of exclusion
    assertThat(filter.accept("baz")).isFalse();
  }

  @Test
  void shouldAllowWhenInclusionEmptyAndNotExcluded() {
    // given: no inclusion rules, only one exclusion
    final var exclusionRules = List.of(new NameFilterRule(NameFilterRule.Type.EXACT, "forbidden"));
    final var filter = new NameFilter(List.of(), exclusionRules);

    // when / then
    assertThat(filter.accept("allowed")).isTrue();
    assertThat(filter.accept("forbidden")).isFalse();
  }

  // ------- null-handling tests -------

  @Test
  void shouldAcceptNullName() {
    // given
    final var filter = new NameFilter(List.of(), List.of());

    // when / then
    assertThat(filter.accept(null)).isTrue();
  }

  @Test
  void shouldHandleNullInclusionAndExclusionLists() {
    // given
    final var filter = new NameFilter(null, null);

    // when / then
    // With no rules at all, non-null names are accepted
    assertThat(filter.accept("foo")).isTrue();
    assertThat(filter.accept("bar")).isTrue();
  }

  @Test
  void shouldIgnoreNullRulesInLists() {
    // given: lists containing null entries must not cause NPE
    final var validRule = new NameFilterRule(NameFilterRule.Type.EXACT, "foo");
    final var inclusionRules = Arrays.asList(null, validRule);
    final var exclusionRules = Collections.singletonList((NameFilterRule) null);

    final var filter = new NameFilter(inclusionRules, exclusionRules);

    // when / then
    assertThat(filter.accept("foo")).isTrue();
    assertThat(filter.accept("bar")).isFalse();
  }
}
