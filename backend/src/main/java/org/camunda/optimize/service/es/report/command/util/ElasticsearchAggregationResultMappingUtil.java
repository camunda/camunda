/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.util;

import org.elasticsearch.search.aggregations.metrics.ParsedSingleValueNumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.ParsedTDigestPercentiles;

public class ElasticsearchAggregationResultMappingUtil {

  private ElasticsearchAggregationResultMappingUtil() {
  }

  public static Double mapToDouble(final ParsedSingleValueNumericMetricsAggregation aggregation) {
    return mapToDouble(aggregation.value());
  }

  public static Double mapToDouble(final Double value) {
    if (Double.isInfinite(value) || Double.isNaN(value)) {
      return 0.0;
    } else {
      return value;
    }
  }

  public static Double mapToDouble(final ParsedTDigestPercentiles aggregation) {
    double median = aggregation.percentile(50);
    if (Double.isNaN(median) || Double.isInfinite(median)) {
      return 0.0;
    } else {
      return median;
    }
  }

  public static Double mapToDoubleOrNull(final ParsedTDigestPercentiles aggregation) {
    double median = aggregation.percentile(50);
    if (Double.isNaN(median) || Double.isInfinite(median)) {
      return null;
    } else {
      return median;
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
