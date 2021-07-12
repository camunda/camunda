/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.util;

import com.google.common.collect.ImmutableMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.OpenIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ResolvedIncidentFilterDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.service.es.filter.util.modelelement.ModelElementFilterQueryUtil.createExecutedFlowNodeFilterQuery;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENTS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_STATUS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class IncidentFilterQueryUtil {

  private static Map<Class<? extends ProcessFilterDto<?>>, Function<BoolQueryBuilder, QueryBuilder>>
    incidentViewFilterInstanceQueries =
    ImmutableMap.of(
      OpenIncidentFilterDto.class,
      IncidentFilterQueryUtil::createOpenIncidentTermQuery,
      ResolvedIncidentFilterDto.class,
      IncidentFilterQueryUtil::createResolvedIncidentTermQuery
    );

  public static BoolQueryBuilder createIncidentAggregationFilter(final ProcessReportDataDto reportDataDto) {
    final BoolQueryBuilder filterBoolQuery = boolQuery();
    addOpenIncidentFilter(filterBoolQuery, reportDataDto.getFilter());
    addResolvedIncidentFilter(filterBoolQuery, reportDataDto.getFilter());
    addExecutedFlowNodeFilter(filterBoolQuery, reportDataDto);
    return filterBoolQuery;
  }

  public static Optional<NestedQueryBuilder> addInstanceFilterForRelevantViewLevelFilters(final List<ProcessFilterDto<?>> filters) {
    final List<ProcessFilterDto<?>> viewLevelFiltersForInstanceMatch = filters.stream()
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .filter(filter -> incidentViewFilterInstanceQueries.containsKey(filter.getClass()))
      .collect(Collectors.toList());
    if (!viewLevelFiltersForInstanceMatch.isEmpty()) {
      final BoolQueryBuilder viewFilterInstanceQuery = boolQuery();
      viewLevelFiltersForInstanceMatch.forEach(
        filter -> incidentViewFilterInstanceQueries.get(filter.getClass()).apply(viewFilterInstanceQuery));
      return Optional.of(nestedQuery(INCIDENTS, viewFilterInstanceQuery, ScoreMode.None));
    }
    return Optional.empty();
  }

  public static BoolQueryBuilder createResolvedIncidentTermQuery() {
    return createResolvedIncidentTermQuery(boolQuery());
  }

  private static BoolQueryBuilder createResolvedIncidentTermQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery.must(termQuery(INCIDENTS + "." + INCIDENT_STATUS, IncidentStatus.RESOLVED.getId()));
  }

  public static BoolQueryBuilder createOpenIncidentTermQuery() {
    return createOpenIncidentTermQuery(boolQuery());
  }

  private static BoolQueryBuilder createOpenIncidentTermQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery.must(termQuery(INCIDENTS + "." + INCIDENT_STATUS, IncidentStatus.OPEN.getId()));
  }

  private static void addOpenIncidentFilter(final BoolQueryBuilder boolQuery,
                                            final List<ProcessFilterDto<?>> filters) {
    if (containsViewLevelFilterOfType(filters, OpenIncidentFilterDto.class)) {
      boolQuery.filter(createOpenIncidentTermQuery());
    }
  }

  private static void addResolvedIncidentFilter(final BoolQueryBuilder boolQuery,
                                                final List<ProcessFilterDto<?>> filters) {
    if (containsViewLevelFilterOfType(filters, ResolvedIncidentFilterDto.class)) {
      boolQuery.filter(createResolvedIncidentTermQuery());
    }
  }

  public static void addExecutedFlowNodeFilter(final BoolQueryBuilder boolQuery,
                                               final ProcessReportDataDto reportDataDto) {
    findAllViewLevelFiltersOfType(
      reportDataDto.getFilter(),
      ExecutedFlowNodeFilterDto.class
    ).map(ProcessFilterDto::getData)
      .forEach(executedFlowNodeFilterData -> boolQuery.filter(createExecutedFlowNodeFilterQuery(
        executedFlowNodeFilterData,
        nestedFieldReference(IncidentDto.Fields.activityId),
        boolQuery()
      )));
  }

  private static String nestedFieldReference(final String fieldName) {
    return INCIDENTS + "." + fieldName;
  }

  private static boolean containsViewLevelFilterOfType(final List<ProcessFilterDto<?>> filters,
                                                       final Class<? extends ProcessFilterDto<?>> filterClass) {
    return filters.stream()
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .anyMatch(filterClass::isInstance);
  }

  private static <T extends ProcessFilterDto<?>> Stream<T> findAllViewLevelFiltersOfType(
    final List<ProcessFilterDto<?>> filters,
    final Class<T> filterClass) {
    return filters.stream()
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .filter(filterClass::isInstance)
      .map(filterClass::cast);
  }

}
