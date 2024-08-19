/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter.util;

import static io.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtil.createExecutedFlowNodeFilterQuery;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_STATUS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_TENANT_ID;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

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
import io.camunda.optimize.service.util.NestedDefinitionQueryBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class IncidentFilterQueryUtil {

  private static final NestedDefinitionQueryBuilder NESTED_DEFINITION_QUERY_BUILDER =
      new NestedDefinitionQueryBuilder(
          INCIDENTS, INCIDENT_DEFINITION_KEY, INCIDENT_DEFINITION_VERSION, INCIDENT_TENANT_ID);

  private static final Map<
          Class<? extends ProcessFilterDto<?>>, Function<BoolQueryBuilder, QueryBuilder>>
      incidentViewFilterInstanceQueries =
          ImmutableMap.of(
              OpenIncidentFilterDto.class,
              IncidentFilterQueryUtil::createOpenIncidentTermQuery,
              ResolvedIncidentFilterDto.class,
              IncidentFilterQueryUtil::createResolvedIncidentTermQuery);

  private IncidentFilterQueryUtil() {}

  public static BoolQueryBuilder createIncidentAggregationFilter(
      final ProcessReportDataDto reportData, final DefinitionService definitionService) {
    final BoolQueryBuilder filterBoolQuery = boolQuery().minimumShouldMatch(1);
    final Map<String, List<ProcessFilterDto<?>>> filtersByDefinition =
        reportData.groupFiltersByDefinitionIdentifier();
    reportData
        .getDefinitions()
        .forEach(
            definitionDto -> {
              final BoolQueryBuilder incidentDefinitionQuery =
                  boolQuery()
                      .must(
                          NESTED_DEFINITION_QUERY_BUILDER.createNestedDocDefinitionQuery(
                              definitionDto.getKey(),
                              definitionDto.getVersions(),
                              definitionDto.getTenantIds(),
                              definitionService));
              addIncidentFilters(
                  incidentDefinitionQuery,
                  filtersByDefinition.getOrDefault(
                      definitionDto.getIdentifier(), Collections.emptyList()));
              filterBoolQuery.should(incidentDefinitionQuery);
            });

    addIncidentFilters(
        filterBoolQuery,
        filtersByDefinition.getOrDefault(APPLIED_TO_ALL_DEFINITIONS, Collections.emptyList()));
    return filterBoolQuery;
  }

  private static void addIncidentFilters(
      final BoolQueryBuilder filterBoolQuery, final List<ProcessFilterDto<?>> filters) {
    addOpenIncidentFilter(filterBoolQuery, filters);
    addResolvedIncidentFilter(filterBoolQuery, filters);
    addExecutedFlowNodeFilter(filterBoolQuery, filters);
  }

  public static Optional<NestedQueryBuilder> addInstanceFilterForRelevantViewLevelFilters(
      final List<ProcessFilterDto<?>> filters) {
    final List<ProcessFilterDto<?>> viewLevelFiltersForInstanceMatch =
        filters.stream()
            .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
            .filter(filter -> incidentViewFilterInstanceQueries.containsKey(filter.getClass()))
            .collect(Collectors.toList());
    if (!viewLevelFiltersForInstanceMatch.isEmpty()) {
      final BoolQueryBuilder viewFilterInstanceQuery = boolQuery();
      viewLevelFiltersForInstanceMatch.forEach(
          filter ->
              incidentViewFilterInstanceQueries
                  .get(filter.getClass())
                  .apply(viewFilterInstanceQuery));
      return Optional.of(nestedQuery(INCIDENTS, viewFilterInstanceQuery, ScoreMode.None));
    }
    return Optional.empty();
  }

  public static BoolQueryBuilder createResolvedIncidentTermQuery() {
    return createResolvedIncidentTermQuery(boolQuery());
  }

  private static BoolQueryBuilder createResolvedIncidentTermQuery(
      final BoolQueryBuilder boolQuery) {
    return boolQuery.must(
        termQuery(INCIDENTS + "." + INCIDENT_STATUS, IncidentStatus.RESOLVED.getId()));
  }

  public static BoolQueryBuilder createOpenIncidentTermQuery() {
    return createOpenIncidentTermQuery(boolQuery());
  }

  public static BoolQueryBuilder createDeletedIncidentTermQuery() {
    return createDeletedIncidentTermQuery(boolQuery());
  }

  private static BoolQueryBuilder createDeletedIncidentTermQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery.must(
        termQuery(INCIDENTS + "." + INCIDENT_STATUS, IncidentStatus.DELETED.getId()));
  }

  private static BoolQueryBuilder createOpenIncidentTermQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery.must(
        termQuery(INCIDENTS + "." + INCIDENT_STATUS, IncidentStatus.OPEN.getId()));
  }

  private static void addOpenIncidentFilter(
      final BoolQueryBuilder boolQuery, final List<ProcessFilterDto<?>> filters) {
    if (containsViewLevelFilterOfType(filters, OpenIncidentFilterDto.class)) {
      boolQuery.filter(createOpenIncidentTermQuery());
    }
  }

  private static void addResolvedIncidentFilter(
      final BoolQueryBuilder boolQuery, final List<ProcessFilterDto<?>> filters) {
    if (containsViewLevelFilterOfType(filters, ResolvedIncidentFilterDto.class)) {
      boolQuery.filter(createResolvedIncidentTermQuery());
    }
  }

  public static void addExecutedFlowNodeFilter(
      final BoolQueryBuilder boolQuery, final List<ProcessFilterDto<?>> filters) {
    findAllViewLevelFiltersOfType(filters, ExecutedFlowNodeFilterDto.class)
        .map(ProcessFilterDto::getData)
        .forEach(
            executedFlowNodeFilterData ->
                boolQuery.filter(
                    createExecutedFlowNodeFilterQuery(
                        executedFlowNodeFilterData,
                        nestedFieldReference(IncidentDto.Fields.activityId),
                        boolQuery())));
  }

  private static String nestedFieldReference(final String fieldName) {
    return INCIDENTS + "." + fieldName;
  }

  private static boolean containsViewLevelFilterOfType(
      final List<ProcessFilterDto<?>> filters,
      final Class<? extends ProcessFilterDto<?>> filterClass) {
    return filters.stream()
        .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
        .anyMatch(filterClass::isInstance);
  }

  private static <T extends ProcessFilterDto<?>> Stream<T> findAllViewLevelFiltersOfType(
      final List<ProcessFilterDto<?>> filters, final Class<T> filterClass) {
    return filters.stream()
        .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
        .filter(filterClass::isInstance)
        .map(filterClass::cast);
  }
}
