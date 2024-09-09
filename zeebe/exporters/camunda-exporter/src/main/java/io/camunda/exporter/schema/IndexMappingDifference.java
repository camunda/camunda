/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The #{@link IndexMappingDifference} will be built using the builder where the left and right are
 * two index mappings, and after it is built it will result in a record where
 *
 * @param equal is whether the two mappings are the same
 * @param entriesOnlyOnLeft properties which only exist in the left mapping
 * @param entriesOnlyOnRight properties which only exist in the right mapping
 * @param entriesInCommon properties which exist in both mappings
 * @param entriesDiffering properties which exist in both mappings but with different exact values
 * @param leftIndexMapping the left mapping to compare
 * @param rightIndexMapping the right mapping to compare
 */
public record IndexMappingDifference(
    boolean equal,
    Set<IndexMappingProperty> entriesOnlyOnLeft,
    Set<IndexMappingProperty> entriesOnlyOnRight,
    Set<IndexMappingProperty> entriesInCommon,
    Set<PropertyDifference> entriesDiffering,
    IndexMapping leftIndexMapping,
    IndexMapping rightIndexMapping) {

  public static class IndexMappingDifferenceBuilder {
    private IndexMapping left;
    private IndexMapping right;

    public static IndexMappingDifferenceBuilder builder() {
      return new IndexMappingDifferenceBuilder();
    }

    public IndexMappingDifferenceBuilder setLeft(final IndexMapping left) {
      this.left = left;
      return this;
    }

    public IndexMappingDifferenceBuilder setRight(final IndexMapping right) {
      this.right = right;
      return this;
    }

    public IndexMappingDifference build() {
      final Map<String, Object> leftMap = left == null ? Map.of() : left.toMap();
      final Map<String, Object> rightMap = right == null ? Map.of() : right.toMap();
      final MapDifference<String, Object> difference = Maps.difference(leftMap, rightMap);
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
                      new PropertyDifference.Builder()
                          .name(entry.getKey())
                          .leftValue(
                              new IndexMappingProperty.Builder()
                                  .name(entry.getKey())
                                  .typeDefinition(entry.getValue().leftValue())
                                  .build())
                          .rightValue(
                              new IndexMappingProperty.Builder()
                                  .name(entry.getKey())
                                  .typeDefinition(entry.getValue().rightValue())
                                  .build())
                          .build())
              .collect(Collectors.toSet()),
          left,
          right);
    }
  }

  private record PropertyDifference(
      String name, IndexMappingProperty leftValue, IndexMappingProperty rightValue) {

    private static final class Builder {
      private String name;
      private IndexMappingProperty leftValue;
      private IndexMappingProperty rightValue;

      private Builder name(final String name) {
        this.name = name;
        return this;
      }

      private Builder leftValue(final IndexMappingProperty leftValue) {
        this.leftValue = leftValue;
        return this;
      }

      private Builder rightValue(final IndexMappingProperty rightValue) {
        this.rightValue = rightValue;
        return this;
      }

      private PropertyDifference build() {
        return new PropertyDifference(name, leftValue, rightValue);
      }
    }
  }
}
