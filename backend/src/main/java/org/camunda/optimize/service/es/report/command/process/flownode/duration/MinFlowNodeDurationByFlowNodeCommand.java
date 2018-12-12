package org.camunda.optimize.service.es.report.command.process.flownode.duration;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.min.InternalMin;

import static org.elasticsearch.search.aggregations.AggregationBuilders.min;

public class MinFlowNodeDurationByFlowNodeCommand extends AbstractFlowNodeDurationByFlowNodeCommand<InternalMin> {

  @Override
  protected AggregationBuilder addOperation(String aggregationName, String field) {
    return min(aggregationName)
      .field(field);
  }

  @Override
  protected Long processOperationAggregation(InternalMin aggregation) {
    return Math.round(aggregation.getValue());
  }
}
