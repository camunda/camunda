/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.util.modelelement;

import lombok.Builder;
import lombok.Getter;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeDurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.FlowNodeDurationFiltersDataDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.stream.Stream;

import static org.camunda.optimize.service.es.report.command.util.AggregationFilterUtil.getDurationFilterScript;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public interface ModelElementFilterQueryUtil {

  static void addFlowNodeDurationFilter(final BoolQueryBuilder boolQuery,
                                        final ProcessReportDataDto reportDataDto,
                                        final FlowNodeDurationFilterProperties flowNodeDurationProperties) {
    findAllViewLevelFiltersOfType(reportDataDto, FlowNodeDurationFilterDto.class).map(ProcessFilterDto::getData)
      .forEach(durationFilterData -> boolQuery.filter(
        createFlowNodeDurationFilterQuery(durationFilterData, flowNodeDurationProperties)));
  }

  static <T extends ProcessFilterDto<?>> Stream<T> findAllViewLevelFiltersOfType(final ProcessReportDataDto reportDataDto,
                                                                                 final Class<T> filterClass) {
    return reportDataDto.getFilter().stream()
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .filter(filterClass::isInstance)
      .map(filterClass::cast);
  }

  static <T extends ProcessFilterDto<?>> boolean viewLevelFiltersOfTypeExists(final ProcessReportDataDto reportDataDto,
                                                                              final Class<T> filterClass) {
    return reportDataDto.getFilter().stream()
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .anyMatch(filterClass::isInstance);
  }

  static QueryBuilder createFlowNodeDurationFilterQuery(final FlowNodeDurationFiltersDataDto durationFilterData,
                                                        final FlowNodeDurationFilterProperties properties) {
    final BoolQueryBuilder disjunctMultiFlowNodeQuery = boolQuery().minimumShouldMatch(1);
    durationFilterData.forEach((flowNodeId, durationFilter) -> {
      final BoolQueryBuilder particularFlowNodeQuery = boolQuery()
        .must(termQuery(nestedFieldBuilder(properties.getNestedDocRef(), properties.getIdField()), flowNodeId))
        .must(QueryBuilders.scriptQuery(getDurationFilterScript(
          LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
          nestedFieldBuilder(properties.getNestedDocRef(), properties.getDurationField()),
          nestedFieldBuilder(properties.getNestedDocRef(), properties.getStartDateField()),
          durationFilter
        )));
      disjunctMultiFlowNodeQuery.should(particularFlowNodeQuery);
    });
    return disjunctMultiFlowNodeQuery;
  }

  static String nestedFieldBuilder(final String nestedField, final String fieldName) {
    return nestedField + "." + fieldName;
  }

  @Builder
  @Getter
  class FlowNodeDurationFilterProperties {
    private String nestedDocRef;
    private String idField;
    private String durationField;
    private String startDateField;
  }

}
