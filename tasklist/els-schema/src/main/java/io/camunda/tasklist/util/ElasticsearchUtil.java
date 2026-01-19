/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.CollectionUtil.throwAwayNullElements;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.ScrollException;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ElasticsearchUtil {

  public static final String ZEEBE_INDEX_DELIMITER = "_";
  public static final int AGGREGATION_TERMS_SIZE = 20000;
  public static final int SCROLL_KEEP_ALIVE_MS = 60000;
  public static final int INTERNAL_SCROLL_KEEP_ALIVE_MS =
      30000; // this scroll timeout value is used for reindex and delete queries
  public static final int QUERY_MAX_SIZE = 10000;
  public static final int UPDATE_RETRY_COUNT = 3;

  public static final Class<Map<String, Object>> MAP_CLASS =
      (Class<Map<String, Object>>) (Class<?>) Map.class;

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchUtil.class);

  /* CREATE QUERIES */

  public static String whereToSearch(final IndexDescriptor descriptor, final QueryType queryType) {
    switch (queryType) {
      case ONLY_RUNTIME:
        return descriptor.getFullQualifiedName();
      case ALL:
      default:
        return descriptor.getAlias();
    }
  }

  /**
   * Scrolls through all search results using ES8 client and collects results into a list, with
   * chunking support for large ID lists.
   *
   * @param client ES8 client
   * @param ids List of IDs to process in chunks
   * @param chunkSize Maximum number of IDs per chunk
   * @param searchRequestBuilderFactory Factory to create search request builder for each chunk
   * @param docClass Document class type
   * @param <T> Type of documents
   * @param <ID> Type of IDs
   * @return List of document sources
   */
  public static <T, ID> List<T> scrollInChunks(
      final co.elastic.clients.elasticsearch.ElasticsearchClient client,
      final List<ID> ids,
      final int chunkSize,
      final Function<List<ID>, co.elastic.clients.elasticsearch.core.SearchRequest.Builder>
          searchRequestBuilderFactory,
      final Class<T> docClass) {
    final var result = new ArrayList<T>();
    for (final var chunk : ListUtils.partition(ids, chunkSize)) {
      result.addAll(scrollAllToList(client, searchRequestBuilderFactory.apply(chunk), docClass));
    }
    return result;
  }

  /**
   * Scrolls through all search results using ES8 client and collects document IDs with their index
   * names into a map.
   *
   * @param client ES8 client
   * @param searchRequestBuilder Search request builder
   * @return Map of document ID to index name
   */
  public static Map<String, String> scrollIdsWithIndexToMap(
      final co.elastic.clients.elasticsearch.ElasticsearchClient client,
      final co.elastic.clients.elasticsearch.core.SearchRequest.Builder searchRequestBuilder) {
    return scrollAllStream(client, searchRequestBuilder, MAP_CLASS)
        .flatMap(response -> response.hits().hits().stream())
        .collect(Collectors.toMap(Hit::id, Hit::index));
  }

  // ============ ES8 Query Helper Methods ============

  /**
   * Creates a match-none query for ES8 that returns no results.
   *
   * @return BoolQuery.Builder configured to match no documents
   */
  public static co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder
      createMatchNoneQueryEs8() {
    return co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool()
        .must(m -> m.matchNone(mn -> mn));
  }

  /**
   * Creates a terms query for ES8 with a collection of values.
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
   * Creates a terms query for ES8 with a single value.
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
   * Joins multiple ES8 queries with AND logic. Returns null if no queries provided, single query if
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
   * Creates a match-all query for ES8.
   *
   * @return Query that matches all documents
   */
  public static Query matchAllQueryEs8() {
    return Query.of(q -> q.matchAll(m -> m));
  }

  /**
   * Creates an ES8 ids query for the given document IDs.
   *
   * @param ids Document IDs to match
   * @return Query that matches documents with the specified IDs
   */
  public static Query idsQuery(final String... ids) {
    return Query.of(q -> q.ids(i -> i.values(Arrays.asList(ids))));
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

  /**
   * Creates an ES8 exists query that matches documents containing a value for the specified field.
   *
   * @param field The field name to check for existence
   * @return Query that matches documents where the field exists
   */
  public static Query existsQuery(final String field) {
    return Query.of(q -> q.exists(e -> e.field(field)));
  }

  /**
   * Creates a bool query that must NOT match the provided query.
   *
   * @param query The query to negate
   * @return Query that does not match the provided query
   */
  public static Query mustNotQuery(final Query query) {
    return Query.of(q -> q.bool(b -> b.mustNot(query)));
  }

  /**
   * Joins multiple ES8 queries with OR logic. Returns null if no queries provided, single query if
   * only one provided, or a bool query with should clauses for multiple queries.
   *
   * @param queries Queries to join
   * @return Combined query or null
   */
  public static Query joinWithOr(final Query... queries) {
    final var notNullQueries = throwAwayNullElements(queries);

    if (notNullQueries.isEmpty()) {
      return null;
    } else if (notNullQueries.size() == 1) {
      return notNullQueries.get(0);
    } else {
      return Query.of(q -> q.bool(b -> b.should(notNullQueries)));
    }
  }

  /**
   * Creates an ES8 range query builder for the specified field.
   *
   * @param field Field name to apply range on
   * @return A RangeQueryBuilder for chaining range operations
   */
  public static RangeQueryBuilder rangeQuery(final String field) {
    return new RangeQueryBuilder(field);
  }

  /**
   * Creates an ES8 script sort option.
   *
   * @param scriptSource The inline script source
   * @param scriptSortType The type of script sort (STRING or NUMBER)
   * @param sortOrder The sort order
   * @return SortOptions configured for script sorting
   */
  public static SortOptions scriptSort(
      final String scriptSource,
      final co.elastic.clients.elasticsearch._types.ScriptSortType scriptSortType,
      final co.elastic.clients.elasticsearch._types.SortOrder sortOrder) {
    return SortOptions.of(
        s ->
            s.script(
                sc ->
                    sc.script(
                            script ->
                                script
                                    .lang(
                                        co.elastic.clients.elasticsearch._types.ScriptLanguage
                                            .Painless)
                                    .source(scriptSource))
                        .type(scriptSortType)
                        .order(sortOrder)));
  }

  // ===========================================================================================
  // ES8 Scroll Helper Methods
  // ===========================================================================================

  /**
   * Scrolls through all search results using the ES8 client and returns a stream of response
   * bodies.
   *
   * @param client ES8 client
   * @param searchRequestBuilder Search request builder
   * @param docClass Document class type
   * @param <T> Type of documents
   * @return Stream of response bodies containing hits
   * @throws ScrollException if scroll operation fails
   */
  public static <T> Stream<ResponseBody<T>> scrollAllStream(
      final co.elastic.clients.elasticsearch.ElasticsearchClient client,
      final co.elastic.clients.elasticsearch.core.SearchRequest.Builder searchRequestBuilder,
      final Class<T> docClass) {
    final AtomicReference<String> lastScrollId = new AtomicReference<>(null);

    final var scrollKeepAlive = Time.of(t -> t.time(SCROLL_KEEP_ALIVE_MS + "ms"));

    final co.elastic.clients.elasticsearch.core.SearchResponse<T> searchRes;
    searchRequestBuilder.scroll(scrollKeepAlive);

    try {
      searchRes = client.search(searchRequestBuilder.build(), docClass);
      lastScrollId.set(searchRes.scrollId());

      if (searchRes.hits().hits().isEmpty()) {
        clearScrollSilently(client, lastScrollId.get());
        return Stream.of(searchRes);
      }
    } catch (final IOException e) {
      throw new ScrollException("Error during scroll where initial search request failed", e);
    }

    final var scrollStream =
        Stream.generate(
                () -> {
                  final ScrollResponse<T> response;
                  try {
                    response =
                        client.scroll(
                            r -> r.scrollId(lastScrollId.get()).scroll(scrollKeepAlive), docClass);

                    lastScrollId.set(response.scrollId());

                    if (response.hits().hits().isEmpty()) {
                      clearScrollSilently(client, lastScrollId.get());
                      return null;
                    }
                  } catch (final IOException e) {
                    clearScrollSilently(client, lastScrollId.get());
                    throw new ScrollException(
                        "Error during scroll with id: " + lastScrollId.get(), e);
                  }
                  return response;
                })
            .takeWhile(Objects::nonNull);

    return Stream.concat(Stream.of(searchRes), scrollStream);
  }

  /**
   * Clears scroll context silently, logging any errors that occur.
   *
   * @param client ES8 client
   * @param scrollId Scroll ID to clear
   */
  private static void clearScrollSilently(
      final co.elastic.clients.elasticsearch.ElasticsearchClient client, final String scrollId) {
    if (scrollId != null) {
      try {
        client.clearScroll(cs -> cs.scrollId(scrollId));
      } catch (final Exception e) {
        LOGGER.warn("Error occurred when clearing the scroll with id [{}]", scrollId, e);
      }
    }
  }

  /**
   * Scrolls through all search results using ES8 client and collects results into a list.
   *
   * @param client ES8 client
   * @param searchRequestBuilder Search request builder
   * @param docClass Document class type
   * @param <T> Type of documents
   * @return List of document sources
   */
  public static <T> List<T> scrollAllToList(
      final co.elastic.clients.elasticsearch.ElasticsearchClient client,
      final co.elastic.clients.elasticsearch.core.SearchRequest.Builder searchRequestBuilder,
      final Class<T> docClass) {
    return scrollAllStream(client, searchRequestBuilder, docClass)
        .flatMap(response -> response.hits().hits().stream())
        .map(co.elastic.clients.elasticsearch.core.search.Hit::source)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * Scrolls through all search results using ES8 client and collects a specific field's Long values
   * into a set.
   *
   * @param client ES8 client
   * @param searchRequestBuilder Search request builder
   * @param fieldName The field name to extract Long values from
   * @return Set of Long values
   */
  public static Set<Long> scrollFieldToLongSet(
      final co.elastic.clients.elasticsearch.ElasticsearchClient client,
      final co.elastic.clients.elasticsearch.core.SearchRequest.Builder searchRequestBuilder,
      final String fieldName) {
    return scrollAllStream(client, searchRequestBuilder, MAP_CLASS)
        .flatMap(response -> response.hits().hits().stream())
        .map(co.elastic.clients.elasticsearch.core.search.Hit::source)
        .filter(Objects::nonNull)
        .map(source -> source.get(fieldName))
        .filter(Objects::nonNull)
        .map(value -> ((Number) value).longValue())
        .collect(Collectors.toSet());
  }

  // ===========================================================================================
  // ES8 Sort Helper Methods
  // ===========================================================================================

  /**
   * Creates an ES8 SortOptions for a field with specified order.
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
   * Creates an ES8 SortOptions for a field with specified order and missing value handling.
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

  // ===========================================================================================
  // Inner Classes
  // ===========================================================================================

  /**
   * Executes a bulk request with the given operations and refresh policy.
   *
   * @param client the Elasticsearch client
   * @param operations the list of bulk operations to execute
   * @param refresh the refresh policy to use
   * @throws IOException if an I/O error occurs
   * @throws TasklistRuntimeException if the bulk request contains errors
   */
  public static void executeBulkRequest(
      final ElasticsearchClient client, final List<BulkOperation> operations, final Refresh refresh)
      throws IOException {
    if (operations.isEmpty()) {
      return;
    }

    final var bulkRequest =
        co.elastic.clients.elasticsearch.core.BulkRequest.of(
            b -> b.operations(operations).refresh(refresh));

    final var bulkResponse = client.bulk(bulkRequest);

    if (bulkResponse.errors()) {
      final var errorMessages =
          bulkResponse.items().stream()
              .filter(item -> item.error() != null)
              .map(item -> item.error().reason())
              .collect(Collectors.joining(", "));
      throw new TasklistRuntimeException("Bulk request failed. Errors: " + errorMessages);
    }
  }

  /**
   * Converts an array of search_after values to ES8 FieldValue list for pagination.
   *
   * @param searchAfter Array of sort values from previous search result
   * @return List of FieldValue objects for ES8 searchAfter parameter
   */
  public static List<co.elastic.clients.elasticsearch._types.FieldValue> searchAfterToFieldValues(
      final Object[] searchAfter) {
    return Arrays.stream(searchAfter)
        .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
        .toList();
  }

  /** Builder class for creating ES8 range queries with a fluent API. */
  public static class RangeQueryBuilder {
    private final String field;
    private Object gt;
    private Object gte;
    private Object lt;
    private Object lte;

    public RangeQueryBuilder(final String field) {
      this.field = field;
    }

    public RangeQueryBuilder gt(final Object value) {
      gt = value;
      return this;
    }

    public RangeQueryBuilder gte(final Object value) {
      gte = value;
      return this;
    }

    public RangeQueryBuilder lt(final Object value) {
      lt = value;
      return this;
    }

    public RangeQueryBuilder lte(final Object value) {
      lte = value;
      return this;
    }

    public Query build() {
      final var untypedBuilder =
          new co.elastic.clients.elasticsearch._types.query_dsl.UntypedRangeQuery.Builder();
      untypedBuilder.field(field);

      if (gt != null) {
        untypedBuilder.gt(co.elastic.clients.json.JsonData.of(gt));
      }
      if (gte != null) {
        untypedBuilder.gte(co.elastic.clients.json.JsonData.of(gte));
      }
      if (lt != null) {
        untypedBuilder.lt(co.elastic.clients.json.JsonData.of(lt));
      }
      if (lte != null) {
        untypedBuilder.lte(co.elastic.clients.json.JsonData.of(lte));
      }

      return co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.range()
          .untyped(untypedBuilder.build())
          .build()
          ._toQuery();
    }
  }

  public enum QueryType {
    ONLY_RUNTIME,
    ALL
  }
}
