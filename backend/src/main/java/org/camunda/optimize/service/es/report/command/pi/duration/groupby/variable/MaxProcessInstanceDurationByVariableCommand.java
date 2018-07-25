package org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.max.InternalMax;

public class MaxProcessInstanceDurationByVariableCommand
  extends AbstractProcessInstanceDurationByVariableCommand<InternalMax> {

  @Override
  protected long processAggregationOperation(InternalMax aggregation) {
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
