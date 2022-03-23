/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.elasticsearch.search.aggregations.metrics.ParsedTDigestPercentiles;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ElasticsearchAggregationResultMappingUtil {

  public static Double mapToDoubleOrNull(final ParsedTDigestPercentiles aggregation, final double percentileValue) {
    double percentile = aggregation.percentile(percentileValue);
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
