/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

final class NameFilterTest {

  @Test
  void shouldAcceptAllNamesWhenNoRules() {
    // given
    final var filter = new NameFilter(List.of(), List.of());

    // when / then
    assertThat(filter.test("foo")).isTrue();
    assertThat(filter.test("bar")).isTrue();
    assertThat(filter.test("any_name")).isTrue();
  }

  @Test
  void shouldAcceptOnlyNamesMatchingExactInclusion() {
    // given
    final var inclusionRules = List.of(new NameRule(NameRule.Type.EXACT, "foo"));
    final var filter = new NameFilter(inclusionRules, List.of());

    // when / then
    assertThat(filter.test("foo")).isTrue();
    assertThat(filter.test("bar")).isFalse();
    assertThat(filter.test("foobar")).isFalse();
  }

  @Test
  void shouldApplyStartsWithInclusionRule() {
    // given
    final var inclusionRules = List.of(new NameRule(NameRule.Type.STARTS_WITH, "foo"));
    final var filter = new NameFilter(inclusionRules, List.of());

    // when / then
    assertThat(filter.test("foo")).isTrue();
    assertThat(filter.test("fooVar")).isTrue();
    assertThat(filter.test("foobar")).isTrue();

    assertThat(filter.test("barfoo")).isFalse();
    assertThat(filter.test("other")).isFalse();
  }

  @Test
  void shouldApplyEndsWithInclusionRule() {
    // given
    final var inclusionRules = List.of(new NameRule(NameRule.Type.ENDS_WITH, "_suffix"));
    final var filter = new NameFilter(inclusionRules, List.of());

    // when / then
    assertThat(filter.test("var_suffix")).isTrue();
    assertThat(filter.test("foo_suffix")).isTrue();

    assertThat(filter.test("suffix_foo")).isFalse();
    assertThat(filter.test("foo")).isFalse();
  }

  @Test
  void shouldRejectWhenInclusionRulesPresentAndNoneMatch() {
    // given
    final var inclusionRules = List.of(new NameRule(NameRule.Type.EXACT, "allowed"));
    final var filter = new NameFilter(inclusionRules, List.of());

    // when / then
    assertThat(filter.test("other")).isFalse();
    assertThat(filter.test("allowedX")).isFalse();
  }

  @Test
  void shouldRejectNamesMatchingExactExclusionOnly() {
    // given: no inclusion, only exclusion
    final var exclusionRules = List.of(new NameRule(NameRule.Type.EXACT, "secret"));
    final var filter = new NameFilter(List.of(), exclusionRules);

    // when / then
    assertThat(filter.test("secret")).isFalse();
    assertThat(filter.test("public")).isTrue();
  }

  @Test
  void shouldRejectNamesMatchingStartsWithExclusionOnly() {
    // given
    final var exclusionRules = List.of(new NameRule(NameRule.Type.STARTS_WITH, "debug_"));
    final var filter = new NameFilter(List.of(), exclusionRules);

    // when / then
    assertThat(filter.test("debug_var")).isFalse();
    assertThat(filter.test("debug_foo")).isFalse();

    assertThat(filter.test("nodebug")).isTrue();
    assertThat(filter.test("prod_var")).isTrue();
  }

  @Test
  void shouldRejectNamesMatchingEndsWithExclusionOnly() {
    // given
    final var exclusionRules = List.of(new NameRule(NameRule.Type.ENDS_WITH, "_tmp"));
    final var filter = new NameFilter(List.of(), exclusionRules);

    // when / then
    assertThat(filter.test("var_tmp")).isFalse();
    assertThat(filter.test("foo_tmp")).isFalse();

    assertThat(filter.test("tmp_var")).isTrue();
    assertThat(filter.test("var")).isTrue();
  }

  @Test
  void shouldApplyInclusionThenExclusion() {
    // given: name must start with "foo", but anything ending with "_debug" is excluded
    final var inclusionRules = List.of(new NameRule(NameRule.Type.STARTS_WITH, "foo"));
    final var exclusionRules = List.of(new NameRule(NameRule.Type.ENDS_WITH, "_debug"));

    final var filter = new NameFilter(inclusionRules, exclusionRules);

    // when / then
    assertThat(filter.test("fooVar")).isTrue(); // included, not excluded
    assertThat(filter.test("foo_debug")).isFalse(); // included AND excluded -> rejected
    assertThat(filter.test("barVar")).isFalse(); // not included -> rejected early
  }

  @Test
  void shouldShortCircuitOnFailedInclusionEvenIfExclusionWouldAllow() {
    // given: include EXACT "foo"; exclude EXACT "bar"
    final var inclusionRules = List.of(new NameRule(NameRule.Type.EXACT, "foo"));
    final var exclusionRules = List.of(new NameRule(NameRule.Type.EXACT, "bar"));

    final var filter = new NameFilter(inclusionRules, exclusionRules);

    // when / then
    // "baz" does not match inclusion -> immediately false, regardless of exclusion
    assertThat(filter.test("baz")).isFalse();
  }

  @Test
  void shouldAllowWhenInclusionEmptyAndNotExcluded() {
    // given: no inclusion rules, only one exclusion
    final var exclusionRules = List.of(new NameRule(NameRule.Type.EXACT, "forbidden"));
    final var filter = new NameFilter(List.of(), exclusionRules);

    // when / then
    assertThat(filter.test("allowed")).isTrue();
    assertThat(filter.test("forbidden")).isFalse();
  }
}
