package org.camunda.optimize.service.es.report.command.process.flownode.duration;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.avg.InternalAvg;

import static org.elasticsearch.search.aggregations.AggregationBuilders.avg;

public class AverageFlowNodeDurationByFlowNodeCommand extends AbstractFlowNodeDurationByFlowNodeCommand<InternalAvg> {

  @Override
  protected AggregationBuilder addOperation(String aggregationName, String field) {
    return avg(aggregationName)
                  .field(field);
  }

  @Override
  protected Long processOperationAggregation(InternalAvg aggregation) {
    return Math.round(aggregation.getValue());
  }
}
