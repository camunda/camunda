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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class IndexMappingDifference {

  private boolean equal;
  private Set<IndexMappingProperty> entriesOnlyOnLeft;
  private Set<IndexMappingProperty> entriesOnlyOnRight;
  private Set<IndexMappingProperty> entriesInCommon;
  private Set<PropertyDifference> entriesDiffering;
  private IndexMapping leftIndexMapping;
  private IndexMapping rightIndexMapping;

  public boolean isEqual() {
    return equal;
  }

  public IndexMappingDifference setEqual(final boolean equal) {
    this.equal = equal;
    return this;
  }

  public Set<IndexMappingProperty> getEntriesOnlyOnLeft() {
    return entriesOnlyOnLeft;
  }

  public IndexMappingDifference setEntriesOnlyOnLeft(
      final Set<IndexMappingProperty> entriesOnlyOnLeft) {
    this.entriesOnlyOnLeft = entriesOnlyOnLeft;
    return this;
  }

  public Set<IndexMappingProperty> getEntriesOnlyOnRight() {
    return entriesOnlyOnRight;
  }

  public IndexMappingDifference setEntriesOnlyOnRight(
      final Set<IndexMappingProperty> entriesOnlyOnRight) {
    this.entriesOnlyOnRight = entriesOnlyOnRight;
    return this;
  }

  public Set<IndexMappingProperty> getEntriesInCommon() {
    return entriesInCommon;
  }

  public IndexMappingDifference setEntriesInCommon(
      final Set<IndexMappingProperty> entriesInCommon) {
    this.entriesInCommon = entriesInCommon;
    return this;
  }

  public Set<PropertyDifference> getEntriesDiffering() {
    return entriesDiffering;
  }

  public IndexMappingDifference setEntriesDiffering(
      final Set<PropertyDifference> entriesDiffering) {
    this.entriesDiffering = entriesDiffering;
    return this;
  }

  public IndexMapping getLeftIndexMapping() {
    return leftIndexMapping;
  }

  public IndexMappingDifference setLeftIndexMapping(final IndexMapping leftIndexMapping) {
    this.leftIndexMapping = leftIndexMapping;
    return this;
  }

  public IndexMapping getRightIndexMapping() {
    return rightIndexMapping;
  }

  public IndexMappingDifference setRightIndexMapping(final IndexMapping rightIndexMapping) {
    this.rightIndexMapping = rightIndexMapping;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        equal,
        entriesOnlyOnLeft,
        entriesOnlyOnRight,
        entriesInCommon,
        entriesDiffering,
        leftIndexMapping,
        rightIndexMapping);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IndexMappingDifference that = (IndexMappingDifference) o;
    return equal == that.equal
        && Objects.equals(entriesOnlyOnLeft, that.entriesOnlyOnLeft)
        && Objects.equals(entriesOnlyOnRight, that.entriesOnlyOnRight)
        && Objects.equals(entriesInCommon, that.entriesInCommon)
        && Objects.equals(entriesDiffering, that.entriesDiffering)
        && Objects.equals(leftIndexMapping, that.leftIndexMapping)
        && Objects.equals(rightIndexMapping, that.rightIndexMapping);
  }

  @Override
  public String toString() {
    return "IndexMappingDifference{"
        + "equal="
        + equal
        + ", entriesOnlyOnLeft="
        + entriesOnlyOnLeft
        + ", entriesOnlyOnRight="
        + entriesOnlyOnRight
        + ", entriesInCommon="
        + entriesInCommon
        + ", entriesDiffering="
        + entriesDiffering
        + ", leftIndexMapping="
        + leftIndexMapping
        + ", rightIndexMapping="
        + rightIndexMapping
        + '}';
  }

  // Compares the differences between two IndexMappingDifference objects independent
  // of the index mappings. Used to validate that different indices have the "same"
  // differences.
  public boolean checkEqualityForDifferences(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IndexMappingDifference that = (IndexMappingDifference) o;
    return equal == that.equal
        && Objects.equals(entriesOnlyOnLeft, that.entriesOnlyOnLeft)
        && Objects.equals(entriesOnlyOnRight, that.entriesOnlyOnRight)
        && Objects.equals(entriesInCommon, that.entriesInCommon)
        && Objects.equals(entriesDiffering, that.entriesDiffering);
  }

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
      return new IndexMappingDifference()
          .setEqual(difference.areEqual())
          .setLeftIndexMapping(left)
          .setRightIndexMapping(right)
          .setEntriesOnlyOnLeft(
              difference.entriesOnlyOnLeft().entrySet().stream()
                  .map(IndexMappingProperty::createIndexMappingProperty)
                  .collect(Collectors.toSet()))
          .setEntriesOnlyOnRight(
              difference.entriesOnlyOnRight().entrySet().stream()
                  .map(IndexMappingProperty::createIndexMappingProperty)
                  .collect(Collectors.toSet()))
          .setEntriesInCommon(
              difference.entriesInCommon().entrySet().stream()
                  .map(IndexMappingProperty::createIndexMappingProperty)
                  .collect(Collectors.toSet()))
          .setEntriesDiffering(
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
                  .collect(Collectors.toSet()));
    }
  }

  public record PropertyDifference(
      String name, IndexMappingProperty leftValue, IndexMappingProperty rightValue) {

    public static class Builder {
      private String name;
      private IndexMappingProperty leftValue;
      private IndexMappingProperty rightValue;

      public Builder name(final String name) {
        this.name = name;
        return this;
      }

      public Builder leftValue(final IndexMappingProperty leftValue) {
        this.leftValue = leftValue;
        return this;
      }

      public Builder rightValue(final IndexMappingProperty rightValue) {
        this.rightValue = rightValue;
        return this;
      }

      public PropertyDifference build() {
        return new PropertyDifference(name, leftValue, rightValue);
      }
    }
  }
}
