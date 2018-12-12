package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.withprocesspart;

import org.camunda.optimize.service.es.report.command.process.processinstance.duration.ProcessPartQueryUtil;
import org.elasticsearch.search.aggregations.Aggregations;


public class MedianProcessInstanceDurationGroupByStartDateWithProcessPartCommand extends
  AbstractProcessInstanceDurationGroupByStartDateWithProcessPartCommand {

  @Override
  protected long processAggregationOperation(Aggregations aggs) {
    return ProcessPartQueryUtil.processProcessPartAggregationAsMedian(aggs);
  }

}
