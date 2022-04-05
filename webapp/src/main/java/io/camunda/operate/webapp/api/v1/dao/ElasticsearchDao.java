/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.util.ConversionUtils.stringIsEmpty;

import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort.Order;
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

public abstract class ElasticsearchDao<T> implements SortableDao<T>, PageableDao<T> {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  @Qualifier("esClient")
  protected RestHighLevelClient elasticsearch;

  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  protected OperateProperties operateProperties;

  public void buildSorting(final Query<T> query, final String uniqueSortKey,
      final SearchSourceBuilder searchSourceBuilder) {
    final List<Sort> sorts = query.getSort();
    if (sorts != null) {
      searchSourceBuilder.sort(sorts.stream().map(sort -> {
            final Order order = sort.getOrder();
            final FieldSortBuilder sortBuilder = SortBuilders.fieldSort(sort.getField());
            if (order.equals(Order.DESC)) {
              return sortBuilder.order(SortOrder.DESC);
            } else {
              // if not specified always assume ASC order
              return sortBuilder.order(SortOrder.ASC);
            }
          }
      ).collect(Collectors.toList()));
    }
    // always sort at least by key - needed for searchAfter method of paging
    searchSourceBuilder.sort(SortBuilders.fieldSort(uniqueSortKey).order(SortOrder.ASC));
  }

  public void buildPaging(final Query<T> query, final SearchSourceBuilder searchSourceBuilder) {
    final Object[] searchAfter = query.getSearchAfter();
    if (searchAfter != null) {
      searchSourceBuilder.searchAfter(searchAfter);
    }
    searchSourceBuilder.size(query.getSize());
  }

  protected SearchSourceBuilder buildQueryOn(final Query<T> query,final String uniqueKey,final SearchSourceBuilder searchSourceBuilder) {
    logger.debug("Build query for Elasticsearch from query {}", query);
    buildSorting(query, uniqueKey, searchSourceBuilder);
    buildPaging(query, searchSourceBuilder);
    buildFiltering(query, searchSourceBuilder);
    return searchSourceBuilder;
  }

  protected abstract void buildFiltering(final Query<T> query, final SearchSourceBuilder searchSourceBuilder);

  protected QueryBuilder buildTermQuery(final String name, final String value){
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
      return matchQuery(name,  value).operator(Operator.AND);
    }
    return null;
  }

  protected QueryBuilder buildMatchDateQuery(final String name, final String dateAsString) {
      if (dateAsString != null) {
        // Used to match in different time ranges like hours, minutes etc
        // See: https://www.elastic.co/guide/en/elasticsearch/reference/current/common-options.html#date-math
        return rangeQuery(name)
            .gte(dateAsString)
            .lte(dateAsString).format(operateProperties.getElasticsearch().getDateFormat());
      }
      return null;
  }

}
