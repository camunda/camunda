/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static io.camunda.operate.util.ConversionUtils.stringIsEmpty;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.ElasticsearchTenantHelper;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort.Order;
import io.camunda.operate.webapp.api.v1.entities.Results;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class ElasticsearchDao<T> {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired protected ElasticsearchClient es8Client;

  @Autowired protected ElasticsearchTenantHelper tenantHelper;

  @Autowired protected TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired
  @Qualifier("esClient")
  protected RestHighLevelClient elasticsearch;

  @Autowired
  @Qualifier("operateObjectMapper")
  protected ObjectMapper objectMapper;

  @Autowired protected OperateDateTimeFormatter dateTimeFormatter;

  private SortOptions toFieldSort(final Sort sort) {
    final var order =
        (sort.getOrder() == Order.DESC)
            ? co.elastic.clients.elasticsearch._types.SortOrder.Desc
            : co.elastic.clients.elasticsearch._types.SortOrder.Asc;

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
        SortOptions.of(
            s ->
                s.field(
                    f ->
                        f.field(uniqueSortKey)
                            .order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)));

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

    final var res = es8Client.search(searchRequest, clazz);

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

  /**
   * @deprecated Use {@link #buildFiltering(Query, SearchRequest.Builder, boolean)} instead
   */
  @Deprecated
  protected void buildFiltering(
      final Query<T> query, final SearchSourceBuilder searchSourceBuilder) {
    // Default implementation for backward compatibility
    throw new UnsupportedOperationException(
        "Legacy buildFiltering method not implemented. Override buildFiltering(Query, SearchRequest.Builder, boolean) instead.");
  }

  /**
   * @deprecated Use {@link #buildSorting(Query, String, SearchRequest.Builder)} instead
   */
  @Deprecated
  protected void buildSorting(
      final Query<T> query,
      final String uniqueSortKey,
      final SearchSourceBuilder searchSourceBuilder) {
    final List<Sort> sorts = query.getSort();
    if (sorts != null) {
      searchSourceBuilder.sort(
          sorts.stream()
              .map(
                  sort -> {
                    final Order order = sort.getOrder();
                    final FieldSortBuilder sortBuilder = SortBuilders.fieldSort(sort.getField());
                    if (order.equals(Order.DESC)) {
                      return sortBuilder.order(SortOrder.DESC);
                    } else {
                      return sortBuilder.order(SortOrder.ASC);
                    }
                  })
              .collect(Collectors.toList()));
    }
    searchSourceBuilder.sort(SortBuilders.fieldSort(uniqueSortKey).order(SortOrder.ASC));
  }

  /**
   * @deprecated Use {@link #buildPaging(Query, SearchRequest.Builder)} instead
   */
  @Deprecated
  protected void buildPaging(final Query<T> query, final SearchSourceBuilder searchSourceBuilder) {
    final Object[] searchAfter = query.getSearchAfter();
    if (searchAfter != null) {
      searchSourceBuilder.searchAfter(searchAfter);
    }
    searchSourceBuilder.size(query.getSize());
  }

  /**
   * @deprecated Use {@link #buildQueryOn(Query, String, SearchRequest.Builder, boolean)} instead
   */
  @Deprecated
  protected SearchSourceBuilder buildQueryOn(
      final Query<T> query, final String uniqueKey, final SearchSourceBuilder searchSourceBuilder) {
    logger.debug("Build query for Elasticsearch from query {}", query);
    buildSorting(query, uniqueKey, searchSourceBuilder);
    buildPaging(query, searchSourceBuilder);
    buildFiltering(query, searchSourceBuilder);
    return searchSourceBuilder;
  }

  /**
   * @deprecated Use {@link #buildIfPresent(String, Object, BiFunction)} instead
   */
  @Deprecated
  protected QueryBuilder buildTermQuery(final String name, final String value) {
    if (!stringIsEmpty(value)) {
      return termQuery(name, value);
    }
    return null;
  }

  /**
   * @deprecated Use {@link #buildIfPresent(String, Object, BiFunction)} instead
   */
  @Deprecated
  protected QueryBuilder buildTermQuery(final String name, final Integer value) {
    if (value != null) {
      return termQuery(name, value);
    }
    return null;
  }

  /**
   * @deprecated Use {@link #buildIfPresent(String, Object, BiFunction)} instead
   */
  @Deprecated
  protected QueryBuilder buildTermQuery(final String name, final Long value) {
    if (value != null) {
      return termQuery(name, value);
    }
    return null;
  }

  /**
   * @deprecated Use {@link #buildIfPresent(String, Object, BiFunction)} instead
   */
  @Deprecated
  protected QueryBuilder buildTermQuery(final String name, final Boolean value) {
    if (value != null) {
      return termQuery(name, value);
    }
    return null;
  }

  /**
   * @deprecated Use {@link #buildIfPresent(String, Object, BiFunction)} instead
   */
  @Deprecated
  protected QueryBuilder buildMatchQuery(final String name, final String value) {
    if (value != null) {
      return matchQuery(name, value).operator(Operator.AND);
    }
    return null;
  }

  /**
   * @deprecated Use {@link #buildMatchDateQuery(String, String)} with ES8 client instead
   */
  @Deprecated
  protected QueryBuilder buildMatchDateQuery_Legacy(final String name, final String dateAsString) {
    if (dateAsString != null) {
      return rangeQuery(name)
          .gte(dateAsString)
          .lte(dateAsString)
          .format(dateTimeFormatter.getApiDateTimeFormatString());
    }
    return null;
  }

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
