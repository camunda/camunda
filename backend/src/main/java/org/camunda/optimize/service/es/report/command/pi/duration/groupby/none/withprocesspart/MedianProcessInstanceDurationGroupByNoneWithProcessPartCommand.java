package org.camunda.optimize.service.es.report.command.pi.duration.groupby.none.withprocesspart;

import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.scripted.ScriptedMetric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class MedianProcessInstanceDurationGroupByNoneWithProcessPartCommand extends
 AbstractProcessInstanceDurationGroupByNoneWithProcessPartCommand {

  @Override
  protected long processAggregation(Aggregations aggs) {
    Terms agg = aggs.get(TERMS_AGGRATIONS);
    List<Long> allDurations = new ArrayList<>();
    for (Terms.Bucket entry : agg.getBuckets()) {
      Nested foo = entry.getAggregations().get(NESTED_AGGREGATION);
      ScriptedMetric aggregation = foo.getAggregations().get(SCRIPT_AGGRATION);
      Long scriptedResult = (Long) aggregation.aggregation();
      allDurations.add(scriptedResult);
    }
    Collections.sort(allDurations);
    return allDurations.isEmpty()? 0 : allDurations.get(allDurations.size()/2);
  }

}
