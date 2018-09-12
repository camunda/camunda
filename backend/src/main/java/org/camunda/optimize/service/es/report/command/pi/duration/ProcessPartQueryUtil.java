package org.camunda.optimize.service.es.report.command.pi.duration;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.scripted.ScriptedMetricAggregationBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.scriptedMetric;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.scripted.ScriptedMetric;


public class ProcessPartQueryUtil {

  protected static final String MI_BODY = "multiInstanceBody";
  protected static final String SCRIPT_AGGREGATION = "scriptAggregation";
  protected static final String NESTED_AGGREGATION = "nestedAggregation";
  protected static final String TERMS_AGGREGATIONS = "termsAggregations";

  public static long processProcessPartAggregationAsAverage(Aggregations aggs) {
    Terms agg = aggs.get(TERMS_AGGREGATIONS);
    long sum = 0;
    for (Terms.Bucket entry : agg.getBuckets()) {
      Nested nested = entry.getAggregations().get(NESTED_AGGREGATION);
      ScriptedMetric scriptedMetric = nested.getAggregations().get(SCRIPT_AGGREGATION);
      Long scriptedResult = (Long) scriptedMetric.aggregation();
      sum += scriptedResult;
    }
    return sum/ Math.max(agg.getBuckets().size(), 1);
  }

  public static long processProcessPartAggregationAsMin(Aggregations aggs) {
    Terms agg = aggs.get(TERMS_AGGREGATIONS);
    long min = Long.MAX_VALUE;
    for (Terms.Bucket entry : agg.getBuckets()) {
      Nested nested = entry.getAggregations().get(NESTED_AGGREGATION);
      ScriptedMetric scriptedMetric = nested.getAggregations().get(SCRIPT_AGGREGATION);
      Long scriptedResult = (Long) scriptedMetric.aggregation();
      min = min < scriptedResult ? min : scriptedResult;
    }
    return min == Long.MAX_VALUE ? 0 : min;
  }

  public static long processProcessPartAggregationAsMax(Aggregations aggs) {
    Terms agg = aggs.get(TERMS_AGGREGATIONS);
    long max = 0;
    for (Terms.Bucket entry : agg.getBuckets()) {
      Nested nested = entry.getAggregations().get(NESTED_AGGREGATION);
      ScriptedMetric scriptedMetric = nested.getAggregations().get(SCRIPT_AGGREGATION);
      Long scriptedResult = (Long) scriptedMetric.aggregation();
      max = max > scriptedResult ? max : scriptedResult;
    }
    return max;
  }

  public static BoolQueryBuilder addProcessPartQuery(BoolQueryBuilder boolQueryBuilder,
                                              String startFlowNodeId,
                                              String endFlowNodeId) {
    String termPath = ProcessInstanceType.EVENTS + "." + ProcessInstanceType.ACTIVITY_ID;
    boolQueryBuilder.must(nestedQuery(
      ProcessInstanceType.EVENTS,
      termQuery(termPath, startFlowNodeId),
      ScoreMode.None)
    );
    boolQueryBuilder.must(nestedQuery(
      ProcessInstanceType.EVENTS,
        termQuery(termPath, endFlowNodeId),
      ScoreMode.None)
    );
    return boolQueryBuilder;
  }

  public static long processProcessPartAggregationAsMedian(Aggregations aggs) {
    Terms agg = aggs.get(TERMS_AGGREGATIONS);
    List<Long> allDurations = new ArrayList<>();
    for (Terms.Bucket entry : agg.getBuckets()) {
      Nested nested = entry.getAggregations().get(NESTED_AGGREGATION);
      ScriptedMetric scriptedMetric = nested.getAggregations().get(SCRIPT_AGGREGATION);
      Long scriptedResult = (Long) scriptedMetric.aggregation();
      allDurations.add(scriptedResult);
    }
    Collections.sort(allDurations);
    return allDurations.isEmpty()? 0 : allDurations.get(allDurations.size()/2);
  }

  public static AggregationBuilder createProcessPartAggregation(String startFlowNodeId, String endFlowNodeId) {
    Map<String, Object> params = new HashMap<>();
    params.put("_agg", new HashMap<>());
    params.put("startFlowNodeId", startFlowNodeId);
    params.put("endFlowNodeId", endFlowNodeId);

    ScriptedMetricAggregationBuilder findStartAndEndDatesForEvents = scriptedMetric(SCRIPT_AGGREGATION)
      .initScript(createInitScript())
      .mapScript(createMapScript())
      .combineScript(createCombineScript())
      .reduceScript(getReduceScript())
      .params(params);
    NestedAggregationBuilder searchThroughTheEvents =
      nested(NESTED_AGGREGATION, ProcessInstanceType.EVENTS);
    return
      terms(TERMS_AGGREGATIONS)
      .field(ProcessInstanceType.PROCESS_INSTANCE_ID)
      .subAggregation(
        searchThroughTheEvents
          .subAggregation(
            findStartAndEndDatesForEvents
        )
      );
  }

  private static Script createInitScript() {
    return new Script("params._agg.starts = []; params._agg.ends = []");
  }

  private static Script createMapScript() {
    return new Script(
      "if(doc['events.activityId'].value == params.startFlowNodeId && doc['events.startDate'].value != null) {" +
        "long startDateInMillis = doc['events.startDate'].value.getMillis();" +
        "params._agg.starts.add(startDateInMillis);" +
      "} else if(doc['events.activityId'].value == params.endFlowNodeId && doc['events.endDate'].value != null) {" +
        "long endDateInMillis = doc['events.endDate'].value.getMillis();" +
        "params._agg.ends.add(endDateInMillis);" +
      "}"
    );
  }

  private static Script createCombineScript() {
    return new Script(
        "long minStart = params._agg.starts.stream().min(Long::compareTo).get(); " +
          "long closestEnd = params._agg.ends.stream()" +
            ".min(Comparator.comparingDouble(v -> Math.abs(v - minStart))).get();" +
          "return closestEnd-minStart;"
    );
  }

  private static Script getReduceScript() {
    return new Script("long sum = 0; for (a in params._aggs) { sum += a } return sum");
  }
}
