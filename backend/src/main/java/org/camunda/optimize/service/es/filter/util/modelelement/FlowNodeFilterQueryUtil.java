/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.util.modelelement;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.FlowNodeDurationFiltersDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_CANCELED;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FlowNodeFilterQueryUtil extends ModelElementFilterQueryUtil {

  private static final String NESTED_DOC = EVENTS;
  private static final String MI_BODY = "multiInstanceBody";

  public static QueryBuilder createFlowNodeDurationFilterQuery(final FlowNodeDurationFiltersDataDto durationFilterData) {
    return createFlowNodeDurationFilterQuery(
      durationFilterData,
      getFlowNodeDurationProperties()
    );
  }

  public static BoolQueryBuilder createFlowNodeAggregationFilter(final ProcessReportDataDto reportDataDto) {
    final BoolQueryBuilder filterBoolQuery = boolQuery();
    addFlowNodeDurationFilter(filterBoolQuery, reportDataDto, getFlowNodeDurationProperties());
    addFlowNodeStatusFilter(filterBoolQuery, reportDataDto);
    return filterBoolQuery;
  }

  public static QueryBuilder createRunningFlowNodesOnlyFilterQuery() {
    return boolQuery()
      .mustNot(termQuery(nestedFieldReference(ACTIVITY_TYPE), MI_BODY))
      .mustNot(existsQuery(nestedFieldReference(ACTIVITY_END_DATE)));
  }

  public static QueryBuilder createCompletedFlowNodesOnlyFilterQuery() {
    return boolQuery()
      .mustNot(termQuery(nestedFieldReference(ACTIVITY_TYPE), MI_BODY))
      .must(termQuery(nestedFieldReference(ACTIVITY_CANCELED), false))
      .must(existsQuery(nestedFieldReference(ACTIVITY_END_DATE)));
  }

  public static QueryBuilder createCanceledFlowNodesOnlyFilterQuery() {
    return boolQuery()
      .mustNot(termQuery(nestedFieldReference(ACTIVITY_TYPE), MI_BODY))
      .must(termQuery(nestedFieldReference(ACTIVITY_CANCELED), true));
  }

  public static QueryBuilder createCompletedOrCanceledFlowNodesOnlyFilterQuery() {
    return boolQuery()
      .mustNot(termQuery(nestedFieldReference(ACTIVITY_TYPE), MI_BODY))
      .must(existsQuery(nestedFieldReference(ACTIVITY_END_DATE)));
  }

  private static void addFlowNodeStatusFilter(final BoolQueryBuilder boolQuery,
                                              final ProcessReportDataDto reportDataDto) {
    if (viewLevelFiltersOfTypeExists(reportDataDto, RunningFlowNodesOnlyFilterDto.class)) {
      boolQuery.filter(createRunningFlowNodesOnlyFilterQuery());
    }
    if (viewLevelFiltersOfTypeExists(reportDataDto, CompletedFlowNodesOnlyFilterDto.class)) {
      boolQuery.filter(createCompletedFlowNodesOnlyFilterQuery());
    }
    if (viewLevelFiltersOfTypeExists(reportDataDto, CanceledFlowNodesOnlyFilterDto.class)) {
      boolQuery.filter(createCanceledFlowNodesOnlyFilterQuery());
    }
    if (viewLevelFiltersOfTypeExists(reportDataDto, CompletedOrCanceledFlowNodesOnlyFilterDto.class)) {
      boolQuery.filter(createCompletedOrCanceledFlowNodesOnlyFilterQuery());
    }
  }

  private static String nestedFieldReference(final String nestedField) {
    return nestedFieldBuilder(NESTED_DOC, nestedField);
  }

  private static FlowNodeDurationFilterProperties getFlowNodeDurationProperties() {
    return new FlowNodeDurationFilterProperties(NESTED_DOC, ACTIVITY_ID, ACTIVITY_DURATION, ACTIVITY_START_DATE);
  }

}
