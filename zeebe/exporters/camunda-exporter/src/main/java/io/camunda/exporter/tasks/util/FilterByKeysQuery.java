/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class that represents a fairly simple form of search query.
 *
 * <p>Typically used when we want to find document that have a known set of key values, but where
 * there might be multiple options for the fields those keys are part of
 *
 * @param keysByField a map of fields -> ids used to form the basic query
 * @param inclusionFilters extra filters that much be applied
 * @param exclusionFilters filter to exclude results
 */
public record FilterByKeysQuery<T>(
    Map<String, List<T>> keysByField,
    Map<String, String> inclusionFilters,
    Map<String, String> exclusionFilters) {

  private FilterByKeysQuery(final Map<String, List<T>> keysByField) {
    this(keysByField, Map.of(), Map.of());
  }

  private FilterByKeysQuery(final String keyFieldName, final List<T> keyValues) {
    this(Map.of(keyFieldName, keyValues));
  }

  public static <T> FilterByKeysQuery<T> of(final Map<String, List<T>> keysByField) {
    return new FilterByKeysQuery<>(keysByField);
  }

  public static <T> FilterByKeysQuery<T> of(final String keyFieldName, final List<T> keyValues) {
    return new FilterByKeysQuery<>(keyFieldName, keyValues);
  }

  public FilterByKeysQuery<T> inclusion(final String fieldName, final String fieldValue) {
    return inclusions(Map.of(fieldName, fieldValue));
  }

  public FilterByKeysQuery<T> inclusions(final Map<String, String> inclusionFilters) {
    return new FilterByKeysQuery<>(
        keysByField, addToMap(this.inclusionFilters, inclusionFilters), exclusionFilters);
  }

  public FilterByKeysQuery<T> exclusion(final String fieldName, final String fieldValue) {
    return exclusions(Map.of(fieldName, fieldValue));
  }

  public FilterByKeysQuery<T> exclusions(final Map<String, String> exclusionFilters) {
    return new FilterByKeysQuery<>(
        keysByField, inclusionFilters, addToMap(this.exclusionFilters, exclusionFilters));
  }

  private Map<String, String> addToMap(
      final Map<String, String> existing, final Map<String, String> extra) {
    final var newMap = new HashMap<>(existing);
    newMap.putAll(extra);
    return newMap;
  }
}
