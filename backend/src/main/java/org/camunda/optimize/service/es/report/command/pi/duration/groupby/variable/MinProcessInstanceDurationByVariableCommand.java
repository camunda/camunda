package org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.min.InternalMin;

import static org.elasticsearch.search.aggregations.AggregationBuilders.min;

public class MinProcessInstanceDurationByVariableCommand
  extends AbstractProcessInstanceDurationByVariableCommand<InternalMin> {

  @Override
  protected long processAggregationOperation(InternalMin aggregation) {
    if (Double.isInfinite(aggregation.getValue())){
      return 0L;
    } else {
      return Math.round(aggregation.getValue());
    }
  }

  @Override
  protected AggregationBuilder createAggregationOperation(String aggregationName, String fieldName) {
    return min(aggregationName)
      .field(fieldName);
  }
}
