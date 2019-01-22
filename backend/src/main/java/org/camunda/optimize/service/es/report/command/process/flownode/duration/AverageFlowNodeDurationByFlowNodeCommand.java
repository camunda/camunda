package org.camunda.optimize.service.es.report.command.process.flownode.duration;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.avg.ParsedAvg;

import static org.elasticsearch.search.aggregations.AggregationBuilders.avg;

public class AverageFlowNodeDurationByFlowNodeCommand extends AbstractFlowNodeDurationByFlowNodeCommand<ParsedAvg> {

  @Override
  protected AggregationBuilder addOperation(String aggregationName, String field) {
    return avg(aggregationName)
      .field(field);
  }

  @Override
  protected Long processOperationAggregation(ParsedAvg aggregation) {
    if (Double.isInfinite(aggregation.getValue())) {
      return 0L;
    } else {
      return Math.round(aggregation.getValue());
    }
  }
}
