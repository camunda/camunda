package org.camunda.optimize.service.es.report.command.process.flownode.duration;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.max.ParsedMax;

import static org.elasticsearch.search.aggregations.AggregationBuilders.max;

public class MaxFlowNodeDurationByFlowNodeCommand extends AbstractFlowNodeDurationByFlowNodeCommand<ParsedMax> {

  @Override
  protected AggregationBuilder addOperation(String aggregationName, String field) {
    return max(aggregationName)
      .field(field);
  }

  @Override
  protected Long processOperationAggregation(ParsedMax aggregation) {
    if (Double.isInfinite(aggregation.getValue())){
      return 0L;
    } else {
      return Math.round(aggregation.getValue());
    }
  }
}
