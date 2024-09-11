/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.aggregations;

import static io.camunda.optimize.service.db.es.report.interpreter.util.ElasticsearchAggregationResultMappingUtil.mapToDoubleOrNull;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation.Builder.ContainerBuilder;
import co.elastic.clients.elasticsearch._types.aggregations.AverageAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AvgAggregate;
import co.elastic.clients.util.Pair;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import java.util.Map;

public class AvgAggregation extends AggregationStrategy<AverageAggregation.Builder> {

  private static final String AVG_AGGREGATION = "avgAggregation";

  @Override
  public Pair<String, ContainerBuilder> createAggregationBuilderForAggregation(
      final String customIdentifier, Script script, String... field) {
    Aggregation.Builder builder = new Aggregation.Builder();
    return Pair.of(
        createAggregationName(customIdentifier, AVG_AGGREGATION),
        builder.avg(
            a -> {
              a.script(script);
              if (field != null && field.length != 0) {
                a.field(field[0]);
              }
              return a;
            }));
  }

  @Override
  protected Double getValueForAggregation(String customIdentifier, Map<String, Aggregate> aggs) {
    final AvgAggregate aggregation =
        aggs.get(createAggregationName(customIdentifier, AVG_AGGREGATION)).avg();
    return mapToDoubleOrNull(aggregation.value());
  }

  @Override
  public AggregationDto getAggregationType() {
    return new AggregationDto(AggregationType.AVERAGE);
  }
}
