package org.camunda.optimize.service.es.report.command.process.user_task.duration;

import org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.ParsedTDigestPercentiles;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

import static org.elasticsearch.search.aggregations.AggregationBuilders.percentiles;

public abstract class MedianUserTaskDurationByUserTaskCommand
  extends AbstractUserTaskDurationByUserTaskCommand<ParsedTDigestPercentiles> {

  @Override
  protected ValuesSourceAggregationBuilder<?, ?> getDurationAggregationBuilder(final String aggregationName) {
    return percentiles(aggregationName).percentiles(50);
  }

  @Override
  protected Long mapDurationByTaskIdAggregationResult(final ParsedTDigestPercentiles aggregation) {
    return ElasticsearchAggregationResultMappingUtil.mapToLong(aggregation);
  }
}
