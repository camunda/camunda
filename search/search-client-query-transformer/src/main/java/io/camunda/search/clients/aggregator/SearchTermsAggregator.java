/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregator;

import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;

public record SearchTermsAggregator(
    String name,
    String field,
    Integer size,
    Integer minDocCount,
    String script,
    String lang,
    List<FieldSorting> sorting,
    List<SearchAggregator> aggregations)
    implements SearchAggregator {

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<SearchAggregator> getAggregations() {
    return aggregations;
  }

  public static final class Builder extends SearchAggregator.AbstractBuilder<Builder>
      implements ObjectBuilder<SearchTermsAggregator> {

    private String field;
    private Integer size = 10; // Default to 10 buckets
    private Integer minDocCount = 1; // Default to showing at least 1 document
    private String script;
    private String lang;
    private List<FieldSorting> sorting;

    @Override
    protected Builder self() {
      return this;
    }

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder sorting(final List<FieldSorting> value) {
      if (value == null) {
        throw new IllegalArgumentException("Order must not be null.");
      }
      sorting = value;
      return this;
    }

    public Builder size(final Integer value) {
      // Validate size to ensure it's a positive integer
      if (value != null && value < 0) {
        throw new IllegalArgumentException("Size must be a positive integer.");
      }
      size = value;
      return this;
    }

    public Builder minDocCount(final Integer value) {
      minDocCount = value;
      return this;
    }

    public Builder script(final String value) {
      script = value;
      return this;
    }

    public Builder lang(final String value) {
      lang = value;
      return this;
    }

    private void validateFieldOrScript(final String field, final String script) {
      final boolean fieldProvided = field != null && !field.isBlank();
      final boolean scriptProvided = script != null && !script.isBlank();

      if (fieldProvided == scriptProvided) {
        // both true or both false → invalid
        throw new IllegalArgumentException(
            "Exactly one of 'field' or 'script' must be provided for SearchTermsAggregator, but received: "
                + "field="
                + field
                + ", script="
                + script);
      }
    }

    @Override
    public SearchTermsAggregator build() {
      validateFieldOrScript(field, script);
      return new SearchTermsAggregator(
          Objects.requireNonNull(name, "Expected non-null field for name."),
          field,
          size,
          minDocCount,
          script,
          lang,
          sorting,
          aggregations);
    }
  }
}
