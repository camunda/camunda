package org.camunda.optimize.service.es.report.command.pi.duration.groupby.none;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.max.InternalMax;

public class MaxProcessInstanceDurationGroupByNoneCommand extends
  AbstractProcessInstanceDurationGroupByNoneCommand<InternalMax>{

  @Override
  protected long processAggregation(InternalMax aggregation) {
    if (Double.isInfinite(aggregation.getValue())){
      return 0L;
    } else {
      return Math.round(aggregation.getValue());
    }
  }

  @Override
  protected AggregationBuilder createAggregationOperation(String aggregationName, String fieldName) {
    return AggregationBuilders
      .max(aggregationName)
      .field(fieldName);
  }
}
