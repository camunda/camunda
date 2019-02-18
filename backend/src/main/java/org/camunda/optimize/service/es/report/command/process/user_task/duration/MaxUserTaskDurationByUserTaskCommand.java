package org.camunda.optimize.service.es.report.command.process.user_task.duration;

import org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil;
import org.elasticsearch.search.aggregations.metrics.max.ParsedMax;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

import static org.elasticsearch.search.aggregations.AggregationBuilders.max;

public abstract class MaxUserTaskDurationByUserTaskCommand extends AbstractUserTaskDurationByUserTaskCommand<ParsedMax> {

  @Override
  protected ValuesSourceAggregationBuilder<?, ?> getDurationAggregationBuilder(final String aggregationName) {
    return max(aggregationName);
  }

  @Override
  protected Long mapDurationByTaskIdAggregationResult(final ParsedMax aggregation) {
    return ElasticsearchAggregationResultMappingUtil.mapToLong(aggregation);
  }
}
