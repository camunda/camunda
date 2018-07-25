package org.camunda.optimize.service.es.report.command.pi.duration.groupby.date;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.avg.InternalAvg;

public class AverageProcessInstanceDurationGroupedByStartDateCommand extends
  AbstractProcessInstanceDurationGroupedByStartDateCommand<InternalAvg> {

  @Override
  protected long processAggregationOperation(InternalAvg aggregation) {
    return Math.round(aggregation.getValue());
  }

  @Override
  protected AggregationBuilder createAggregationOperation(String aggregationName, String fieldName) {
    return AggregationBuilders
          .avg(aggregationName)
          .field(fieldName);
  }

}
