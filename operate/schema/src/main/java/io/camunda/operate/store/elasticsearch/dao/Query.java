/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch.dao;

import io.camunda.operate.util.ElasticsearchUtil;
import java.util.Objects;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;

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

  public Query aggregate(String groupName, String fieldName) {
    this.aggregationBuilder = AggregationBuilders.sum(groupName).field(fieldName);
    this.groupName = groupName;

    return this;
  }

  public Query and(Query andQuery) {
    this.queryBuilder = ElasticsearchUtil.joinWithAnd(this.queryBuilder, andQuery.queryBuilder);
    return this;
  }

  public Query or(Query orQuery) {
    this.queryBuilder = ElasticsearchUtil.joinWithOr(this.queryBuilder, orQuery.queryBuilder);
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
  public int hashCode() {
    return Objects.hash(queryBuilder, aggregationBuilder, groupName);
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
  public String toString() {
    return "Query{"
        + "queryBuilder="
        + queryBuilder
        + ", aggregationBuilder="
        + aggregationBuilder
        + ", groupName='"
        + groupName
        + '\''
        + '}';
  }
}
