/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.CollectionUtil.throwAwayNullElements;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public abstract class ElasticsearchUtil {

  // ============ ES Query Helper Methods ============

  /**
   * Creates a match-none query for ES that returns no results.
   *
   * @return BoolQuery.Builder configured to match no documents
   */
  public static BoolQuery.Builder createMatchNoneQueryEs() {
    return QueryBuilders.bool().must(m -> m.matchNone(mn -> mn));
  }

  /**
   * Creates a terms query for ES with a collection of values.
   *
   * @param name Field name
   * @param values Collection of values to match
   * @return Query with terms condition
   */
  public static Query termsQuery(final String name, final Collection<?> values) {
    if (values.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(
          "Cannot use terms query with null value, trying to query ["
              + name
              + "] where terms field is "
              + values);
    }

    return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
        q ->
            q.terms(
                t ->
                    t.field(name)
                        .terms(
                            TermsQueryField.of(
                                tf -> tf.value(values.stream().map(FieldValue::of).toList())))));
  }

  /**
   * Creates a terms query for ES with a single value.
   *
   * @param name Field name
   * @param value Single value to match
   * @return Query with terms condition
   */
  public static <T> Query termsQuery(final String name, final T value) {
    if (value == null) {
      throw new IllegalArgumentException(
          "Cannot use terms query with null value, trying to query [" + name + "] with null value");
    }

    if (value.getClass().isArray()) {
      throw new IllegalStateException(
          "Cannot pass an array to the singleton terms query, must pass a single value");
    }

    return termsQuery(name, List.of(value));
  }

  /**
   * Joins multiple ES queries with AND logic. Returns null if no queries provided, single query if
   * only one provided, or a bool query with must clauses for multiple queries.
   *
   * @param queries Queries to join
   * @return Combined query or null
   */
  public static Query joinWithAnd(final Query... queries) {
    final var notNullQueries = throwAwayNullElements(queries);

    if (notNullQueries.isEmpty()) {
      return null;
    } else if (notNullQueries.size() == 1) {
      return notNullQueries.get(0);
    } else {
      return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
          q -> q.bool(b -> b.must(notNullQueries)));
    }
  }

  /**
   * Creates a match-all query.
   *
   * @return Query that matches all documents
   */
  public static Query matchAllQuery() {
    return Query.of(q -> q.matchAll(m -> m));
  }

  /**
   * Wraps a query in a constant_score query, which assigns all matching documents a relevance score
   * equal to the boost parameter (default 1.0).
   *
   * @param query The query to wrap
   * @return Query with constant scoring applied
   */
  public static Query constantScoreQuery(final Query query) {
    return Query.of(q -> q.constantScore(cs -> cs.filter(query)));
  }

  // ===========================================================================================
  // ES Sort Helper Methods
  // ===========================================================================================

  /**
   * Creates an ES SortOptions for a field with specified order.
   *
   * @param field The field name to sort by
   * @param sortOrder The sort order (Asc or Desc)
   * @return SortOptions configured for the field
   */
  public static SortOptions sortOrder(
      final String field, final co.elastic.clients.elasticsearch._types.SortOrder sortOrder) {
    return SortOptions.of(s -> s.field(f -> f.field(field).order(sortOrder)));
  }

  /**
   * Creates an ES SortOptions for a field with specified order and missing value handling.
   *
   * @param field The field name to sort by
   * @param sortOrder The sort order (Asc or Desc)
   * @param missing How to handle missing values ("_first", "_last", or a custom value)
   * @return SortOptions configured for the field with missing value handling
   */
  public static SortOptions sortOrder(
      final String field,
      final co.elastic.clients.elasticsearch._types.SortOrder sortOrder,
      final String missing) {
    return SortOptions.of(s -> s.field(f -> f.field(field).order(sortOrder).missing(missing)));
  }
}
