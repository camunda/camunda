package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.withoutprocesspart;

import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.AbstractProcessInstanceDurationGroupByNoneCommand;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.max.InternalMax;

public class MaxProcessInstanceDurationGroupByNoneCommand extends
  AbstractProcessInstanceDurationGroupByNoneCommand {

  @Override
  protected long processAggregation(Aggregations aggs) {
    InternalMax aggregation = aggs.get(DURATION_AGGREGATION);
    if (Double.isInfinite(aggregation.getValue())){
      return 0L;
    } else {
      return Math.round(aggregation.getValue());
    }
  }

  @Override
  protected AggregationBuilder createAggregationOperation(String fieldName) {
    return AggregationBuilders
      .max(DURATION_AGGREGATION)
      .field(fieldName);
  }
}
