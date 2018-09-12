package org.camunda.optimize.service.es.report.command.pi.duration.groupby.date.withprocesspart;

import org.elasticsearch.search.aggregations.Aggregations;

import static org.camunda.optimize.service.es.report.command.pi.duration.ProcessPartQueryUtil.processProcessPartAggregationAsMin;


public class MinProcessInstanceDurationGroupByStartDateWithProcessPartCommand extends
  AbstractProcessInstanceDurationGroupByStartDateWithProcessPartCommand {

  @Override
  protected long processAggregationOperation(Aggregations aggs) {
    return processProcessPartAggregationAsMin(aggs);
  }

}
