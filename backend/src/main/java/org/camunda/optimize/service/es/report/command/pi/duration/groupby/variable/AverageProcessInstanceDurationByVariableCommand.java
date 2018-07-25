package org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.avg.InternalAvg;

import static org.elasticsearch.search.aggregations.AggregationBuilders.avg;

public class AverageProcessInstanceDurationByVariableCommand
  extends AbstractProcessInstanceDurationByVariableCommand<InternalAvg> {

  @Override
  protected long processAggregationOperation(InternalAvg aggregation) {
    return Math.round(aggregation.getValue());
  }

  @Override
  protected AggregationBuilder createAggregationOperation(String aggregationName, String fieldName) {
    return avg(aggregationName)
      .field(fieldName);
  }
}
