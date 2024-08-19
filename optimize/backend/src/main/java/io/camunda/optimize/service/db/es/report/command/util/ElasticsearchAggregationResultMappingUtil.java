/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.util;

import org.elasticsearch.search.aggregations.metrics.ParsedTDigestPercentiles;

public class ElasticsearchAggregationResultMappingUtil {

  private ElasticsearchAggregationResultMappingUtil() {}

  public static Double mapToDoubleOrNull(
      final ParsedTDigestPercentiles aggregation, final double percentileValue) {
    final double percentile = aggregation.percentile(percentileValue);
    if (Double.isNaN(percentile) || Double.isInfinite(percentile)) {
      return null;
    } else {
      return percentile;
    }
  }

  public static Double mapToDoubleOrNull(final Double value) {
    if (Double.isInfinite(value) || Double.isNaN(value)) {
      return null;
    } else {
      return value;
    }
  }
}
