/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter.util;

import static io.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.and;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.nested;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.os.report.filter.util.ModelElementFilterQueryUtilOS.createExecutedFlowNodeFilterQuery;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_STATUS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_TENANT_ID;

import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.OpenIncidentFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ResolvedIncidentFilterDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.report.filter.util.IncidentFilterQueryUtil;
import io.camunda.optimize.util.types.ListUtil;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class IncidentFilterQueryUtilOS extends IncidentFilterQueryUtil {

  private static final NestedDefinitionQueryBuilderOS NESTED_DEFINITION_QUERY_BUILDER =
      new NestedDefinitionQueryBuilderOS(
          INCIDENTS, INCIDENT_DEFINITION_KEY, INCIDENT_DEFINITION_VERSION, INCIDENT_TENANT_ID);
  private static final Map<Class<? extends ProcessFilterDto<?>>, Query>
      INCIDENT_VIEW_FILTER_INSTANCE_QUERIES =
          ImmutableMap.of(
              OpenIncidentFilterDto.class,
              IncidentFilterQueryUtilOS.createOpenIncidentTermQuery(),
              ResolvedIncidentFilterDto.class,
              IncidentFilterQueryUtilOS.createResolvedIncidentTermQuery());

  private IncidentFilterQueryUtilOS() {}

  public static Query createIncidentAggregationFilterQuery(
      final ProcessReportDataDto reportData, final DefinitionService definitionService) {
    final Map<String, List<ProcessFilterDto<?>>> filtersByDefinition =
        reportData.groupFiltersByDefinitionIdentifier();
    final List<ProcessFilterDto<?>> allDefinitionFilters =
        filtersByDefinition.getOrDefault(APPLIED_TO_ALL_DEFINITIONS, Collections.emptyList());

    final List<Query> definitionQueries =
        reportData.getDefinitions().stream()
            .map(
                definitionDto -> {
                  final List<ProcessFilterDto<?>> filters =
                      filtersByDefinition.getOrDefault(
                          definitionDto.getIdentifier(), Collections.emptyList());
                  return buildIncidentDefinitionQuery(definitionDto, definitionService, filters);
                })
            .toList();

    return new BoolQuery.Builder()
        .minimumShouldMatch("1")
        .filter(getIncidentFilterQueries(allDefinitionFilters))
        .should(definitionQueries)
        .build()
        .toQuery();
  }

  private static Query buildIncidentDefinitionQuery(
      final ReportDataDefinitionDto definitionDto,
      final DefinitionService definitionService,
      final List<ProcessFilterDto<?>> filters) {
    final Query nestedDocDefinitionQuery =
        NESTED_DEFINITION_QUERY_BUILDER
            .createNestedDocDefinitionQuery(
                definitionDto.getKey(),
                definitionDto.getVersions(),
                definitionDto.getTenantIds(),
                definitionService)
            .build()
            .toQuery();

    return new BoolQuery.Builder()
        .must(nestedDocDefinitionQuery)
        .filter(getIncidentFilterQueries(filters))
        .build()
        .toQuery();
  }

  private static List<Query> getIncidentFilterQueries(final List<ProcessFilterDto<?>> filters) {
    return ListUtil.concat(
        getOpenIncidentFilterQueries(filters),
        getResolvedIncidentFilterQueries(filters),
        getExecutedFlowNodeFilterQueries(filters));
  }

  private static List<Query> getOpenIncidentFilterQueries(final List<ProcessFilterDto<?>> filters) {
    return containsViewLevelFilterOfType(filters, OpenIncidentFilterDto.class)
        ? List.of(createOpenIncidentTermQuery())
        : List.of();
  }

  private static List<Query> getResolvedIncidentFilterQueries(
      final List<ProcessFilterDto<?>> filters) {
    return containsViewLevelFilterOfType(filters, ResolvedIncidentFilterDto.class)
        ? List.of(createResolvedIncidentTermQuery())
        : List.of();
  }

  public static List<Query> getExecutedFlowNodeFilterQueries(
      final List<ProcessFilterDto<?>> filters) {
    return findAllViewLevelFiltersOfType(filters, ExecutedFlowNodeFilterDto.class)
        .map(ProcessFilterDto::getData)
        .map(
            executedFlowNodeFilterData ->
                createExecutedFlowNodeFilterQuery(
                    executedFlowNodeFilterData,
                    nestedFieldReference(IncidentDto.Fields.activityId),
                    new BoolQuery.Builder()))
        .toList();
  }

  public static Optional<Query> instanceFilterForRelevantViewLevelFiltersQuery(
      final List<ProcessFilterDto<?>> filters) {
    final List<Query> filterQueries =
        filters.stream()
            .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
            .filter(filter -> INCIDENT_VIEW_FILTER_INSTANCE_QUERIES.containsKey(filter.getClass()))
            .map(filter -> INCIDENT_VIEW_FILTER_INSTANCE_QUERIES.get(filter.getClass()))
            .toList();
    return filterQueries.isEmpty()
        ? Optional.empty()
        : Optional.of(nested(INCIDENTS, and(filterQueries), ChildScoreMode.None));
  }

  public static Query createResolvedIncidentTermQuery() {
    return term(INCIDENTS + "." + INCIDENT_STATUS, IncidentStatus.RESOLVED.getId());
  }

  public static Query createOpenIncidentTermQuery() {
    return createOpenIncidentTermQuery(new BoolQuery.Builder());
  }

  public static Query createDeletedIncidentTermQuery() {
    return createDeletedIncidentTermQuery(new BoolQuery.Builder());
  }

  private static Query createDeletedIncidentTermQuery(final BoolQuery.Builder boolQueryBuilder) {
    return boolQueryBuilder
        .must(term(INCIDENTS + "." + INCIDENT_STATUS, IncidentStatus.DELETED.getId()))
        .build()
        .toQuery();
  }

  private static Query createOpenIncidentTermQuery(final BoolQuery.Builder boolQueryBuilder) {
    return boolQueryBuilder
        .must(term(INCIDENTS + "." + INCIDENT_STATUS, IncidentStatus.OPEN.getId()))
        .build()
        .toQuery();
  }
}
