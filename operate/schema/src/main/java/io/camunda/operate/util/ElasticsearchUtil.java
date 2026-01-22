/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.CollectionUtil.throwAwayNullElements;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.MissingRequiredPropertyException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.ScrollException;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ElasticsearchUtil {

  public static final int SCROLL_KEEP_ALIVE_MS = 60000;
  public static final int TERMS_AGG_SIZE = 10000;
  public static final int QUERY_MAX_SIZE = 10000;
  public static final int TOPHITS_AGG_SIZE = 100;
  public static final int UPDATE_RETRY_COUNT = 3;
  public static final Class<Map<String, Object>> MAP_CLASS =
      (Class<Map<String, Object>>) (Class<?>) Map.class;
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchUtil.class);

  public static String whereToSearch(
      final IndexTemplateDescriptor template, final QueryType queryType) {
    switch (queryType) {
      case ONLY_RUNTIME:
        return template.getFullQualifiedName();
      case ALL:
      default:
        return template.getAlias();
    }
  }

  public static Query joinWithOr(final Query... queries) {
    final List<Query> notNullQueries = throwAwayNullElements(queries);

    return switch (notNullQueries.size()) {
      case 0 -> null;
      case 1 -> notNullQueries.get(0);
      default -> Query.of(q -> q.bool(b -> b.should(notNullQueries)));
    };
  }

  /**
   * Join queries with AND clause. If 0 queries are passed for wrapping, then null is returned. If 1
   * parameter is passed, it will be returned back as is. Otherwise, a new BoolQuery will be created
   * and returned.
   *
   * @param queries Variable number of Query objects to join
   * @return A single Query combining all inputs with AND logic, or null if no queries provided
   */
  public static Query joinWithAnd(final Query... queries) {
    final List<Query> notNullQueries = throwAwayNullElements(queries);

    return switch (notNullQueries.size()) {
      case 0 -> null;
      case 1 -> notNullQueries.get(0);
      default -> Query.of(q -> q.bool(b -> b.must(notNullQueries)));
    };
  }

  public static BoolQuery.Builder createMatchNoneQuery() {
    return QueryBuilders.bool().must(m -> m.matchNone(mn -> mn));
  }

  public static void processBulkRequest(
      final ElasticsearchClient esClient,
      final BulkRequest.Builder bulkRequestBuilder,
      final long maxBulkRequestSizeInBytes) {
    processBulkRequest(esClient, bulkRequestBuilder, false, maxBulkRequestSizeInBytes);
  }

  public static String getFieldFromResponseObject(
      final SearchResponse<Map<String, Object>> response, final String fieldName) {
    if (response.hits().hits().size() != 1) {
      throw new IllegalArgumentException(
          "Expected exactly one document in response object " + response);
    }

    return String.valueOf(response.hits().hits().getFirst().source().get(fieldName));
  }

  public static Query idsQuery(final String... ids) {
    return Query.of(q -> q.ids(i -> i.values(Arrays.asList(ids))));
  }

  public static Query matchAllQuery() {
    return Query.of(q -> q.matchAll(m -> m));
  }

  /**
   * A query that matches documents where the specified field contains a term with a specified
   * prefix.
   *
   * @param field The field name
   * @param prefix The prefix to match
   */
  public static Query prefixQuery(final String field, final String prefix) {
    return Query.of(q -> q.prefix(p -> p.field(field).value(prefix)));
  }

  /**
   * A query that matches documents that have at least one non-null value in the specified field.
   *
   * @param field The field name
   */
  public static Query existsQuery(final String field) {
    return Query.of(q -> q.exists(e -> e.field(field)));
  }

  /**
   * Creates a has_child query that returns parent documents whose child documents match the query.
   *
   * @param type The child type to query
   * @param query The query to run on child documents
   * @param scoreMode How to score the parent documents
   * @return ES Query object
   */
  public static Query hasChildQuery(
      final String type, final Query query, final ChildScoreMode scoreMode) {
    return Query.of(q -> q.hasChild(h -> h.type(type).query(query).scoreMode(scoreMode)));
  }

  /**
   * A query that wraps another query and simply returns a constant score equal to the query boost
   * for every document in the query.
   *
   * @param query The query to wrap in a constant score query
   */
  public static Query constantScoreQuery(final Query query) {
    return Query.of(q -> q.constantScore(cs -> cs.filter(query)));
  }

  /* EXECUTE QUERY */

  public static void processBulkRequest(
      final ElasticsearchClient esClient,
      final BulkRequest.Builder bulkRequestBuilder,
      final boolean refreshImmediately,
      final long maxBulkRequestSizeInBytes) {
    try {
      final var bulkRequest = bulkRequestBuilder.build();
      LOGGER.debug("Execute batchRequest with {} requests", bulkRequest.operations().size());

      try (final var bulkIngester =
          createBulkIngester(esClient, maxBulkRequestSizeInBytes, refreshImmediately)) {
        bulkRequest.operations().forEach(bulkIngester::add);
      }
    } catch (final MissingRequiredPropertyException ignored) {
      // if bulk request has no operations calling .build() will throw an exception, we suppress
      // this as it is a no op.
    }
  }

  private static BulkIngester<Void> createBulkIngester(
      final ElasticsearchClient esClient,
      final long maxBulkRequestSizeInBytes,
      final boolean refreshImmediately) {
    final Refresh refreshVal;
    if (refreshImmediately) {
      refreshVal = Refresh.True;
    } else {
      refreshVal = Refresh.False;
    }
    return BulkIngester.of(
        b ->
            b.client(esClient)
                .maxOperations(100)
                .maxSize(maxBulkRequestSizeInBytes)
                .globalSettings(s -> s.refresh(refreshVal)));
  }

  public static <T> T fromSearchHit(
      final String searchHitString, final ObjectMapper objectMapper, final Class<T> clazz) {
    final T entity;
    try {
      entity = objectMapper.readValue(searchHitString, clazz);
    } catch (final IOException e) {
      LOGGER.error(
          String.format(
              "Error while reading entity of type %s from Elasticsearch!", clazz.getName()),
          e);
      throw new OperateRuntimeException(
          String.format(
              "Error while reading entity of type %s from Elasticsearch!", clazz.getName()),
          e);
    }
    return entity;
  }

  public static <T> Stream<ResponseBody<T>> scrollAllStream(
      final ElasticsearchClient client,
      final SearchRequest.Builder searchRequestBuilder,
      final Class<T> docClass) {
    final AtomicReference<String> lastScrollId = new AtomicReference<>(null);

    final var scrollKeepAlive = Time.of(t -> t.time(SCROLL_KEEP_ALIVE_MS + "ms"));

    final SearchResponse<T> searchRes;
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

  private static void clearScrollSilently(final ElasticsearchClient client, final String scrollId) {
    if (scrollId != null) {
      try {
        client.clearScroll(cs -> cs.scrollId(scrollId));
      } catch (final Exception e) {
        LOGGER.warn("Error occurred when clearing the scroll with id [{}]", scrollId, e);
      }
    }
  }

  public static SortOrder reverseOrder(final SortOrder sortOrder) {
    if (sortOrder.equals(SortOrder.Asc)) {
      return SortOrder.Desc;
    } else {
      return SortOrder.Asc;
    }
  }

  public static SortOrder toSortOrder(final String sortOrder) {
    return "desc".equalsIgnoreCase(sortOrder) ? SortOrder.Desc : SortOrder.Asc;
  }

  public static Query termsQuery(final String name, final Collection<?> values) {
    if (values.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(
          "Cannot use terms query with null value, trying to query ["
              + name
              + "] where terms field is "
              + values);
    }
    return Query.of(
        q ->
            q.terms(
                t ->
                    t.field(name)
                        .terms(
                            TermsQueryField.of(
                                tf -> tf.value(values.stream().map(FieldValue::of).toList())))));
  }

  public static <T> Query termsQuery(final String name, final T value) {
    if (value == null) {
      throw new IllegalArgumentException(
          "Cannot use terms query with null value, trying to query [" + name + "] with null value");
    }

    if (value.getClass().isArray()) {
      throw new IllegalStateException(
          "Cannot pass an array to the singleton terms query, must pass a single value");
    }

    return termsQuery(name, Collections.singletonList(value));
  }

  public static SortOptions sortOrder(final String field, final SortOrder sortOrder) {
    return SortOptions.of(s -> s.field(f -> f.field(field).order(sortOrder)));
  }

  public static SortOptions sortOrder(
      final String field, final SortOrder sortOrder, final String missing) {
    return SortOptions.of(s -> s.field(f -> f.field(field).order(sortOrder).missing(missing)));
  }

  /**
   * Converts an array of search_after values to ES FieldValue list for pagination.
   *
   * @param searchAfter Array of sort values from previous search result
   * @return List of FieldValue objects for ES searchAfter parameter
   */
  public static List<FieldValue> searchAfterToFieldValues(final Object[] searchAfter) {
    return Arrays.stream(searchAfter).map(FieldValue::of).toList();
  }

  public enum QueryType {
    ONLY_RUNTIME,
    ALL
  }
}
