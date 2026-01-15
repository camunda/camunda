/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch.dao;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithOr;
import static io.camunda.operate.util.ElasticsearchUtil.termsQuery;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.UntypedRangeQuery;
import co.elastic.clients.json.JsonData;
import java.util.Objects;

public class Query {

  private co.elastic.clients.elasticsearch._types.query_dsl.Query esQuery = null;
  private String groupName = null;
  private Aggregation aggregation = null;

  public static Query whereEquals(final String field, final String value) {
    final Query instance = new Query();
    instance.esQuery = termsQuery(field, value);
    return instance;
  }

  public static Query range(final String field, final Object gte, final Object lt) {
    final Query instance = new Query();

    final UntypedRangeQuery.Builder untypedBuilder = new UntypedRangeQuery.Builder();
    untypedBuilder.field(field);
    if (gte != null) {
      untypedBuilder.gte(JsonData.of(gte));
    }
    if (lt != null) {
      untypedBuilder.lt(JsonData.of(lt));
    }

    final RangeQuery.Builder rangeBuilder = new RangeQuery.Builder();
    rangeBuilder.untyped(untypedBuilder.build());

    instance.esQuery =
        co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
            q -> q.range(rangeBuilder.build()));

    return instance;
  }

  public Query aggregate(final String aggregationName, final String fieldName) {
    groupName = aggregationName;
    aggregation = Aggregation.of(a -> a.sum(s -> s.field(fieldName)));

    return this;
  }

  public Query and(final Query andQuery) {
    esQuery = joinWithAnd(esQuery, andQuery.esQuery);
    return this;
  }

  public Query or(final Query orQuery) {
    esQuery = joinWithOr(esQuery, orQuery.esQuery);
    return this;
  }

  co.elastic.clients.elasticsearch._types.query_dsl.Query getEsQuery() {
    return esQuery;
  }

  String getGroupName() {
    return groupName;
  }

  Aggregation getAggregation() {
    return aggregation;
  }

  @Override
  public int hashCode() {
    // Use string representations for hashCode since ES8 client objects don't implement proper
    // equals/hashCode
    return Objects.hash(
        esQuery != null ? esQuery.toString() : null,
        groupName,
        aggregation != null ? aggregation.toString() : null);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Query)) {
      return false;
    }
    final Query query = (Query) o;
    // Compare string representations since ES8 client objects don't implement proper equals()
    return Objects.equals(
            esQuery != null ? esQuery.toString() : null,
            query.esQuery != null ? query.esQuery.toString() : null)
        && Objects.equals(groupName, query.groupName)
        && Objects.equals(
            aggregation != null ? aggregation.toString() : null,
            query.aggregation != null ? query.aggregation.toString() : null);
  }

  @Override
  public String toString() {
    return "Query{"
        + "esQuery="
        + esQuery
        + ", groupName='"
        + groupName
        + '\''
        + ", aggregation="
        + aggregation
        + '}';
  }
}
