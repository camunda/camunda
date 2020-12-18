/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.ScriptedMetric;
import org.elasticsearch.search.aggregations.metrics.ScriptedMetricAggregationBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.dto.optimize.ReportConstants.NO_DATA_AVAILABLE_RESULT;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.scriptedMetric;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessPartQueryUtil {

  private static final String SCRIPT_AGGREGATION = "scriptAggregation";
  private static final String NESTED_AGGREGATION = "nestedAggregation";

  public static Double processProcessPartAggregationOperations(Aggregations aggs) {
    Nested nested = aggs.get(NESTED_AGGREGATION);
    ScriptedMetric scriptedMetric = nested.getAggregations().get(SCRIPT_AGGREGATION);

    if (scriptedMetric.aggregation() instanceof Number) {
      final Number scriptedResult = (Number) scriptedMetric.aggregation();
      return scriptedResult.doubleValue();
    }
    return NO_DATA_AVAILABLE_RESULT;
  }

  public static BoolQueryBuilder addProcessPartQuery(BoolQueryBuilder boolQueryBuilder,
                                                     String startFlowNodeId,
                                                     String endFlowNodeId) {
    String termPath = ProcessInstanceIndex.EVENTS + "." + ProcessInstanceIndex.ACTIVITY_ID;
    boolQueryBuilder.must(nestedQuery(
      ProcessInstanceIndex.EVENTS,
      termQuery(termPath, startFlowNodeId),
      ScoreMode.None
                          )
    );
    boolQueryBuilder.must(nestedQuery(
      ProcessInstanceIndex.EVENTS,
      termQuery(termPath, endFlowNodeId),
      ScoreMode.None
                          )
    );
    return boolQueryBuilder;
  }

  public static AggregationBuilder createProcessPartAggregation(String startFlowNodeId, String endFlowNodeId,
                                                                final AggregationType aggregationType) {
    Map<String, Object> params = new HashMap<>();
    params.put("startFlowNodeId", startFlowNodeId);
    params.put("endFlowNodeId", endFlowNodeId);
    params.put("aggregationType", aggregationType.getId());

    ScriptedMetricAggregationBuilder findStartAndEndDatesForEvents = scriptedMetric(SCRIPT_AGGREGATION)
      .initScript(createInitScript())
      .mapScript(createMapScript())
      .combineScript(createCombineScript())
      .reduceScript(getReduceScript())
      .params(params);
    NestedAggregationBuilder searchThroughTheEvents =
      nested(NESTED_AGGREGATION, ProcessInstanceIndex.EVENTS);
    return searchThroughTheEvents
      .subAggregation(findStartAndEndDatesForEvents);
  }

  private static Script createInitScript() {
    // @formatter:off
    return new Script(
      "state.procInstIdToStartDates = new HashMap();" +
      "state.procInstIdToEndDates = new HashMap();"
    );
    // @formatter:on
  }

  private static Script createMapScript() {
    // @formatter:off
    return new Script(
      "def processInstanceId = doc['events.processInstanceId'].value;" +
      "if(doc['events.activityId'].value == params.startFlowNodeId && " +
          "doc['events.startDate'].size() != 0 && doc['events.startDate'].value != null && " +
          "doc['events.startDate'].value.toInstant().toEpochMilli() != 0) {" +
        "long startDateInMillis = doc['events.startDate'].value.toInstant().toEpochMilli();" +
        "state.procInstIdToStartDates.putIfAbsent(processInstanceId, new ArrayList());" +
        "state.procInstIdToStartDates.get(processInstanceId).add(startDateInMillis);" +
      "} else if(doc['events.activityId'].value == params.endFlowNodeId && " +
          "doc['events.endDate'].size() != 0 && doc['events.endDate'].value != null && " +
          "doc['events.endDate'].value.toInstant().toEpochMilli() != 0) {" +
        "long endDateInMillis = doc['events.endDate'].value.toInstant().toEpochMilli();" +
        "state.procInstIdToEndDates.putIfAbsent(processInstanceId, new ArrayList());" +
        "state.procInstIdToEndDates.get(processInstanceId).add(endDateInMillis);" +
      "}"
    );
    // @formatter:on
  }

  private static Script createCombineScript() {
    // @formatter:off
    return new Script(
      "double sum = 0.0;" +
      "long count = 0;" +
      "double min = Double.MAX_VALUE;" +
      "double max = Double.MIN_VALUE;" +
      "for (procInstIdToStartDatesEntry in state.procInstIdToStartDates.entrySet()) {  " +
        "def endDates = state.procInstIdToEndDates.getOrDefault(procInstIdToStartDatesEntry.getKey(), new ArrayList());" +
        "def startDates = procInstIdToStartDatesEntry.getValue();" +
        "if (!startDates.isEmpty() && !endDates.isEmpty()) {" +
          "long minStartDate = startDates.stream().min(Long::compareTo).get(); " +
          "List endDatesLargerMinStartDate = endDates.stream().filter(e -> e >= minStartDate).collect(Collectors.toList());" +
          "if (!endDatesLargerMinStartDate.isEmpty()) {" +
            "long closestEndDate = endDatesLargerMinStartDate.stream()" +
              ".min(Comparator.comparingDouble(v -> Math.abs(v - minStartDate))).get();" +
            "double duration = closestEndDate - minStartDate;" +
            "min = duration < min? duration : min;" +
            "max = duration > max? duration : max;" +
            "sum += duration;" +
            "count += 1;" +
          "}" +
        "}" +
      "}" +
      "Map result = new HashMap();" +
      // the params object is only available in Elasticsearch from 7.3 so we have to use this workaround
      // see https://github.com/elastic/elasticsearch/issues/42046
      "result.put('aggregationType', params.aggregationType);" +
      "result.put('sum', sum);" +
      "result.put('count', count);" +
      "result.put('min', min);" +
      "result.put('max', max);" +
      "return result;"
    );
    // @formatter:on
  }

  private static Script getReduceScript() {
    // @formatter:off
    return new Script(
      "if (states == null || states.isEmpty()) {" +
        "return null;" +
      "}" +
      "double sum = 0; " +
      "long count = 0; " +
      "double min = Double.MAX_VALUE;" +
      "double max = Double.MIN_VALUE;" +
      "def aggregationType = 'avg';" +
      "for (a in states) { " +
        "if (a != null) {" +
          "sum += a.get('sum');" +
          "count += a.get('count');" +
          "min = a.get('min') < min? a.get('min') : min;" +
          "max = a.get('max') > max? a.get('max') : max;" +
          // it will be the same type for each shard but since we need to make sure that we assign the type
          // for a state that's non null. Until Elasticsearch 7.3 we can't access the params object directly.
          // See https://github.com/elastic/elasticsearch/issues/42046 for the origin of the problem.
          "aggregationType = a.get('aggregationType');" +
        "}" +
      "}" +
      "if (count == 0) {" +
        "return null;" +
      "}" +
      // return correct result depending on the aggregation type
      "if (aggregationType == 'avg') {" +
        "return sum / count;" +
      "} else if (aggregationType == 'min') {" +
        "return min;" +
      "} else if (aggregationType == 'max') {" +
        "return max;" +
      "} else if (aggregationType == 'sum') {" +
        "return sum;" +
      "} else {" +
        "Debug.explain('Aggregation type ' + aggregationType + 'is not supported!');" +
      "}"
    );
    // @formatter:on
  }
}
