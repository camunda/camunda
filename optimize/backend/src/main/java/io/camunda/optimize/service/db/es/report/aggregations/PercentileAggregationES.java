/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.aggregations;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.PercentilesAggregation.Builder;
import co.elastic.clients.elasticsearch._types.aggregations.TDigestPercentilesAggregate;
import co.elastic.clients.util.Pair;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import java.util.Map;
<<<<<<< HEAD
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
=======
import java.util.Optional;
>>>>>>> 03df3535 (fix: sanitise aggregation names during outlier analysis to avoid illegal characters)

@AllArgsConstructor
@NoArgsConstructor
public class PercentileAggregationES extends AggregationStrategyES<Builder> {

  private static final String PERCENTILE_AGGREGATION = "percentileAggregation";

  private final Double percentileValue;

<<<<<<< HEAD
  @Override
  public Double getValueForAggregation(
      final String customIdentifier, final Map<String, Aggregate> aggs) {
    final TDigestPercentilesAggregate percentiles =
        aggs.get(
                createAggregationName(
                    customIdentifier, String.valueOf(percentileValue), PERCENTILE_AGGREGATION))
            .tdigestPercentiles();
    return AggregationResultMappingUtilES.mapToDoubleOrNull(percentiles, percentileValue);
=======
  public PercentileAggregationES(final Double percentileValue) {
    this.percentileValue = percentileValue;
>>>>>>> 03df3535 (fix: sanitise aggregation names during outlier analysis to avoid illegal characters)
  }

  @Override
  public Pair<String, Aggregation.Builder.ContainerBuilder> createAggregationBuilderForAggregation(
      final String customIdentifier, final Script script, final String... field) {
    final Aggregation.Builder builder = new Aggregation.Builder();
    return Pair.of(
        createAggregationName(
            customIdentifier, String.valueOf(percentileValue), PERCENTILE_AGGREGATION),
        builder.percentiles(
            a -> {
              a.script(script).percents(percentileValue);
              if (field != null && field.length != 0) {
                a.field(field[0]);
              }
              return a;
            }));
  }

  @Override
  public Double getValueForAggregation(
      final String customIdentifier, final Map<String, Aggregate> aggs) {
    final TDigestPercentilesAggregate percentiles =
        aggs.get(
                createAggregationName(
                    customIdentifier, String.valueOf(percentileValue), PERCENTILE_AGGREGATION))
            .tdigestPercentiles();
    return mapToDoubleOrNull(percentiles, percentileValue);
  }

  @Override
  public AggregationDto getAggregationType() {
    return new AggregationDto(AggregationType.PERCENTILE, percentileValue);
  }

  private Double mapToDoubleOrNull(
      final TDigestPercentilesAggregate aggregation, final double percentileValue) {
    final Double percentile =
        Optional.ofNullable(aggregation.values())
            .filter(h -> h.keyed().get(Double.toString(percentileValue)) != null)
            .map(h -> Double.parseDouble(h.keyed().get(Double.toString(percentileValue))))
            .orElse(null);
    if (percentile == null || Double.isNaN(percentile) || Double.isInfinite(percentile)) {
      return null;
    } else {
      return percentile;
    }
  }
}
