/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter.util;

import static io.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtilES.createExecutedFlowNodeFilterQuery;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_STATUS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_TENANT_ID;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.OpenIncidentFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ResolvedIncidentFilterDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.report.filter.util.IncidentFilterQueryUtil;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class IncidentFilterQueryUtilES extends IncidentFilterQueryUtil {

  private static final NestedDefinitionQueryBuilderES NESTED_DEFINITION_QUERY_BUILDER =
      new NestedDefinitionQueryBuilderES(
          INCIDENTS, INCIDENT_DEFINITION_KEY, INCIDENT_DEFINITION_VERSION, INCIDENT_TENANT_ID);

  private static final Map<
          Class<? extends ProcessFilterDto<?>>, Function<BoolQuery.Builder, BoolQuery.Builder>>
      INCIDENT_VIEW_FILTER_INSTANCE_QUERIES =
          ImmutableMap.of(
              OpenIncidentFilterDto.class,
              IncidentFilterQueryUtilES::createOpenIncidentTermQuery,
              ResolvedIncidentFilterDto.class,
              IncidentFilterQueryUtilES::createResolvedIncidentTermQuery);

  private IncidentFilterQueryUtilES() {}

  public static BoolQuery.Builder createIncidentAggregationFilter(
      final ProcessReportDataDto reportData, final DefinitionService definitionService) {
    final BoolQuery.Builder filterBoolQuery = new BoolQuery.Builder().minimumShouldMatch("1");
    final Map<String, List<ProcessFilterDto<?>>> filtersByDefinition =
        reportData.groupFiltersByDefinitionIdentifier();
    reportData
        .getDefinitions()
        .forEach(
            definitionDto -> {
              final BoolQuery.Builder incidentDefinitionQuery = new BoolQuery.Builder();
              incidentDefinitionQuery.must(
                  m ->
                      NESTED_DEFINITION_QUERY_BUILDER.createNestedDocDefinitionQuery(
                          definitionDto.getKey(),
                          definitionDto.getVersions(),
                          definitionDto.getTenantIds(),
                          definitionService));
              addIncidentFilters(
                  incidentDefinitionQuery,
                  filtersByDefinition.getOrDefault(
                      definitionDto.getIdentifier(), Collections.emptyList()));
              filterBoolQuery.should(s -> s.bool(incidentDefinitionQuery.build()));
            });

    addIncidentFilters(
        filterBoolQuery,
        filtersByDefinition.getOrDefault(APPLIED_TO_ALL_DEFINITIONS, Collections.emptyList()));
    return filterBoolQuery;
  }

  private static void addIncidentFilters(
      final BoolQuery.Builder filterBoolQuery, final List<ProcessFilterDto<?>> filters) {
    addOpenIncidentFilter(filterBoolQuery, filters);
    addResolvedIncidentFilter(filterBoolQuery, filters);
    addExecutedFlowNodeFilter(filterBoolQuery, filters);
  }

  public static Optional<NestedQuery.Builder> addInstanceFilterForRelevantViewLevelFilters(
      final List<ProcessFilterDto<?>> filters) {
    final List<ProcessFilterDto<?>> viewLevelFiltersForInstanceMatch =
        filters.stream()
            .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
            .filter(filter -> INCIDENT_VIEW_FILTER_INSTANCE_QUERIES.containsKey(filter.getClass()))
            .collect(Collectors.toList());
    if (!viewLevelFiltersForInstanceMatch.isEmpty()) {
      final BoolQuery.Builder viewFilterInstanceQuery = new BoolQuery.Builder();
      viewLevelFiltersForInstanceMatch.forEach(
          filter ->
              INCIDENT_VIEW_FILTER_INSTANCE_QUERIES
                  .get(filter.getClass())
                  .apply(viewFilterInstanceQuery));
      final NestedQuery.Builder builder = new NestedQuery.Builder();
      builder
          .path(INCIDENTS)
          .scoreMode(ChildScoreMode.None)
          .query(q -> q.bool(viewFilterInstanceQuery.build()));
      return Optional.of(builder);
    }
    return Optional.empty();
  }

  public static Query.Builder createResolvedIncidentTermQuery() {
    final Query.Builder builder = new Query.Builder();
    builder.bool(b -> createResolvedIncidentTermQuery(new BoolQuery.Builder()));
    return builder;
  }

  private static BoolQuery.Builder createResolvedIncidentTermQuery(
      final BoolQuery.Builder boolQuery) {
    return boolQuery.must(
        m ->
            m.term(
                t ->
                    t.field(INCIDENTS + "." + INCIDENT_STATUS)
                        .value(IncidentStatus.RESOLVED.getId())));
  }

  public static BoolQuery.Builder createOpenIncidentTermQuery() {
    return createOpenIncidentTermQuery(new BoolQuery.Builder());
  }

  public static BoolQuery.Builder createDeletedIncidentTermQuery() {
    return createDeletedIncidentTermQuery(new BoolQuery.Builder());
  }

  private static BoolQuery.Builder createDeletedIncidentTermQuery(
      final BoolQuery.Builder boolQuery) {
    return new BoolQuery.Builder()
        .must(
            m ->
                m.term(
                    t ->
                        t.field(INCIDENTS + "." + INCIDENT_STATUS)
                            .value(IncidentStatus.DELETED.getId())));
  }

  private static BoolQuery.Builder createOpenIncidentTermQuery(final BoolQuery.Builder boolQuery) {
    return boolQuery.must(
        m ->
            m.term(
                t ->
                    t.field(INCIDENTS + "." + INCIDENT_STATUS).value(IncidentStatus.OPEN.getId())));
  }

  private static void addOpenIncidentFilter(
      final BoolQuery.Builder boolQuery, final List<ProcessFilterDto<?>> filters) {
    if (containsViewLevelFilterOfType(filters, OpenIncidentFilterDto.class)) {
      boolQuery.filter(f -> f.bool(createOpenIncidentTermQuery().build()));
    }
  }

  private static void addResolvedIncidentFilter(
      final BoolQuery.Builder boolQuery, final List<ProcessFilterDto<?>> filters) {
    if (containsViewLevelFilterOfType(filters, ResolvedIncidentFilterDto.class)) {
      boolQuery.filter(f -> createResolvedIncidentTermQuery());
    }
  }

  public static void addExecutedFlowNodeFilter(
      final BoolQuery.Builder boolQuery, final List<ProcessFilterDto<?>> filters) {
    findAllViewLevelFiltersOfType(filters, ExecutedFlowNodeFilterDto.class)
        .map(ProcessFilterDto::getData)
        .forEach(
            executedFlowNodeFilterData ->
                boolQuery.filter(
                    f ->
                        f.bool(
                            createExecutedFlowNodeFilterQuery(
                                    executedFlowNodeFilterData,
                                    nestedFieldReference(IncidentDto.Fields.activityId),
                                    new BoolQuery.Builder())
                                .build())));
  }
}
