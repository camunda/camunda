/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.aggregations;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.service.db.report.interpreter.util.AggregationResultMappingUtil;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.PercentilesAggregation;
import org.opensearch.client.opensearch._types.aggregations.TDigestPercentilesAggregate;

public class PercentileAggregationOS extends AggregationStrategyOS {

  private static final String PERCENTILE_AGGREGATION = "percentileAggregation";
  private Double percentileValue;

  public PercentileAggregationOS(final Double percentileValue) {
    this.percentileValue = percentileValue;
  }

  public PercentileAggregationOS() {}

  @Override
  public AggregationDto getAggregationType() {
    return new AggregationDto(AggregationType.PERCENTILE, percentileValue);
  }

  @Override
  public Double getValueForAggregation(
      final String customIdentifier, final Map<String, Aggregate> aggs) {
    final String aggregationName =
        createAggregationName(
            customIdentifier, String.valueOf(percentileValue), PERCENTILE_AGGREGATION);
    final TDigestPercentilesAggregate percentiles = aggs.get(aggregationName).tdigestPercentiles();
    return mapToDoubleOrNull(percentiles, percentileValue);
  }

  @Override
  public Pair<String, Aggregation> createAggregation(
      final String customIdentifier, final Script script, final String... fields) {
    final PercentilesAggregation.Builder builder =
        new PercentilesAggregation.Builder()
            .script(script)
            .tdigest(b -> b.compression(9999))
            .percents(percentileValue);
    AggregationResultMappingUtil.firstField(fields).ifPresent(builder::field);

    return Pair.of(
        createAggregationName(
            customIdentifier, String.valueOf(percentileValue), PERCENTILE_AGGREGATION),
        builder.build()._toAggregation());
  }

  private Double mapToDoubleOrNull(
      final TDigestPercentilesAggregate aggregation, final double percentileValue) {
    final Double percentile =
        Optional.ofNullable(aggregation.values())
            .filter(h -> h.keyed().get(Double.toString(percentileValue)) != null)
            .map(h -> Double.parseDouble(h.keyed().get(Double.toString(percentileValue))))
            .orElse(null);
    return percentile == null || Double.isNaN(percentile) || Double.isInfinite(percentile)
        ? null
        : percentile;
  }
}
