/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.util.modelelement;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeDurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.FlowNodeDurationFiltersDataDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.List;
import java.util.stream.Stream;

import static org.camunda.optimize.service.es.report.command.util.DurationScriptUtil.getDurationFilterScript;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ModelElementFilterQueryUtil {

  protected static void addFlowNodeDurationFilter(final BoolQueryBuilder boolQuery,
                                                  final ProcessReportDataDto reportDataDto,
                                                  final FlowNodeDurationFilterProperties flowNodeDurationProperties) {
    findAllViewLevelFiltersOfType(
      reportDataDto.getFilter(),
      FlowNodeDurationFilterDto.class
    ).map(ProcessFilterDto::getData)
      .forEach(durationFilterData -> boolQuery.filter(
        createFlowNodeDurationFilterQuery(durationFilterData, flowNodeDurationProperties)));
  }

  protected static <T extends ProcessFilterDto<?>> Stream<T> findAllViewLevelFiltersOfType(final List<ProcessFilterDto<?>> filters,
                                                                                           final Class<T> filterClass) {
    return filters.stream()
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .filter(filterClass::isInstance)
      .map(filterClass::cast);
  }

  protected static <T extends ProcessFilterDto<?>> boolean containsViewLevelFilterOfType(
    final List<ProcessFilterDto<?>> filters,
    final Class<T> filterClass) {
    return filters.stream()
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .anyMatch(filterClass::isInstance);
  }

  protected static QueryBuilder createFlowNodeDurationFilterQuery(final FlowNodeDurationFiltersDataDto durationFilterData,
                                                                  final FlowNodeDurationFilterProperties properties) {
    return createFlowNodeDurationFilterQuery(durationFilterData, properties, boolQuery());
  }

  protected static QueryBuilder createFlowNodeDurationFilterQuery(final FlowNodeDurationFiltersDataDto durationFilterData,
                                                                  final FlowNodeDurationFilterProperties properties,
                                                                  final BoolQueryBuilder queryBuilder) {
    queryBuilder.minimumShouldMatch(1);
    durationFilterData.forEach((flowNodeId, durationFilter) -> {
      final BoolQueryBuilder particularFlowNodeQuery = boolQuery()
        .must(termQuery(nestedFieldBuilder(properties.getNestedDocRef(), properties.getIdField()), flowNodeId))
        .must(QueryBuilders.scriptQuery(getDurationFilterScript(
          LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
          nestedFieldBuilder(properties.getNestedDocRef(), properties.getDurationField()),
          nestedFieldBuilder(properties.getNestedDocRef(), properties.getStartDateField()),
          durationFilter
        )));
      queryBuilder.should(particularFlowNodeQuery);
    });
    return queryBuilder;
  }

  protected static String nestedFieldBuilder(final String nestedField, final String fieldName) {
    return nestedField + "." + fieldName;
  }

  @AllArgsConstructor
  @Getter
  static class FlowNodeDurationFilterProperties {
    private String nestedDocRef;
    private String idField;
    private String durationField;
    private String startDateField;
  }

}
