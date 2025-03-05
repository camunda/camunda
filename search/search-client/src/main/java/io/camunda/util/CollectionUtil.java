/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class CollectionUtil {

  private CollectionUtil() {}

  public static <A> List<A> withoutNull(final A[] items) {
    if (items != null) {
      return withoutNull(Arrays.asList(items));
    }
    return null;
  }

  public static <A> List<A> withoutNull(final Collection<A> items) {
    if (items != null) {
      return items.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }
    return null;
  }

  /**
   * Adds given values to a list.
   *
   * <p>This method can handle null-values gracefully.
   *
   * <p>This means, if either one of the given list is empty, and empty ArrayList will be used
   * instead.
   *
   * <p>Example:
   *
   * <p>* addValuesToList(null, List.of(1)) -> List(1) * addValuesToList(List.of(1), null) ->
   * List(1) * addValuesToList(null, null) -> List() * addValuesToList(List.of(1), List.of(2)) ->
   * List(1, 2)
   *
   * @param list where the values should be added to
   * @param values the values which should be added
   * @return the given list (when not empty) otherwise a new list containing the values
   * @param <T> the list value type
   */
  public static <T> List<T> addValuesToList(final List<T> list, final List<T> values) {
    final List<T> result = Objects.requireNonNullElse(list, new ArrayList<>());
    result.addAll(Objects.requireNonNullElse(values, new ArrayList<>()));
    return result;
  }

  /**
   * Adds given values to a set.
   *
   * <p>This method can handle null-values gracefully.
   *
   * <p>This means, if either one of the given set is empty, and empty HashSet will be used instead.
   *
   * <p>Example:
   *
   * <p>* addValuesToSet(null, Set.of(1)) -> Set(1) * addValuesToSet(Set.of(1), null) -> Set(1) *
   * addValuesToSet(null, null) -> Set() * addValuesToSet(Set.of(1), Set.of(2)) -> Set(1, 2)
   *
   * @param set where the values should be added to
   * @param values the values which should be added
   * @return the given set (when not empty) otherwise a new set containing the values
   * @param <T> the set value type
   */
  public static <T> Set<T> addValuesToSet(final Set<T> set, final Collection<T> values) {
    final Set<T> result = Objects.requireNonNullElse(set, new HashSet<>());
    result.addAll(Objects.requireNonNullElse(values, new HashSet<>()));
    return result;
  }

  /**
   * Collects a given values (array) as list, and handles potential null or empty values.
   *
   * @param values the values that needs to be collected
   * @return an appropriate list containing the values
   * @param <T> the type of the values
   */
  public static <T> List<T> collectValuesAsList(final T... values) {
    if (values == null) {
      return List.of();
    }

    return Arrays.stream(values).toList();
  }

  public static <T> List<T> collectValues(final T value, final T... values) {
    final List<T> collectedValues = new ArrayList<>();
    collectedValues.add(value);
    if (values != null && values.length > 0) {
      collectedValues.addAll(Arrays.asList(values));
    }
    return collectedValues;
  }
}
