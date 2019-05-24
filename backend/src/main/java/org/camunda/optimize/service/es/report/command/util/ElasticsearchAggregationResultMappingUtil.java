/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.util;

import org.elasticsearch.search.aggregations.metrics.ParsedSingleValueNumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.ParsedTDigestPercentiles;

public class ElasticsearchAggregationResultMappingUtil {

  private ElasticsearchAggregationResultMappingUtil() {
  }

  public static Long mapToLong(final ParsedSingleValueNumericMetricsAggregation aggregation) {
    return mapToLong(aggregation.value());
  }

  public static Long mapToLong(final Double value) {
    if (Double.isInfinite(value)) {
      return 0L;
    } else {
      return Math.round(value);
    }
  }

  public static Long mapToLong(final ParsedTDigestPercentiles aggregation) {
    double median = aggregation.percentile(50);
    if (Double.isNaN(median) || Double.isInfinite(median)) {
      return 0L;
    } else {
      return Math.round(median);
    }
  }

  public static Long mapToLongOrNull(final ParsedTDigestPercentiles aggregation) {
    double median = aggregation.percentile(50);
    if (Double.isNaN(median) || Double.isInfinite(median)) {
      return null;
    } else {
      return Math.round(median);
    }
  }

  public static Long mapToLongOrNull(final Double value) {
    if (Double.isInfinite(value)) {
      return null;
    } else {
      return Math.round(value);
    }
  }


}
