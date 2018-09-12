package org.camunda.optimize.service.es.report.command.pi.duration.groupby.date.withprocesspart;

import org.elasticsearch.search.aggregations.Aggregations;

import static org.camunda.optimize.service.es.report.command.pi.duration.ProcessPartQueryUtil.processProcessPartAggregationAsMedian;


public class MedianProcessInstanceDurationGroupByStartDateWithProcessPartCommand extends
  AbstractProcessInstanceDurationGroupByStartDateWithProcessPartCommand {

  @Override
  protected long processAggregationOperation(Aggregations aggs) {
    return processProcessPartAggregationAsMedian(aggs);
  }

}
