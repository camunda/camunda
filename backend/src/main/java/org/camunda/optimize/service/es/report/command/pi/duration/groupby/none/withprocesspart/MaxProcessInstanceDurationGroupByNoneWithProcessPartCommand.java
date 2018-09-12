package org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.withprocesspart;

import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.scripted.ScriptedMetric;

import static org.camunda.optimize.service.es.report.command.pi.duration.ProcessPartQueryUtil.processProcessPartAggregationAsMax;


public class MaxProcessInstanceDurationGroupByNoneWithProcessPartCommand extends
  AbstractProcessInstanceDurationGroupByNoneWithProcessPartCommand {

  @Override
  protected long processAggregation(Aggregations aggs) {
    return processProcessPartAggregationAsMax(aggs);
  }

}
