/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es.dao;

import io.camunda.tasklist.util.ElasticsearchUtil;
import java.util.Objects;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;

public class Query {

  private QueryBuilder queryBuilder = null;
  private AggregationBuilder aggregationBuilder = null;
  private String groupName = null;

  public static Query whereEquals(String field, String value) {
    final Query instance = new Query();
    instance.queryBuilder = QueryBuilders.termsQuery(field, value);

    return instance;
  }

  public static Query range(String field, Object gte, Object lte) {
    final Query instance = new Query();

    RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(field);
    if (gte != null) {
      rangeQueryBuilder = rangeQueryBuilder.gte(gte);
    }

    if (lte != null) {
      rangeQueryBuilder = rangeQueryBuilder.lte(lte);
    }

    instance.queryBuilder = rangeQueryBuilder;

    return instance;
  }

  public Query aggregate(String groupName, String fieldName, int limit) {
    final TermsAggregationBuilder aggregation = AggregationBuilders.terms(groupName);
    aggregation.field(fieldName);
    aggregation.size(limit);
    this.aggregationBuilder = aggregation;
    this.groupName = groupName;

    return this;
  }

  public Query aggregate(String groupName, String fieldName) {
    return aggregate(groupName, fieldName, Integer.MAX_VALUE);
  }

  public Query and(Query andQuery) {
    this.queryBuilder = ElasticsearchUtil.joinWithAnd(this.queryBuilder, andQuery.queryBuilder);
    return this;
  }

  QueryBuilder getQueryBuilder() {
    return this.queryBuilder;
  }

  AggregationBuilder getAggregationBuilder() {
    return this.aggregationBuilder;
  }

  String getGroupName() {
    return this.groupName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Query)) {
      return false;
    }
    final Query query = (Query) o;
    return Objects.equals(queryBuilder, query.queryBuilder)
        && Objects.equals(aggregationBuilder, query.aggregationBuilder)
        && Objects.equals(groupName, query.groupName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(queryBuilder, aggregationBuilder, groupName);
  }
}
