/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregator;

import io.camunda.util.ObjectBuilder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record SearchCompositeAggregator(
    String name,
    Integer size,
    String after,
    List<SearchAggregator> aggregations,
    List<SearchAggregator> sources)
    implements SearchAggregator {

  public static final String COMPOSITE_KEY_DELIMITER = "__";

  /**
   * Joins a map of composite source key→value pairs into a single bucket map key.
   *
   * <p>Entries are sorted by key name before joining to guarantee a deterministic order regardless
   * of the underlying {@link Map} iteration order.
   *
   * @param keyValues a map of source name → field value (as produced by a composite bucket)
   * @return the joined key string
   */
  public static String joinKeys(final Map<String, String> keyValues) {
    return keyValues.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(Map.Entry::getValue)
        .collect(Collectors.joining(COMPOSITE_KEY_DELIMITER));
  }

  /**
   * Splits a composite bucket key values string back into a {@code Map} of source name → value.
   *
   * <p>The provided {@code keys} are sorted alphabetically to match the order used by {@link
   * #joinKeys(Map)}. The split uses a bounded limit equal to the number of keys so that the last
   * value may itself contain the delimiter.
   *
   * @param compositeKeyValues the joined values string (e.g. {@code "THECODE__THEMESSAGE"})
   * @param keys the source names whose values are encoded in {@code compositeKeyValues} (e.g.
   *     {@code "errorCode", "errorMessage"})
   * @return a map from source name to its value
   */
  public static Map<String, String> splitKeyValues(
      final String compositeKeyValues, final String... keys) {
    Arrays.sort(keys);
    final String[] parts = compositeKeyValues.split(COMPOSITE_KEY_DELIMITER, keys.length);
    final Map<String, String> result = new HashMap<>();
    IntStream.range(0, keys.length)
        .forEach(i -> result.put(keys[i], i < parts.length ? parts[i] : null));
    return result;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<SearchAggregator> getAggregations() {
    return aggregations;
  }

  public static final class Builder extends AbstractBuilder<SearchCompositeAggregator.Builder>
      implements ObjectBuilder<SearchCompositeAggregator> {
    private Integer size = 10000;
    private String after;
    private List<SearchAggregator> sources;

    @Override
    protected SearchCompositeAggregator.Builder self() {
      return this;
    }

    public SearchCompositeAggregator.Builder size(final Integer value) {
      // Validate size to ensure it's a positive integer
      if (value != null && value < 0) {
        throw new IllegalArgumentException("Size must be a positive integer.");
      }
      size = value;
      return this;
    }

    public SearchCompositeAggregator.Builder after(final String value) {
      after = value;
      return this;
    }

    public SearchCompositeAggregator.Builder sources(final List<SearchAggregator> sources) {
      this.sources = sources;
      return this;
    }

    @Override
    public SearchCompositeAggregator build() {
      return new SearchCompositeAggregator(
          Objects.requireNonNull(name, "Expected non-null field for name."),
          size,
          after,
          aggregations,
          Objects.requireNonNull(sources, "Expected non-null field for sources."));
    }
  }
}
