package org.camunda.optimize.service.es.report.command.process.user_task.duration;

import org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil;
import org.elasticsearch.search.aggregations.metrics.avg.ParsedAvg;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

import static org.elasticsearch.search.aggregations.AggregationBuilders.avg;

public abstract class AverageUserTaskDurationByUserTaskCommand extends AbstractUserTaskDurationByUserTaskCommand<ParsedAvg> {

  @Override
  protected ValuesSourceAggregationBuilder<?, ?> getDurationAggregationBuilder(final String aggregationName) {
    return avg(aggregationName);
  }

  @Override
  protected Long mapDurationByTaskIdAggregationResult(final ParsedAvg aggregation) {
    return ElasticsearchAggregationResultMappingUtil.mapToLong(aggregation);
  }

}
