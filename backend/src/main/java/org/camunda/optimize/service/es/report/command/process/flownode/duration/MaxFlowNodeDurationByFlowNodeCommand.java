package org.camunda.optimize.service.es.report.command.process.flownode.duration;

import org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil;
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
    return ElasticsearchAggregationResultMappingUtil.mapToLong(aggregation);
  }
}
