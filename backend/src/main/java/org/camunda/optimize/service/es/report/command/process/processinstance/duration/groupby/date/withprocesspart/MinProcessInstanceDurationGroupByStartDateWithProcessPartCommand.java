package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.withprocesspart;

import org.camunda.optimize.service.es.report.command.process.processinstance.duration.ProcessPartQueryUtil;
import org.elasticsearch.search.aggregations.Aggregations;


public class MinProcessInstanceDurationGroupByStartDateWithProcessPartCommand extends
  AbstractProcessInstanceDurationGroupByStartDateWithProcessPartCommand {

  @Override
  protected long processAggregationOperation(Aggregations aggs) {
    return ProcessPartQueryUtil.processProcessPartAggregationAsMin(aggs);
  }

}
