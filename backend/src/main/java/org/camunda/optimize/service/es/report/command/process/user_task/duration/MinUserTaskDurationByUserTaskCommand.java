package org.camunda.optimize.service.es.report.command.process.user_task.duration;

import org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil;
import org.elasticsearch.search.aggregations.metrics.min.ParsedMin;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

import static org.elasticsearch.search.aggregations.AggregationBuilders.min;

public abstract class MinUserTaskDurationByUserTaskCommand extends AbstractUserTaskDurationByUserTaskCommand<ParsedMin> {

  @Override
  protected ValuesSourceAggregationBuilder<?, ?> getDurationAggregationBuilder(final String aggregationName) {
    return min(aggregationName);
  }

  @Override
  protected Long mapDurationByTaskIdAggregationResult(final ParsedMin aggregation) {
    return ElasticsearchAggregationResultMappingUtil.mapToLong(aggregation);
  }
}
