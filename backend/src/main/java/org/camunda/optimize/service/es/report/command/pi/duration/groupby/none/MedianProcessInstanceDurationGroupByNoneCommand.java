package org.camunda.optimize.service.es.report.command.pi.duration.groupby.none;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.InternalTDigestPercentiles;

public class MedianProcessInstanceDurationGroupByNoneCommand extends
  AbstractProcessInstanceDurationGroupByNoneCommand<InternalTDigestPercentiles>{

  @Override
  protected long processAggregation(InternalTDigestPercentiles aggregation) {
    if (Double.isNaN(aggregation.percentile(50))){
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
