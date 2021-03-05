/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.elasticsearch.search.aggregations.metrics.ParsedTDigestPercentiles;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ElasticsearchAggregationResultMappingUtil {

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
