/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.util;

import static co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders.scriptedMetric;
import static io.camunda.optimize.dto.optimize.ReportConstants.NO_DATA_AVAILABLE_RESULT;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.NestedAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.ScriptedMetricAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.json.JsonData;
import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.service.db.report.interpreter.util.AbstractProcessPartQueryUtil;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.text.StringSubstitutor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessPartQueryUtilES extends AbstractProcessPartQueryUtil {
  public static Map<String, Aggregate> getProcessPartAggregations(
      final Map<String, Aggregate> aggs) {
    return aggs.get(NESTED_AGGREGATION).nested().aggregations();
  }

  public static Double getProcessPartAggregationResult(
      final Map<String, Aggregate> aggs, final AggregationDto aggregationType) {
    final NestedAggregate nested = aggs.get(NESTED_AGGREGATION).nested();
    final ScriptedMetricAggregate scriptedMetric =
        nested.aggregations().get(getScriptAggregationName(aggregationType)).scriptedMetric();
    try {
      return scriptedMetric.value().to(Double.class);
    } catch (final IllegalStateException i) {
      return NO_DATA_AVAILABLE_RESULT;
    }
  }

  public static BoolQuery.Builder addProcessPartQuery(
      final BoolQuery.Builder boolQueryBuilder,
      final String startFlowNodeId,
      final String endFlowNodeId) {
    final String termPath =
        ProcessInstanceIndex.FLOW_NODE_INSTANCES + "." + ProcessInstanceIndex.FLOW_NODE_ID;
    boolQueryBuilder.must(
        m ->
            m.nested(
                n ->
                    n.path(ProcessInstanceIndex.FLOW_NODE_INSTANCES)
                        .scoreMode(ChildScoreMode.None)
                        .query(q -> q.term(t -> t.field(termPath).value(startFlowNodeId)))));
    boolQueryBuilder.must(
        m ->
            m.nested(
                n ->
                    n.path(ProcessInstanceIndex.FLOW_NODE_INSTANCES)
                        .scoreMode(ChildScoreMode.None)
                        .query(q -> q.term(t -> t.field(termPath).value(endFlowNodeId)))));
    return boolQueryBuilder;
  }

  public static Map<String, Aggregation.Builder.ContainerBuilder> createProcessPartAggregation(
      final String startFlowNodeId,
      final String endFlowNodeId,
      final List<AggregationDto> aggregationTypes) {
    final Aggregation.Builder.ContainerBuilder nestedFlowNodeAggregation =
        new Aggregation.Builder().nested(n -> n.path(ProcessInstanceIndex.FLOW_NODE_INSTANCES));
    aggregationTypes.forEach(
        aggregationType -> {
          final Map<String, JsonData> params = new HashMap<>();
          params.put("startFlowNodeId", JsonData.of(startFlowNodeId));
          params.put("endFlowNodeId", JsonData.of(endFlowNodeId));
          params.put("aggregationType", JsonData.of(aggregationType.getType().getId()));

          final String scriptAggregationName = getScriptAggregationName(aggregationType);
          final Aggregation findStartAndEndDatesForEvents =
              scriptedMetric(
                  s ->
                      s.initScript(createInitScript())
                          .mapScript(createMapScript())
                          .combineScript(createCombineScript())
                          .reduceScript(getReduceScript())
                          .params(params));
          nestedFlowNodeAggregation.aggregations(
              scriptAggregationName, findStartAndEndDatesForEvents);
        });

    return Map.of(NESTED_AGGREGATION, nestedFlowNodeAggregation);
  }

  private static Script createInitScript() {
    // @formatter:off
    return Script.of(
        i ->
            i.source(
                ("state.procInstIdToStartDates = new HashMap();"
                    + "state.procInstIdToEndDates = new HashMap();")));
    // @formatter:on
  }

  private static Script createMapScript() {
    final StringSubstitutor substitutor =
        new StringSubstitutor(
            ImmutableMap.<String, String>builder()
                .put(
                    "flowNodeProcessInstanceIdField",
                    FLOW_NODE_INSTANCES + "." + PROCESS_INSTANCE_ID)
                .put("flowNodeIdField", FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID)
                .put("flowNodeStartDateField", FLOW_NODE_INSTANCES + "." + FLOW_NODE_START_DATE)
                .put("flowNodeEndDateField", FLOW_NODE_INSTANCES + "." + FLOW_NODE_END_DATE)
                .build());

    // @formatter:off
    return Script.of(
        i ->
            i.source(
                substitutor.replace(
                    "def processInstanceId = doc['${flowNodeProcessInstanceIdField}'].value;"
                        + "if(doc['${flowNodeIdField}'].value == params.startFlowNodeId && "
                        + "doc['${flowNodeStartDateField}'].size() != 0 && doc['${flowNodeStartDateField}'].value != null && "
                        + "doc['${flowNodeStartDateField}'].value.toInstant().toEpochMilli() != 0) {"
                        + "long startDateInMillis = doc['${flowNodeStartDateField}'].value.toInstant().toEpochMilli();"
                        + "state.procInstIdToStartDates.putIfAbsent(processInstanceId, new ArrayList());"
                        + "state.procInstIdToStartDates.get(processInstanceId).add(startDateInMillis);"
                        + "} else if(doc['${flowNodeIdField}'].value == params.endFlowNodeId && "
                        + "doc['${flowNodeEndDateField}'].size() != 0 && doc['${flowNodeEndDateField}'].value != null && "
                        + "doc['${flowNodeEndDateField}'].value.toInstant().toEpochMilli() != 0) {"
                        + "long endDateInMillis = doc['${flowNodeEndDateField}'].value.toInstant().toEpochMilli();"
                        + "state.procInstIdToEndDates.putIfAbsent(processInstanceId, new ArrayList());"
                        + "state.procInstIdToEndDates.get(processInstanceId).add(endDateInMillis);"
                        + "}")));
    // @formatter:on
  }

  private static Script createCombineScript() {
    // @formatter:off
    return Script.of(
        i ->
            i.source(
                "double sum = 0.0;"
                    + "long count = 0;"
                    + "double min = Double.MAX_VALUE;"
                    + "double max = Double.MIN_VALUE;"
                    + "for (procInstIdToStartDatesEntry in state.procInstIdToStartDates.entrySet()) {  "
                    + "def endDates = state.procInstIdToEndDates.getOrDefault(procInstIdToStartDatesEntry.getKey(), new ArrayList());"
                    + "def startDates = procInstIdToStartDatesEntry.getValue();"
                    + "if (!startDates.isEmpty() && !endDates.isEmpty()) {"
                    + "long minStartDate = startDates.stream().min(Long::compareTo).get(); "
                    + "List endDatesLargerMinStartDate = endDates.stream().filter(e -> e >= minStartDate).collect(Collectors.toList());"
                    + "if (!endDatesLargerMinStartDate.isEmpty()) {"
                    + "long closestEndDate = endDatesLargerMinStartDate.stream()"
                    + ".min(Comparator.comparingDouble(v -> Math.abs(v - minStartDate))).get();"
                    + "double duration = closestEndDate - minStartDate;"
                    + "min = duration < min? duration : min;"
                    + "max = duration > max? duration : max;"
                    + "sum += duration;"
                    + "count += 1;"
                    + "}"
                    + "}"
                    + "}"
                    + "Map result = new HashMap();"
                    + "result.put('aggregationType', params.aggregationType);"
                    + "result.put('sum', sum);"
                    + "result.put('count', count);"
                    + "result.put('min', min);"
                    + "result.put('max', max);"
                    + "return result;"));
    // @formatter:on
  }

  private static Script getReduceScript() {
    // @formatter:off
    return Script.of(
        i ->
            i.source(
                "if (states == null || states.isEmpty()) {"
                    + "return null;"
                    + "}"
                    + "double sum = 0; "
                    + "long count = 0; "
                    + "double min = Double.MAX_VALUE;"
                    + "double max = Double.MIN_VALUE;"
                    + "def aggregationType = 'avg';"
                    + "for (a in states) { "
                    + "if (a != null) {"
                    + "sum += a.get('sum');"
                    + "count += a.get('count');"
                    + "min = a.get('min') < min? a.get('min') : min;"
                    + "max = a.get('max') > max? a.get('max') : max;"
                    + "}"
                    + "}"
                    + "if (count == 0) {"
                    + "return null;"
                    + "}"
                    +
                    // return correct result depending on the aggregation type
                    "if (params.aggregationType == 'avg') {"
                    + "return sum / count;"
                    + "} else if (params.aggregationType == 'min') {"
                    + "return min;"
                    + "} else if (params.aggregationType == 'max') {"
                    + "return max;"
                    + "} else if (params.aggregationType == 'sum') {"
                    + "return sum;"
                    + "} else {"
                    + "Debug.explain('Aggregation type ' + params.aggregationType + ' is not supported!');"
                    + "}"));
    // @formatter:on
  }
}
