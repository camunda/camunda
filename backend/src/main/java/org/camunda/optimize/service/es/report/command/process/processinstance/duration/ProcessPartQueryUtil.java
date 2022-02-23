/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration;

import com.google.common.collect.ImmutableMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.text.StringSubstitutor;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
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
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.dto.optimize.ReportConstants.NO_DATA_AVAILABLE_RESULT;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.scriptedMetric;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessPartQueryUtil {

  private static final String SCRIPT_AGGREGATION = "scriptAggregation";
  private static final String NESTED_AGGREGATION = "nestedAggregation";

  public static Aggregations getProcessPartAggregations(final Aggregations aggs) {
    return ((Nested) aggs.get(NESTED_AGGREGATION)).getAggregations();
  }

  public static Double getProcessPartAggregationResult(final Aggregations aggs,
                                                       final AggregationDto aggregationType) {
    Nested nested = aggs.get(NESTED_AGGREGATION);
    ScriptedMetric scriptedMetric = nested.getAggregations().get(getScriptAggregationName(aggregationType));

    if (scriptedMetric.aggregation() instanceof Number) {
      final Number scriptedResult = (Number) scriptedMetric.aggregation();
      return scriptedResult.doubleValue();
    }
    return NO_DATA_AVAILABLE_RESULT;
  }

  public static BoolQueryBuilder addProcessPartQuery(final BoolQueryBuilder boolQueryBuilder,
                                                     final String startFlowNodeId,
                                                     final String endFlowNodeId) {
    String termPath = ProcessInstanceIndex.FLOW_NODE_INSTANCES + "." + ProcessInstanceIndex.FLOW_NODE_ID;
    boolQueryBuilder.must(nestedQuery(
      ProcessInstanceIndex.FLOW_NODE_INSTANCES,
      termQuery(termPath, startFlowNodeId),
      ScoreMode.None
    ));
    boolQueryBuilder.must(nestedQuery(
      ProcessInstanceIndex.FLOW_NODE_INSTANCES,
      termQuery(termPath, endFlowNodeId),
      ScoreMode.None
    ));
    return boolQueryBuilder;
  }

  public static AggregationBuilder createProcessPartAggregation(final String startFlowNodeId,
                                                                final String endFlowNodeId,
                                                                final List<AggregationDto> aggregationTypes) {
    final NestedAggregationBuilder nestedFlowNodeAggregation = nested(
      NESTED_AGGREGATION,
      ProcessInstanceIndex.FLOW_NODE_INSTANCES
    );
    aggregationTypes.forEach(aggregationType -> {
      final Map<String, Object> params = new HashMap<>();
      params.put("startFlowNodeId", startFlowNodeId);
      params.put("endFlowNodeId", endFlowNodeId);
      params.put("aggregationType", aggregationType.getType().getId());

      final ScriptedMetricAggregationBuilder findStartAndEndDatesForEvents =
        scriptedMetric(getScriptAggregationName(aggregationType))
          .initScript(createInitScript())
          .mapScript(createMapScript())
          .combineScript(createCombineScript())
          .reduceScript(getReduceScript())
          .params(params);
      nestedFlowNodeAggregation.subAggregation(findStartAndEndDatesForEvents);
    });

    return nestedFlowNodeAggregation;
  }

  private static String getScriptAggregationName(final AggregationDto aggregationType) {
    final String aggName = aggregationType.getType() == AggregationType.PERCENTILE
      ? aggregationType.getType().getId() + String.valueOf(aggregationType.getValue())
      .replace(".", "_")
      : aggregationType.getType().getId();
    return String.join("_", SCRIPT_AGGREGATION, aggName);
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
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("flowNodeProcessInstanceIdField", FLOW_NODE_INSTANCES + "." + PROCESS_INSTANCE_ID)
        .put("flowNodeIdField", FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID)
        .put("flowNodeStartDateField", FLOW_NODE_INSTANCES + "." + FLOW_NODE_START_DATE)
        .put("flowNodeEndDateField", FLOW_NODE_INSTANCES + "." + FLOW_NODE_END_DATE)
        .build()
    );

    // @formatter:off
    return new Script(substitutor.replace(
      "def processInstanceId = doc['${flowNodeProcessInstanceIdField}'].value;" +
      "if(doc['${flowNodeIdField}'].value == params.startFlowNodeId && " +
          "doc['${flowNodeStartDateField}'].size() != 0 && doc['${flowNodeStartDateField}'].value != null && " +
          "doc['${flowNodeStartDateField}'].value.toInstant().toEpochMilli() != 0) {" +
        "long startDateInMillis = doc['${flowNodeStartDateField}'].value.toInstant().toEpochMilli();" +
        "state.procInstIdToStartDates.putIfAbsent(processInstanceId, new ArrayList());" +
        "state.procInstIdToStartDates.get(processInstanceId).add(startDateInMillis);" +
      "} else if(doc['${flowNodeIdField}'].value == params.endFlowNodeId && " +
          "doc['${flowNodeEndDateField}'].size() != 0 && doc['${flowNodeEndDateField}'].value != null && " +
          "doc['${flowNodeEndDateField}'].value.toInstant().toEpochMilli() != 0) {" +
        "long endDateInMillis = doc['${flowNodeEndDateField}'].value.toInstant().toEpochMilli();" +
        "state.procInstIdToEndDates.putIfAbsent(processInstanceId, new ArrayList());" +
        "state.procInstIdToEndDates.get(processInstanceId).add(endDateInMillis);" +
      "}"
    ));
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
        "}" +
      "}" +
      "if (count == 0) {" +
        "return null;" +
      "}" +
      // return correct result depending on the aggregation type
      "if (params.aggregationType == 'avg') {" +
        "return sum / count;" +
      "} else if (params.aggregationType == 'min') {" +
        "return min;" +
      "} else if (params.aggregationType == 'max') {" +
        "return max;" +
      "} else if (params.aggregationType == 'sum') {" +
        "return sum;" +
      "} else {" +
        "Debug.explain('Aggregation type ' + params.aggregationType + ' is not supported!');" +
      "}"
    );
    // @formatter:on
  }
}
