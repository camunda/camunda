/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregator;

import io.camunda.search.clients.query.SearchQuery;
import java.util.List;

public final class SearchAggregatorBuilders {

  public static SearchChildrenAggregator.Builder children() {
    return new SearchChildrenAggregator.Builder();
  }

  public static SearchChildrenAggregator children(final String name, final String type) {
    return children().name(name).type(type).build();
  }

  public static SearchSumAggregator.Builder sum() {
    return new SearchSumAggregator.Builder();
  }

  public static SearchSumAggregator sum(final String name, final String field) {
    return sum().name(name).field(field).build();
  }

  public static SearchParentAggregator.Builder parent() {
    return new SearchParentAggregator.Builder();
  }

  public static SearchParentAggregator parent(final String name, final String type) {
    return parent().name(name).type(type).build();
  }

  public static SearchTermsAggregator.Builder terms() {
    return new SearchTermsAggregator.Builder();
  }

  public static SearchTermsAggregator terms(final String name, final String field) {
    return terms().name(name).field(field).build();
  }

  public static <T> SearchTopHitsAggregator.Builder<T> topHits() {
    return new SearchTopHitsAggregator.Builder<>();
  }

  public static <T> SearchTopHitsAggregator<T> topHits(
      final String name, final String field, final Class<T> documentClass) {
    final SearchTopHitsAggregator.Builder<T> builder = topHits();
    return builder.name(name).field(field).documentClass(documentClass).build();
  }

  public static SearchCompositeAggregator.Builder composite() {
    return new SearchCompositeAggregator.Builder();
  }

  public static SearchCompositeAggregator composite(
      final String name, final List<SearchAggregator> sources) {
    final SearchCompositeAggregator.Builder builder = composite();
    return builder.name(name).sources(sources).build();
  }

  public static SearchFilterAggregator.Builder filter() {
    return new SearchFilterAggregator.Builder();
  }

  public static SearchFilterAggregator filter(final String name, final SearchQuery filter) {
    return filter().name(name).query(filter).build();
  }

  public static SearchFiltersAggregator.Builder filters() {
    return new SearchFiltersAggregator.Builder();
  }
}
