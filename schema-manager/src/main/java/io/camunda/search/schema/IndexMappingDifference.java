/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import com.google.common.base.Equivalence;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
 * @param isLeftDynamic true if the left mapping is dynamic
 * @param isRightDynamic true if the right mapping is dynamic
 */
public record IndexMappingDifference(
    boolean equal,
    Set<IndexMappingProperty> entriesOnlyOnLeft,
    Set<IndexMappingProperty> entriesOnlyOnRight,
    Set<IndexMappingProperty> entriesInCommon,
    Set<PropertyDifference> entriesDiffering,
    boolean isLeftDynamic,
    boolean isRightDynamic) {

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
            .collect(Collectors.toSet()),
        left == null ? false : left.isDynamic(),
        right == null ? false : right.isDynamic());
  }

  private record PropertyDifference(
      String name, IndexMappingProperty leftValue, IndexMappingProperty rightValue) {}

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
  private static final class OrderInsensitiveEquivalence extends Equivalence<Object> {

    private static final OrderInsensitiveEquivalence INSTANCE = new OrderInsensitiveEquivalence();

    public static OrderInsensitiveEquivalence equals() {
      return INSTANCE;
    }

    @Override
    protected boolean doEquivalent(final Object a, final Object b) {
      if (a == b) {
        return true;
      }
      if (a == null || b == null) {
        return false;
      }

      if (a instanceof final Map<?, ?> mapA && b instanceof final Map<?, ?> mapB) {
        if (mapA.size() != mapB.size()) {
          return false;
        }
        for (final Object key : mapA.keySet()) {
          if (!mapB.containsKey(key) || !equivalent(mapA.get(key), mapB.get(key))) {
            return false;
          }
        }
        return true;
      }

      // Handle lists as multisets to ignore order and count duplicates
      if (a instanceof final List<?> listA && b instanceof final List<?> listB) {
        if (listA.size() != listB.size()) {
          return false;
        }

        final var countA = countElements(listA);
        final var countB = countElements(listB);
        return countA.equals(countB);
      }

      return a.equals(b);
    }

    @Override
    protected int doHash(final Object o) {
      if (o instanceof final Map<?, ?> oMap) {
        return oMap.entrySet().stream()
            .mapToInt(entry -> Objects.hash(entry.getKey(), doHash(entry.getValue())))
            .sum();
      } else if (o instanceof final List<?> oList) {
        // Use a multiset-like approach to handle duplicates and ignore order
        return oList.stream().mapToInt(this::doHash).sum();
      } else {
        return Objects.hashCode(o);
      }
    }

    private Map<Object, Long> countElements(final List<?> list) {
      return list.stream()
          .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
  }
}
