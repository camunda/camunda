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
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.ElasticsearchTenantHelper;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort.Order;
import java.util.Arrays;
import java.util.List;
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

  @Autowired
  @Qualifier("esClient")
  protected RestHighLevelClient elasticsearch;

  @Autowired protected ElasticsearchClient es8Client;

  @Autowired protected ElasticsearchTenantHelper tenantHelper;

  @Autowired protected TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired
  @Qualifier("operateObjectMapper")
  protected ObjectMapper objectMapper;

  @Autowired protected OperateDateTimeFormatter dateTimeFormatter;

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
                      // if not specified always assume ASC order
                      return sortBuilder.order(SortOrder.ASC);
                    }
                  })
              .collect(Collectors.toList()));
    }
    // always sort at least by key - needed for searchAfter method of paging
    searchSourceBuilder.sort(SortBuilders.fieldSort(uniqueSortKey).order(SortOrder.ASC));
  }

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

  protected void buildPaging(final Query<T> query, final SearchSourceBuilder searchSourceBuilder) {
    final Object[] searchAfter = query.getSearchAfter();
    if (searchAfter != null) {
      searchSourceBuilder.searchAfter(searchAfter);
    }
    searchSourceBuilder.size(query.getSize());
  }

  protected void buildPaging(
      final Query<T> query, final SearchRequest.Builder searchRequestBuilder) {
    final Object[] searchAfter = query.getSearchAfter();
    if (searchAfter != null) {
      searchRequestBuilder.searchAfter(Arrays.stream(searchAfter).map(FieldValue::of).toList());
    }
    searchRequestBuilder.size(query.getSize());
  }

  protected SearchSourceBuilder buildQueryOn(
      final Query<T> query, final String uniqueKey, final SearchSourceBuilder searchSourceBuilder) {
    logger.debug("Build query for Elasticsearch from query {}", query);
    buildSorting(query, uniqueKey, searchSourceBuilder);
    buildPaging(query, searchSourceBuilder);
    buildFiltering(query, searchSourceBuilder);
    return searchSourceBuilder;
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

  protected abstract void buildFiltering(
      final Query<T> query, final SearchSourceBuilder searchSourceBuilder);

  protected abstract void buildFiltering(
      final Query<T> query,
      final SearchRequest.Builder searchRequestBuilder,
      final boolean isTenantAware);

  protected QueryBuilder buildTermQuery(final String name, final String value) {
    if (!stringIsEmpty(value)) {
      return termQuery(name, value);
    }
    return null;
  }

  protected QueryBuilder buildTermQuery(final String name, final Integer value) {
    if (value != null) {
      return termQuery(name, value);
    }
    return null;
  }

  protected QueryBuilder buildTermQuery(final String name, final Long value) {
    if (value != null) {
      return termQuery(name, value);
    }
    return null;
  }

  protected QueryBuilder buildTermQuery(final String name, final Boolean value) {
    if (value != null) {
      return termQuery(name, value);
    }
    return null;
  }

  protected QueryBuilder buildMatchQuery(final String name, final String value) {
    if (value != null) {
      return matchQuery(name, value).operator(Operator.AND);
    }
    return null;
  }

  protected QueryBuilder buildMatchDateQuery(final String name, final String dateAsString) {
    if (dateAsString != null) {
      // Used to match in different time ranges like hours, minutes etc
      // See:
      // https://www.elastic.co/guide/en/elasticsearch/reference/current/common-options.html#date-math
      return rangeQuery(name)
          .gte(dateAsString)
          .lte(dateAsString)
          .format(dateTimeFormatter.getApiDateTimeFormatString());
    }
    return null;
  }
}
