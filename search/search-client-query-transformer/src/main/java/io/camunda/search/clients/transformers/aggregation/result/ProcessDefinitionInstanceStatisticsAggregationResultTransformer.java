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
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    final var transformer = new ProcessInstanceEntityTransformer();
    final List<ProcessDefinitionInstanceStatisticsEntity> items =
        perProcessAggregations.values().stream()
            .map(
                agg -> {
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

                  final AggregationResult latestProcessDefinitionAgg =
                      agg.aggregations()
                          .getOrDefault(
                              AGGREGATION_NAME_LATEST_PROCESS_DEFINITION, AggregationResult.EMPTY);

                  final var latestProcessDefinitionHits = latestProcessDefinitionAgg.hits();
                  if (latestProcessDefinitionHits == null) {
                    return null;
                  }

                  final var processInstanceEntity =
                      latestProcessDefinitionHits.stream()
                          .map(SearchQueryHit::source)
                          .filter(ProcessInstanceForListViewEntity.class::isInstance)
                          .map(ProcessInstanceForListViewEntity.class::cast)
                          .map(transformer::apply)
                          .findFirst()
                          .orElse(null);

                  if (processInstanceEntity == null) {
                    return null;
                  }

                  final boolean hasMultipleVersions =
                      agg.aggregations()
                              .getOrDefault(AGGREGATION_NAME_VERSION_COUNT, AggregationResult.EMPTY)
                              .docCount()
                          > 1;

                  return new ProcessDefinitionInstanceStatisticsEntity(
                      processInstanceEntity.processDefinitionId(),
                      processInstanceEntity.tenantId(),
                      processInstanceEntity.processDefinitionName(),
                      hasMultipleVersions,
                      withoutIncidents,
                      withIncidents);
                })
            .filter(Objects::nonNull)
            .toList();
    return new ProcessDefinitionInstanceStatisticsAggregationResult(items, totalItems);
  }
}
