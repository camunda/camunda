package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.withprocesspart;

import org.camunda.optimize.service.es.report.command.process.processinstance.duration.ProcessPartQueryUtil;
import org.elasticsearch.search.aggregations.Aggregations;


public class MinProcessInstanceDurationGroupByVariableWithProcessPartCommand extends
  AbstractProcessInstanceDurationGroupByVariableWithProcessPartCommand {

  @Override
  protected long processAggregationOperation(Aggregations aggs) {
    return ProcessPartQueryUtil.processProcessPartAggregationAsMin(aggs);
  }

}
