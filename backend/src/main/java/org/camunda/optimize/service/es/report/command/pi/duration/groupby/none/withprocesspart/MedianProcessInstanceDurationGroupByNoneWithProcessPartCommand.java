package org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.withprocesspart;

import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.scripted.ScriptedMetric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.es.report.command.pi.duration.ProcessPartQueryUtil.processProcessPartAggregationAsMedian;


public class MedianProcessInstanceDurationGroupByNoneWithProcessPartCommand extends
 AbstractProcessInstanceDurationGroupByNoneWithProcessPartCommand {

  @Override
  protected long processAggregation(Aggregations aggs) {
    return processProcessPartAggregationAsMedian(aggs);
  }

}
