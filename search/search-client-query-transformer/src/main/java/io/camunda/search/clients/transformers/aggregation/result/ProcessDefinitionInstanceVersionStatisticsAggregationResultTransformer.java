/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_BY_VERSION_TENANT;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_PROCESS_DEFINITION_VERSION;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_TOTAL_WITH_INCIDENT;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_VERSION_CARDINALITY;

import io.camunda.search.aggregation.result.ProcessDefinitionInstanceVersionStatisticsAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.transformers.entity.ProcessInstanceEntityTransformer;
import io.camunda.search.entities.ProcessDefinitionInstanceVersionStatisticsEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.util.List;
import java.util.Map;

public class ProcessDefinitionInstanceVersionStatisticsAggregationResultTransformer
    implements AggregationResultTransformer<
        ProcessDefinitionInstanceVersionStatisticsAggregationResult> {

  @Override
  public ProcessDefinitionInstanceVersionStatisticsAggregationResult apply(
      final Map<String, AggregationResult> value) {

    final AggregationResult byVersionAgg = value.get(AGGREGATION_NAME_BY_VERSION_TENANT);
    final Map<String, AggregationResult> perProcessAggregations = byVersionAgg.aggregations();

    final AggregationResult cardinalityAgg = value.get(AGGREGATION_NAME_VERSION_CARDINALITY);

    final int totalItems =
        cardinalityAgg != null
            ? Math.toIntExact(cardinalityAgg.docCount())
            : perProcessAggregations.size();

    final List<ProcessDefinitionInstanceVersionStatisticsEntity> items =
        perProcessAggregations.values().stream()
            .map(
                agg -> {
                  final var processInstanceEntity =
                      agg
                          .aggregations()
                          .get(AGGREGATION_NAME_PROCESS_DEFINITION_VERSION)
                          .hits()
                          .stream()
                          .map(SearchQueryHit::source)
                          .map(ProcessInstanceForListViewEntity.class::cast)
                          .map(new ProcessInstanceEntityTransformer()::apply)
                          .findFirst();

                  final long activeInstancesWithIncidents =
                      agg.aggregations()
                          .getOrDefault(
                              AGGREGATION_NAME_TOTAL_WITH_INCIDENT, AggregationResult.EMPTY)
                          .docCount();

                  final long activeInstancesWithoutIncidents =
                      agg.aggregations()
                          .getOrDefault(
                              AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT, AggregationResult.EMPTY)
                          .docCount();

                  return processInstanceEntity
                      .map(
                          entity ->
                              new ProcessDefinitionInstanceVersionStatisticsEntity(
                                  entity.processDefinitionId(),
                                  entity.processDefinitionKey(),
                                  entity.processDefinitionVersion(),
                                  entity.processDefinitionName(),
                                  entity.tenantId(),
                                  activeInstancesWithoutIncidents,
                                  activeInstancesWithIncidents))
                      .orElse(null);
                })
            .toList();

    return new ProcessDefinitionInstanceVersionStatisticsAggregationResult(items, totalItems);
  }
}
