package org.camunda.optimize.service.es.report.command.process.flownode.duration;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.max.InternalMax;

import static org.elasticsearch.search.aggregations.AggregationBuilders.max;

public class MaxFlowNodeDurationByFlowNodeCommand extends AbstractFlowNodeDurationByFlowNodeCommand<InternalMax> {

  @Override
  protected AggregationBuilder addOperation(String aggregationName, String field) {
    return max(aggregationName)
      .field(field);
  }

  @Override
  protected Long processOperationAggregation(InternalMax aggregation) {
    return Math.round(aggregation.getValue());
  }
}
