/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class ConfigValidation {

  public static Predicate<String> skipEmpty = s -> s == null || s.isEmpty();

  private ConfigValidation() {}

  public static <A> Predicate<Optional<A>> skipEmptyOptional() {
    return Optional::isEmpty;
  }

  public static <A> A allMatch(
      final Supplier<A> whenEmpty,
      final Function<Map<String, A>, String> messageIfDifferent,
      final Map<String, A> props) {
    return allMatch(whenEmpty, messageIfDifferent, props, (ignored) -> false);
  }

  public static <A> A allMatch(
      final Supplier<A> whenEmpty,
      final Function<Map<String, A>, String> messageIfDifferent,
      final Map<String, A> props,
      final Predicate<A> skip) {
    return allMatchHaving(whenEmpty, messageIfDifferent, props, Function.identity(), skip);
  }

  public static <From, To> From allMatchHaving(
      final Supplier<From> whenEmpty,
      final Function<Map<String, To>, String> messageIfDifferent,
      final Map<String, From> props,
      final Function<From, To> extract,
      final Predicate<To> skip) {
    final var filtered = props.values().stream().filter(a -> !skip.test(extract.apply(a))).toList();

    if (filtered.isEmpty()) {
      return whenEmpty.get();
    }
    final From firstPrefix = filtered.getFirst();
    if (filtered.stream().map(extract).allMatch(p -> p.equals(extract.apply(firstPrefix)))) {
      return firstPrefix;
    } else {
      final Map<String, To> mappedProps =
          props.entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> extract.apply(e.getValue())));
      throw new IllegalArgumentException(messageIfDifferent.apply(mappedProps));
    }
  }
}
