package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.withoutprocesspart;

import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.AbstractProcessInstanceDurationByVariableCommand;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.min.InternalMin;
import org.elasticsearch.search.aggregations.metrics.min.ParsedMin;

import static org.elasticsearch.search.aggregations.AggregationBuilders.min;

public class MinProcessInstanceDurationByVariableCommand
  extends AbstractProcessInstanceDurationByVariableCommand {

  @Override
  protected long processAggregationOperation(Aggregations aggs) {
    ParsedMin aggregation = aggs.get(DURATION_AGGREGATION);
    if (Double.isInfinite(aggregation.getValue())){
      return 0L;
    } else {
      return Math.round(aggregation.getValue());
    }
  }

  @Override
  protected AggregationBuilder createAggregationOperation() {
    return min(DURATION_AGGREGATION)
      .field(ProcessInstanceType.DURATION);
  }
}
