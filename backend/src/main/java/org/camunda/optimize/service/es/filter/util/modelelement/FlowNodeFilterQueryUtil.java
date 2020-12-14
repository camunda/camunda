/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.util.modelelement;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.FlowNodeDurationFiltersDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

import static org.camunda.optimize.service.es.filter.util.modelelement.ModelElementFilterQueryUtil.addFlowNodeDurationFilter;
import static org.camunda.optimize.service.es.filter.util.modelelement.ModelElementFilterQueryUtil.nestedFieldBuilder;
import static org.camunda.optimize.service.es.report.command.util.AggregationFilterUtil.addExecutionStateFilter;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_CANCELED;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FlowNodeFilterQueryUtil implements ModelElementFilterQueryUtil {

  private static final String MI_BODY = "multiInstanceBody";
  private static final String NESTED_DOC = EVENTS;

  public static QueryBuilder createFlowNodeDurationFilterQuery(final FlowNodeDurationFiltersDataDto durationFilterData) {
    return ModelElementFilterQueryUtil.createFlowNodeDurationFilterQuery(
      durationFilterData,
      getFlowNodeDurationProperties()
    );
  }

  public static BoolQueryBuilder createFlowNodeAggregationFilter(final ProcessReportDataDto reportDataDto) {
    final BoolQueryBuilder filterBoolQuery = boolQuery();
    addFlowNodeExecutionStateFilter(filterBoolQuery, reportDataDto);
    addFlowNodeDurationFilter(
      filterBoolQuery, reportDataDto, getFlowNodeDurationProperties());
    return filterBoolQuery;
  }

  private static void addFlowNodeExecutionStateFilter(final BoolQueryBuilder filterBoolQuery,
                                                      final ProcessReportDataDto reportDataDto) {
    final FlowNodeExecutionState flowNodeExecutionState = reportDataDto.getConfiguration().getFlowNodeExecutionState();
    addExecutionStateFilter(
      filterBoolQuery.mustNot(termQuery(nestedFieldReference(ACTIVITY_TYPE), MI_BODY)),
      flowNodeExecutionState,
      getExecutionStateFilterField(flowNodeExecutionState)
    );
  }

  private static String getExecutionStateFilterField(final FlowNodeExecutionState flowNodeExecutionState) {
    if (FlowNodeExecutionState.CANCELED.equals(flowNodeExecutionState)) {
      return nestedFieldReference(ACTIVITY_CANCELED);
    }
    return nestedFieldReference(ACTIVITY_END_DATE);
  }

  private static String nestedFieldReference(final String nestedField) {
    return nestedFieldBuilder(NESTED_DOC, nestedField);
  }

  private static FlowNodeDurationFilterProperties getFlowNodeDurationProperties() {
    return FlowNodeDurationFilterProperties.builder()
      .nestedDocRef(NESTED_DOC)
      .idField(ACTIVITY_ID)
      .durationField(ACTIVITY_DURATION)
      .startDateField(ACTIVITY_START_DATE)
      .build();
  }

}
