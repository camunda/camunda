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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;

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
    final var filtering = buildFiltering(query);
    final var sortOptions = buildSorting(query, getUniqueSortKey());
    final var request = buildSearchRequest(query, filtering, sortOptions);
    buildPaging(query, request);

    try {
      final HitsMetadata<R> results =
          richOpenSearchClient.doc().search(request, getInternalDocumentModelClass()).hits();

      return formatHitsIntoResults(results);
    } catch (final Exception e) {
      throw new ServerException("Error in reading incidents", e);
    }
  }

  protected SearchRequest.Builder buildSearchRequest(
      final Query<T> query,
      final org.opensearch.client.opensearch._types.query_dsl.Query filtering,
      final ArrayList<SortOptions> sortOptions) {
    return requestDSLWrapper
        .searchRequestBuilder(getIndexName())
        .sort(sortOptions)
        .query(queryDSLWrapper.withTenantCheck(filtering));
  }

  protected abstract String getUniqueSortKey();

  protected abstract Class<R> getInternalDocumentModelClass();

  protected abstract String getIndexName();

  protected ArrayList<SortOptions> buildSorting(final Query<T> query, final String uniqueSortKey) {
    final var sortOptions = new ArrayList<SortOptions>();
    final List<Query.Sort> sorts = query.getSort();
    if (sorts != null) {
      sorts.forEach(
          sort -> {
            final Query.Sort.Order order = sort.getOrder();
            if (order.equals(Query.Sort.Order.DESC)) {
              sortOptions.add(queryDSLWrapper.sortOptions(sort.getField(), SortOrder.Desc));
            } else {
              // if not specified always assume ASC order
              sortOptions.add(queryDSLWrapper.sortOptions(sort.getField(), SortOrder.Asc));
            }
          });
    }
    sortOptions.add(queryDSLWrapper.sortOptions(uniqueSortKey, SortOrder.Asc));
    return sortOptions;
  }

  protected void buildPaging(final Query<T> query, final SearchRequest.Builder request) {
    final Object[] searchAfter = query.getSearchAfter();
    if (searchAfter != null) {
      request.searchAfter(CollectionUtil.toSafeListOfStrings(searchAfter));
    }
    request.size(query.getSize());
  }

  protected abstract org.opensearch.client.opensearch._types.query_dsl.Query buildFiltering(
      Query<T> query);

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
