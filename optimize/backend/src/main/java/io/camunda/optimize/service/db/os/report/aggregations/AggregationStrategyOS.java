/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.aggregations;

import io.camunda.optimize.service.db.report.aggregations.AggregationStrategy;
import io.camunda.optimize.service.db.report.interpreter.util.AggregationResultMappingUtil;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;

public abstract class AggregationStrategyOS extends AggregationStrategy {

  protected abstract Double getValueForAggregation(
      final String customIdentifier, final Map<String, Aggregate> aggs);

  public Double getValue(final Map<String, Aggregate> aggs) {
    return getValue(null, aggs);
  }

  public Double getValue(final String customIdentifier, final Map<String, Aggregate> aggs) {
    return getValueForAggregation(customIdentifier, aggs);
  }

  public Pair<String, Aggregation> createAggregation(final Script script, final String... field) {
    return createAggregation(null, script, field);
  }

  public abstract Pair<String, Aggregation> createAggregation(
      final String customIdentifier, final Script script, final String... field);

  protected Double getValue(final double value, final Map<String, JsonData> meta) {
    /* It is a suggested workaround from ES (and OS inherits it) to distinguish between 0 and null
     * values based on doc count in a bucket (if doc count is 0 value is supposed to be null,
     * otherwise it is real 0). As they say in ES this way of interpreting 0 value is caused by
     * ES client design decision.
     * However, considering we are not passing around buckets in reporting but aggregates, in order
     * to apply the suggested workaround we populate aggregate's meta field "isNull" with true
     * if bucket's doc count is 0 and aggregate value is also 0 and false otherwise. See
     * https://discuss.elastic.co/t/java-api-client-single-metric-aggregation-zero-or-null-deserializer/356207
     */
    return Optional.ofNullable(meta.get("isNull"))
            .flatMap(json -> Optional.ofNullable(json.to(Boolean.class)))
            .orElse(false)
        ? null
        : AggregationResultMappingUtil.mapToDoubleOrNull(value);
  }
}
