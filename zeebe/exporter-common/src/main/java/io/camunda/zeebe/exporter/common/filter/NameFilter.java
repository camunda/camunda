/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.filter;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

final class NameFilter {

  private final List<Predicate<String>> includePredicates;
  private final List<Predicate<String>> excludePredicates;

  NameFilter(final List<NameRule> inclusionRules, final List<NameRule> exclusionRules) {

    includePredicates = toPredicates(inclusionRules);
    excludePredicates = toPredicates(exclusionRules);
  }

  boolean test(final String name) {
    if (!includePredicates.isEmpty() && includePredicates.stream().noneMatch(p -> p.test(name))) {
      return false;
    }

    return excludePredicates.isEmpty() || excludePredicates.stream().noneMatch(p -> p.test(name));
  }

  private static List<Predicate<String>> toPredicates(final List<NameRule> rules) {
    return rules.stream().map(NameFilter::toPredicate).toList();
  }

  private static Predicate<String> toPredicate(final NameRule rule) {
    final String pattern = rule.pattern();

    return switch (rule.type()) {
      case EXACT -> name -> name.equals(pattern);

      case STARTS_WITH -> name -> name.startsWith(pattern);

      case ENDS_WITH -> name -> name.endsWith(pattern);

      case REGEX -> {
        final Pattern compiled = Pattern.compile(pattern);
        yield name -> compiled.matcher(name).matches();
      }
    };
  }
}
