/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import com.google.common.base.Equivalence;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The #{@link IndexMappingDifference} denotes the differences between two Index mappings.
 * Typically, the left mapping is the new mapping and the right one is the existing mapping.
 *
 * @param equal indicates whether the two mappings are the same
 * @param entriesOnlyOnLeft properties which only exist in the left mapping
 * @param entriesOnlyOnRight properties which only exist in the right mapping
 * @param entriesInCommon properties which exist in both mappings
 * @param entriesDiffering properties which exist in both mappings but with different exact values
 */
public record IndexMappingDifference(
    boolean equal,
    Set<IndexMappingProperty> entriesOnlyOnLeft,
    Set<IndexMappingProperty> entriesOnlyOnRight,
    Set<IndexMappingProperty> entriesInCommon,
    Set<PropertyDifference> entriesDiffering) {

  public static IndexMappingDifference of(final IndexMapping left, final IndexMapping right) {
    final Map<String, Object> leftMap = left == null ? Map.of() : left.toMap();
    final Map<String, Object> rightMap = right == null ? Map.of() : right.toMap();
    final MapDifference<String, Object> difference =
        Maps.difference(leftMap, rightMap, OrderInsensitiveEquivalence.equals());
    return new IndexMappingDifference(
        difference.areEqual(),
        difference.entriesOnlyOnLeft().entrySet().stream()
            .map(IndexMappingProperty::createIndexMappingProperty)
            .collect(Collectors.toSet()),
        difference.entriesOnlyOnRight().entrySet().stream()
            .map(IndexMappingProperty::createIndexMappingProperty)
            .collect(Collectors.toSet()),
        difference.entriesInCommon().entrySet().stream()
            .map(IndexMappingProperty::createIndexMappingProperty)
            .collect(Collectors.toSet()),
        difference.entriesDiffering().entrySet().stream()
            .map(
                entry ->
                    new PropertyDifference(
                        entry.getKey(),
                        new IndexMappingProperty.Builder()
                            .name(entry.getKey())
                            .typeDefinition(entry.getValue().leftValue())
                            .build(),
                        new IndexMappingProperty.Builder()
                            .name(entry.getKey())
                            .typeDefinition(entry.getValue().rightValue())
                            .build()))
            .collect(Collectors.toSet()));
  }

  public IndexMappingDifference filterEntriesDiffering(final Predicate<PropertyDifference> filter) {
    return new IndexMappingDifference(
        equal,
        entriesOnlyOnLeft,
        entriesOnlyOnRight,
        entriesInCommon,
        entriesDiffering.stream().filter(filter).collect(Collectors.toSet()));
  }

  public IndexMappingDifference filterEntriesInCommon(
      final Predicate<IndexMappingProperty> filter) {
    return new IndexMappingDifference(
        equal,
        entriesOnlyOnLeft,
        entriesOnlyOnRight,
        entriesInCommon.stream().filter(filter).collect(Collectors.toSet()),
        entriesDiffering);
  }

  /**
   * A recursive equivalence for comparing Elasticsearch/Opensearch index mappings, specifically
   * required to handle nested structures and ignore list ordering.
   *
   * <p>The default({@link com.google.common.base.Equivalence.Equals#equals(Object)}) is
   * insufficient for our use case because:
   *
   * <ul>
   *   <li>Elasticsearch mappings include deeply nested structures (e.g., nested fields, join
   *       relations).
   *   <li>Some fields, such as "relations" in join mappings, represent lists where element order
   *       does not affect semantics (example, ["task", "variable"] vs ["variable", "task"]).
   *   <li>Default map or list equality in Java considers order, causing false positives in diffs.
   * </ul>
   *
   * <p>This custom equivalence:
   *
   * <ul>
   *   <li>Recursively traverses maps and lists in the mapping structure.
   *   <li>Treats lists as sets (acutally as multisets to compare also compare duplicate elements)
   *       to ignore ordering or handle duplicates appropriately.
   *   <li>Ensures that mappings with semantically identical structures (despite ordering
   *       differences) are treated as equivalent.
   * </ul>
   */
  @VisibleForTesting
  public static final class OrderInsensitiveEquivalence extends Equivalence<Object> {

    private static final OrderInsensitiveEquivalence INSTANCE = new OrderInsensitiveEquivalence();

    private OrderInsensitiveEquivalence() {}

    public static OrderInsensitiveEquivalence equals() {
      return INSTANCE;
    }

    @Override
    protected boolean doEquivalent(final Object a, final Object b) {
      return Objects.equals(canonicalize(a), canonicalize(b));
    }

    @Override
    protected int doHash(final Object o) {
      return Objects.hashCode(canonicalize(o));
    }

    private Object canonicalize(final Object o) {
      if (o instanceof final Map<?, ?> map) {
        return map.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> canonicalize(e.getValue())));
      } else if (o instanceof final Collection<?> collection) {
        return HashMultiset.create(collection.stream().map(this::canonicalize).toList());
      } else if (o instanceof final Object[] array) {
        return HashMultiset.create(Arrays.stream(array).map(this::canonicalize).toList());
      } else {
        return o;
      }
    }
  }

  record PropertyDifference(
      String name, IndexMappingProperty leftValue, IndexMappingProperty rightValue) {}
}
