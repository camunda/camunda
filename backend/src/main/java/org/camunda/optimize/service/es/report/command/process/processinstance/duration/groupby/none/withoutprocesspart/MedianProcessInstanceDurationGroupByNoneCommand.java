package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.withoutprocesspart;

import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.AbstractProcessInstanceDurationGroupByNoneCommand;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.InternalTDigestPercentiles;

public class MedianProcessInstanceDurationGroupByNoneCommand extends
  AbstractProcessInstanceDurationGroupByNoneCommand {

  @Override
  protected long processAggregation(Aggregations aggs) {
    InternalTDigestPercentiles aggregation = aggs.get(DURATION_AGGREGATION);
    if (Double.isNaN(aggregation.percentile(50))){
      return 0L;
    } else {
      return Math.round(aggregation.percentile(50));
    }
  }

  @Override
  protected AggregationBuilder createAggregationOperation(String fieldName) {
    return AggregationBuilders
      .percentiles(DURATION_AGGREGATION)
      .percentiles(50)
      .field(fieldName);
  }
}
