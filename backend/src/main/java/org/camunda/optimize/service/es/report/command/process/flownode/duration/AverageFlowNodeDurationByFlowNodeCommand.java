package org.camunda.optimize.service.es.report.command.process.flownode.duration;

import org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil;
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
  protected Long processOperationAggregation(final ParsedAvg aggregation) {
    return ElasticsearchAggregationResultMappingUtil.mapToLong(aggregation);
  }
}
