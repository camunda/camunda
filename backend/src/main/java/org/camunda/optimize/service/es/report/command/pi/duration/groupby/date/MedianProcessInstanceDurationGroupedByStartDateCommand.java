package org.camunda.optimize.service.es.report.command.pi.duration.groupby.date;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.InternalTDigestPercentiles;

public class MedianProcessInstanceDurationGroupedByStartDateCommand extends
  AbstractProcessInstanceDurationGroupedByStartDateCommand<InternalTDigestPercentiles> {

  @Override
  protected long processAggregationOperation(InternalTDigestPercentiles aggregation) {
    if (Double.isNaN(aggregation.percentile(50))) {
      return 0L;
    } else {
      return Math.round(aggregation.percentile(50));
    }
  }

  @Override
  protected AggregationBuilder createAggregationOperation(String aggregationName, String fieldName) {
    return AggregationBuilders
      .percentiles(aggregationName)
      .percentiles(50)
      .field(fieldName);
  }

}
