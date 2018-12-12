package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.withprocesspart;

import org.elasticsearch.search.aggregations.Aggregations;

import static org.camunda.optimize.service.es.report.command.process.processinstance.duration.ProcessPartQueryUtil.processProcessPartAggregationAsMedian;


public class MedianProcessInstanceDurationGroupByNoneWithProcessPartCommand extends
 AbstractProcessInstanceDurationGroupByNoneWithProcessPartCommand {

  @Override
  protected long processAggregation(Aggregations aggs) {
    return processProcessPartAggregationAsMedian(aggs);
  }

}
