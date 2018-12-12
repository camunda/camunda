package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.withoutprocesspart;

import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.AbstractProcessInstanceDurationGroupByStartDateCommand;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.InternalTDigestPercentiles;

public class MedianProcessInstanceDurationGroupByStartDateCommand extends
  AbstractProcessInstanceDurationGroupByStartDateCommand {

  @Override
  protected long processAggregationOperation(Aggregations aggs) {
    InternalTDigestPercentiles aggregation = aggs.get(DURATION_AGGREGATION);
    if (Double.isNaN(aggregation.percentile(50))){
      return 0L;
    } else {
      return Math.round(aggregation.percentile(50));
    }
  }

  @Override
  protected AggregationBuilder createAggregationOperation() {
    return AggregationBuilders
      .percentiles(DURATION_AGGREGATION)
      .percentiles(50)
      .field(ProcessInstanceType.DURATION);
  }

}
