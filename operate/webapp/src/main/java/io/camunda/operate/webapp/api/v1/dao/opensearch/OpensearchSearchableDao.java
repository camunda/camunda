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
import java.util.List;
import java.util.Objects;
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
      OpensearchQueryDSLWrapper queryDSLWrapper,
      OpensearchRequestDSLWrapper requestDSLWrapper,
      RichOpenSearchClient richOpenSearchClient) {
    this.queryDSLWrapper = queryDSLWrapper;
    this.requestDSLWrapper = requestDSLWrapper;
    this.richOpenSearchClient = richOpenSearchClient;
  }

  public Results<T> search(Query<T> query) {
    final var request = buildSearchRequest(query);

    buildSorting(query, getUniqueSortKey(), request);
    buildFiltering(query, request);
    buildPaging(query, request);

    try {
      final HitsMetadata<R> results =
          richOpenSearchClient.doc().search(request, getInternalDocumentModelClass()).hits();

      return formatHitsIntoResults(results);
    } catch (Exception e) {
      throw new ServerException("Error in reading incidents", e);
    }
  }

  protected SearchRequest.Builder buildSearchRequest(Query<T> query) {
    return requestDSLWrapper
        .searchRequestBuilder(getIndexName())
        .query(queryDSLWrapper.withTenantCheck(queryDSLWrapper.matchAll()));
  }

  protected abstract String getUniqueSortKey();

  protected abstract Class<R> getInternalDocumentModelClass();

  protected abstract String getIndexName();

  protected void buildSorting(Query<T> query, String uniqueSortKey, SearchRequest.Builder request) {
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

  protected void buildPaging(Query<T> query, SearchRequest.Builder request) {
    final Object[] searchAfter = query.getSearchAfter();
    if (searchAfter != null) {
      request.searchAfter(CollectionUtil.toSafeListOfStrings(searchAfter));
    }
    request.size(query.getSize());
  }

  protected abstract void buildFiltering(Query<T> query, SearchRequest.Builder request);

  protected Results<T> formatHitsIntoResults(HitsMetadata<R> results) {
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
