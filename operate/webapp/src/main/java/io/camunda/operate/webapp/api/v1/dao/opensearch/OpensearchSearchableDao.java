/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.springframework.lang.NonNull;

/**
 * @param <T> - API model class
 * @param <R> - Internal model class that maps to an opensearch document and fields
 */
public abstract class OpensearchSearchableDao<T, R> {

  protected final OpensearchQueryDSLWrapper queryDSLWrapper;
  protected final OpensearchRequestDSLWrapper requestDSLWrapper;
  protected final RichOpenSearchClient richOpenSearchClient;

  public OpensearchSearchableDao(
      final OpensearchQueryDSLWrapper queryDSLWrapper,
      final OpensearchRequestDSLWrapper requestDSLWrapper,
      final RichOpenSearchClient richOpenSearchClient) {

    this.queryDSLWrapper = queryDSLWrapper;
    this.requestDSLWrapper = requestDSLWrapper;
    this.richOpenSearchClient = richOpenSearchClient;
  }

  public Results<T> search(final Query<T> query) {
    final var request = buildSearchRequest(query);

    buildSorting(query, getUniqueSortKey(), request);
    buildFiltering(query, request);
    buildPaging(query, request);

    try {
      final HitsMetadata<R> results =
          richOpenSearchClient.doc().search(request, getInternalDocumentModelClass()).hits();

      return formatHitsIntoResults(results);
    } catch (final Exception e) {
      throw new ServerException("Error in reading incidents", e);
    }
  }

  protected SearchRequest.Builder buildSearchRequest(final Query<T> query) {
    return requestDSLWrapper
        .searchRequestBuilder(getIndexName())
        .query(queryDSLWrapper.withTenantCheck(queryDSLWrapper.matchAll()));
  }

  protected abstract String getUniqueSortKey();

  protected abstract Class<R> getInternalDocumentModelClass();

  protected abstract String getIndexName();

  protected void buildSorting(
      final Query<T> query, final String uniqueSortKey, final SearchRequest.Builder request) {
    final List<Query.Sort> sorts = query.getSort();
    if (sorts != null) {
      sorts.forEach(
          sort -> {
            final Query.Sort.Order order = sort.getOrder();
            if (order.equals(Query.Sort.Order.DESC)) {
              request.sort(queryDSLWrapper.sortOptions(sort.getField(), SortOrder.Desc));
            } else {
              // if not specified always assume ASC order
              request.sort(queryDSLWrapper.sortOptions(sort.getField(), SortOrder.Asc));
            }
          });
    }
    request.sort(queryDSLWrapper.sortOptions(uniqueSortKey, SortOrder.Asc));
  }

  protected void buildPaging(final Query<T> query, final SearchRequest.Builder request) {
    final Object[] searchAfter = query.getSearchAfter();
    if (searchAfter != null) {
      request.searchAfter(CollectionUtil.toSafeListOfStrings(searchAfter));
    }
    request.size(query.getSize());
  }

  /**
   * Builds a tenant-aware filter query based on the query terms provided by {@link
   * #collectFilterQueryTerms(Object)} and {@link #collectRequiredFilterQueryTerms()}
   */
  @VisibleForTesting
  protected void buildFiltering(final Query<T> query, final SearchRequest.Builder request) {
    final var queryTerms =
        Stream.concat(
                collectRequiredFilterQueryTerms(),
                Optional.ofNullable(query.getFilter())
                    .map(this::collectFilterQueryTerms)
                    .orElseGet(Stream::empty))
            .filter(Objects::nonNull)
            .toList();

    if (!queryTerms.isEmpty()) {
      request.query(queryDSLWrapper.withTenantCheck(queryDSLWrapper.and(queryTerms)));
    }
  }

  /**
   * Collect required query terms that must always be present.
   *
   * <p>You can use {@link #collectFilterQueryTerms(Object)} instead to build query terms based on
   * an existing filter object. The two may be combined if you have query terms that always need to
   * be present as well as query terms based on a filter object that may or may not exist.
   */
  protected Stream<org.opensearch.client.opensearch._types.query_dsl.Query>
      collectRequiredFilterQueryTerms() {
    return Stream.empty();
  }

  /**
   * Collect query terms based on the query's filter object.
   *
   * <p>You can use {@link #collectRequiredFilterQueryTerms()} instead to build query terms that
   * must always be present.The two may be combined if you have query terms that always need to be
   * present as well as query terms based on a filter object that may or may not exist.
   */
  protected abstract Stream<org.opensearch.client.opensearch._types.query_dsl.Query>
      collectFilterQueryTerms(@NonNull final T filter);

  protected Results<T> formatHitsIntoResults(final HitsMetadata<R> results) {
    final List<Hit<R>> hits = results.hits();

    if (!hits.isEmpty()) {
      final List<T> items =
          hits.stream()
              .map(hit -> convertInternalToApiResult(hit.source()))
              .filter(Objects::nonNull)
              .toList();

      final List<String> sortValues = hits.get(hits.size() - 1).sort();

      return new Results<T>()
          .setTotal(results.total().value())
          .setItems(items)
          .setSortValues(sortValues.toArray());
    } else {
      return new Results<T>().setTotal(results.total().value());
    }
  }

  protected abstract T convertInternalToApiResult(R internalResult);
}
