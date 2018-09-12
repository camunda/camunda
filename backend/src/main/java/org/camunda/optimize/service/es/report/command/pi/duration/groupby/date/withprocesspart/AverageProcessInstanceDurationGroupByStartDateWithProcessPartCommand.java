package org.camunda.optimize.service.es.report.command.pi.duration.groupby.date.withprocesspart;

import org.elasticsearch.search.aggregations.Aggregations;

import static org.camunda.optimize.service.es.report.command.pi.duration.ProcessPartQueryUtil.processProcessPartAggregationAsAverage;

public class AverageProcessInstanceDurationGroupByStartDateWithProcessPartCommand extends
  AbstractProcessInstanceDurationGroupByStartDateWithProcessPartCommand {

  @Override
  protected long processAggregationOperation(Aggregations aggs) {
    return processProcessPartAggregationAsAverage(aggs);
  }

}
