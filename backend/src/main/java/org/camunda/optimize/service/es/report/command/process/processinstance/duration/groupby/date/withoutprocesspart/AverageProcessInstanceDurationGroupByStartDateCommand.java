package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.withoutprocesspart;

import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.AbstractProcessInstanceDurationGroupByStartDateCommand;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.avg.InternalAvg;

public class AverageProcessInstanceDurationGroupByStartDateCommand extends
  AbstractProcessInstanceDurationGroupByStartDateCommand {

  @Override
  protected long processAggregationOperation(Aggregations aggs) {
    InternalAvg aggregation = aggs.get(DURATION_AGGREGATION);
    return Math.round(aggregation.getValue());
  }

  @Override
  protected AggregationBuilder createAggregationOperation() {
    return AggregationBuilders
          .avg(DURATION_AGGREGATION)
          .field(ProcessInstanceType.DURATION);
  }

}
