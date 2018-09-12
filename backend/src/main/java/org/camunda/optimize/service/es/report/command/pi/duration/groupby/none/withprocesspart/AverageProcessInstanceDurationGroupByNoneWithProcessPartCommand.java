package org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.withprocesspart;

import org.elasticsearch.search.aggregations.Aggregations;

import static org.camunda.optimize.service.es.report.command.pi.duration.ProcessPartQueryUtil.processProcessPartAggregationAsAverage;


public class AverageProcessInstanceDurationGroupByNoneWithProcessPartCommand extends
  AbstractProcessInstanceDurationGroupByNoneWithProcessPartCommand {

  @Override
  protected long processAggregation(Aggregations aggs) {
    return processProcessPartAggregationAsAverage(aggs);
  }

}