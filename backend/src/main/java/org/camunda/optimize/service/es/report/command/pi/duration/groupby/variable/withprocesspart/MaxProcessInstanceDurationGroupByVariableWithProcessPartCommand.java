package org.camunda.optimize.service.es.report.command.pi.duration.groupby.variable.withprocesspart;

import org.elasticsearch.search.aggregations.Aggregations;

import static org.camunda.optimize.service.es.report.command.pi.duration.ProcessPartQueryUtil.processProcessPartAggregationAsMax;


public class MaxProcessInstanceDurationGroupByVariableWithProcessPartCommand extends
  AbstractProcessInstanceDurationGroupByVariableWithProcessPartCommand {

  @Override
  protected long processAggregationOperation(Aggregations aggs) {
    return processProcessPartAggregationAsMax(aggs);
  }

}
