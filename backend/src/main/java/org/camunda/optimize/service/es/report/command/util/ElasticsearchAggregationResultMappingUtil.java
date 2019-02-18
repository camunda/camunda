package org.camunda.optimize.service.es.report.command.util;

import org.elasticsearch.search.aggregations.metrics.ParsedSingleValueNumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.ParsedTDigestPercentiles;

public class ElasticsearchAggregationResultMappingUtil {

  private ElasticsearchAggregationResultMappingUtil() {
  }

  public static Long mapToLong(final ParsedSingleValueNumericMetricsAggregation aggregation) {
    if (Double.isInfinite(aggregation.value())) {
      return 0L;
    } else {
      return Math.round(aggregation.value());
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

}
