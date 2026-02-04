/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import io.camunda.zeebe.exporter.filter.NameFilterRule.Type;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

final class NameFilter {

  private final List<Predicate<String>> includePredicates;
  private final List<Predicate<String>> excludePredicates;

  NameFilter(final List<NameFilterRule> inclusionRules, final List<NameFilterRule> exclusionRules) {
    includePredicates = toPredicates(inclusionRules);
    excludePredicates = toPredicates(exclusionRules);
  }

  boolean accept(final String name) {
    if (name == null) {
      return true;
    }

    if (!includePredicates.isEmpty() && includePredicates.stream().noneMatch(p -> p.test(name))) {
      return false;
    }

    return excludePredicates.isEmpty() || excludePredicates.stream().noneMatch(p -> p.test(name));
  }

  private static List<Predicate<String>> toPredicates(final List<NameFilterRule> rules) {
    if (rules == null || rules.isEmpty()) {
      return List.of();
    }
    return rules.stream().filter(Objects::nonNull).map(NameFilter::toPredicate).toList();
  }

  private static Predicate<String> toPredicate(final NameFilterRule rule) {
    final String pattern = rule.pattern();

    return switch (rule.type()) {
      case Type.EXACT -> name -> name.equals(pattern);
      case Type.STARTS_WITH -> name -> name.startsWith(pattern);
      case Type.ENDS_WITH -> name -> name.endsWith(pattern);
    };
  }
}
