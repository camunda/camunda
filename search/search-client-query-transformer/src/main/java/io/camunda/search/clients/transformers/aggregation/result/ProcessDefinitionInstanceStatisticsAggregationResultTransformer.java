/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_NAME_BY_PROCESS_ID;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_NAME_LATEST_PROCESS_DEFINITION;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_NAME_TOTAL_WITH_INCIDENT;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_NAME_VERSION_COUNT;

import io.camunda.search.aggregation.result.ProcessDefinitionInstanceStatisticsAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.transformers.entity.ProcessInstanceEntityTransformer;
import io.camunda.search.entities.ProcessDefinitionInstanceStatisticsEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.util.List;
import java.util.Map;

public class ProcessDefinitionInstanceStatisticsAggregationResultTransformer
    implements AggregationResultTransformer<ProcessDefinitionInstanceStatisticsAggregationResult> {

  @Override
  public ProcessDefinitionInstanceStatisticsAggregationResult apply(
      final Map<String, AggregationResult> value) {

    final AggregationResult byProcessIdAgg = value.get(AGGREGATION_NAME_BY_PROCESS_ID);
    final Map<String, AggregationResult> perProcessAggregations = byProcessIdAgg.aggregations();

    // Use the cardinality aggregation for totalItems, which may be an estimate (see ES docs)
    final AggregationResult cardinalityAgg =
        value.get(
            io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation
                .AGGREGATION_NAME_PROCESS_DEFINITION_KEY_CARDINALITY);
    final int totalItems =
        cardinalityAgg != null
            ? Math.toIntExact(cardinalityAgg.docCount())
            : perProcessAggregations.size();

    final List<ProcessDefinitionInstanceStatisticsEntity> items =
        perProcessAggregations.entrySet().stream()
            .map(
                entry -> {
                  final String processId = entry.getKey();
                  final AggregationResult agg = entry.getValue();
                  final long withIncidents =
                      agg.aggregations()
                          .getOrDefault(
                              AGGREGATION_NAME_TOTAL_WITH_INCIDENT, AggregationResult.EMPTY)
                          .docCount();
                  final long withoutIncidents =
                      agg.aggregations()
                          .getOrDefault(
                              AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT, AggregationResult.EMPTY)
                          .docCount();

                  final String latestProcessDefinitionName =
                      entry
                          .getValue()
                          .aggregations()
                          .get(AGGREGATION_NAME_LATEST_PROCESS_DEFINITION)
                          .hits()
                          .stream()
                          .map(SearchQueryHit::source)
                          .filter(ProcessInstanceForListViewEntity.class::isInstance)
                          .map(ProcessInstanceForListViewEntity.class::cast)
                          .map(new ProcessInstanceEntityTransformer()::apply)
                          .map(ProcessInstanceEntity::processDefinitionName)
                          .findFirst()
                          .orElse(null);

                  final boolean hasMultipleVersions =
                      agg.aggregations().get(AGGREGATION_NAME_VERSION_COUNT).docCount() > 1;

                  return new ProcessDefinitionInstanceStatisticsEntity(
                      processId,
                      latestProcessDefinitionName,
                      hasMultipleVersions,
                      withoutIncidents,
                      withIncidents);
                })
            .toList();
    return new ProcessDefinitionInstanceStatisticsAggregationResult(items, totalItems);
  }
}
