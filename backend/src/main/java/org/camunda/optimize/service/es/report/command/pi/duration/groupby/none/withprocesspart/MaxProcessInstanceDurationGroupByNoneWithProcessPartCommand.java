package org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.withprocesspart;

import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.scripted.ScriptedMetric;


public class MaxProcessInstanceDurationGroupByNoneWithProcessPartCommand extends
  AbstractProcessInstanceDurationGroupByNoneWithProcessPartCommand {

  @Override
  protected long processAggregation(Aggregations aggs) {
    Terms agg = aggs.get(TERMS_AGGRATIONS);
    long max = 0;
    for (Terms.Bucket entry : agg.getBuckets()) {
      Nested foo = entry.getAggregations().get(NESTED_AGGREGATION);
      ScriptedMetric aggregation = foo.getAggregations().get(SCRIPT_AGGRATION);
      Long scriptedResult = (Long) aggregation.aggregation();
      max = max > scriptedResult ? max : scriptedResult;
    }
    return max;
  }

}
