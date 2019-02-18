package org.camunda.optimize.service.es.report.command.process.flownode.duration;

import org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.min.ParsedMin;

import static org.elasticsearch.search.aggregations.AggregationBuilders.min;

public class MinFlowNodeDurationByFlowNodeCommand extends AbstractFlowNodeDurationByFlowNodeCommand<ParsedMin> {

  @Override
  protected AggregationBuilder addOperation(String aggregationName, String field) {
    return min(aggregationName)
      .field(field);
  }

  @Override
  protected Long processOperationAggregation(ParsedMin aggregation) {
    return ElasticsearchAggregationResultMappingUtil.mapToLong(aggregation);
  }
}
