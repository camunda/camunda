/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.util.ElasticsearchTenantHelper;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort.Order;
import io.camunda.operate.webapp.api.v1.entities.Results;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class ElasticsearchDao<T> {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired protected ElasticsearchClient esClient;

  @Autowired protected ElasticsearchTenantHelper tenantHelper;

  @Autowired
  @Qualifier("operateObjectMapper")
  protected ObjectMapper objectMapper;

  @Autowired protected OperateDateTimeFormatter dateTimeFormatter;

  private SortOptions toFieldSort(final Sort sort) {
    final var order = (sort.getOrder() == Order.DESC) ? SortOrder.Desc : SortOrder.Asc;

    return SortOptions.of(s -> s.field(f -> f.field(sort.getField()).order(order)));
  }

  protected void buildSorting(
      final Query<T> query,
      final String uniqueSortKey,
      final SearchRequest.Builder searchRequestBuilder) {
    final List<Sort> sorts = query.getSort();
    if (sorts != null) {
      searchRequestBuilder.sort(sorts.stream().map(this::toFieldSort).toList());
    }

    final var uniqueKeySort =
        SortOptions.of(s -> s.field(f -> f.field(uniqueSortKey).order(SortOrder.Asc)));

    searchRequestBuilder.sort(uniqueKeySort);
  }

  protected void buildPaging(
      final Query<T> query, final SearchRequest.Builder searchRequestBuilder) {
    final Object[] searchAfter = query.getSearchAfter();
    if (searchAfter != null) {
      searchRequestBuilder.searchAfter(Arrays.stream(searchAfter).map(FieldValue::of).toList());
    }
    searchRequestBuilder.size(query.getSize());
  }

  protected SearchRequest.Builder buildQueryOn(
      final Query<T> query,
      final String uniqueKey,
      final SearchRequest.Builder searchRequestBuilder,
      final boolean isTenantAware) {
    logger.debug("Build query for Elasticsearch from query {}", query);
    buildSorting(query, uniqueKey, searchRequestBuilder);
    buildPaging(query, searchRequestBuilder);
    buildFiltering(query, searchRequestBuilder, isTenantAware);
    return searchRequestBuilder;
  }

  protected <ResultType> Results<ResultType> searchWithResultsReturn(
      final SearchRequest searchRequest, final Class<ResultType> clazz) throws IOException {

    final var res = esClient.search(searchRequest, clazz);

    final var hits = res.hits().hits();

    if (hits.isEmpty()) {
      return new Results<ResultType>().setTotal(res.hits().total().value());
    }

    final Object[] sortValues = hits.getLast().sort().stream().map(FieldValue::_get).toArray();

    final var items = hits.stream().map(Hit::source).toList();

    return new Results<ResultType>()
        .setTotal(res.hits().total().value())
        .setItems(items)
        .setSortValues(sortValues);
  }

  protected abstract void buildFiltering(
      final Query<T> query,
      final SearchRequest.Builder searchRequestBuilder,
      final boolean isTenantAware);

  protected <V> co.elastic.clients.elasticsearch._types.query_dsl.Query buildIfPresent(
      final String field,
      final V value,
      final BiFunction<String, V, co.elastic.clients.elasticsearch._types.query_dsl.Query>
          builder) {
    return value == null ? null : builder.apply(field, value);
  }

  protected co.elastic.clients.elasticsearch._types.query_dsl.Query buildMatchDateQuery(
      final String name, final String dateAsString) {
    if (dateAsString == null) {
      return null;
    }

    return RangeQuery.of(r -> r.term(t -> t.field(name).gte(dateAsString).lte(dateAsString)))
        ._toQuery();
  }
}
