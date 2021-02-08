/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.util.modelelement;

import com.google.common.collect.ImmutableMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeDurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.FlowNodeDurationFiltersDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_CANCELED;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FlowNodeFilterQueryUtil extends ModelElementFilterQueryUtil {

  private static final String NESTED_DOC = EVENTS;
  private static final String MI_BODY = "multiInstanceBody";

  private static Map<Class<? extends ProcessFilterDto<?>>, Function<BoolQueryBuilder, QueryBuilder>>
    flowNodeStatusViewFilterInstanceQueries =
    ImmutableMap.of(
      RunningFlowNodesOnlyFilterDto.class,
      FlowNodeFilterQueryUtil::createRunningFlowNodesOnlyFilterQuery,
      CompletedFlowNodesOnlyFilterDto.class,
      FlowNodeFilterQueryUtil::createCompletedFlowNodesOnlyFilterQuery,
      CompletedOrCanceledFlowNodesOnlyFilterDto.class,
      FlowNodeFilterQueryUtil::createCompletedOrCanceledFlowNodesOnlyFilterQuery,
      CanceledFlowNodesOnlyFilterDto.class,
      FlowNodeFilterQueryUtil::createCanceledFlowNodesOnlyFilterQuery
    );

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

  public static QueryBuilder createRunningFlowNodesOnlyFilterQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery
      .mustNot(termQuery(nestedFieldReference(ACTIVITY_TYPE), MI_BODY))
      .mustNot(existsQuery(nestedFieldReference(ACTIVITY_END_DATE)));
  }

  public static QueryBuilder createCompletedFlowNodesOnlyFilterQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery
      .mustNot(termQuery(nestedFieldReference(ACTIVITY_TYPE), MI_BODY))
      .must(termQuery(nestedFieldReference(ACTIVITY_CANCELED), false))
      .must(existsQuery(nestedFieldReference(ACTIVITY_END_DATE)));
  }

  public static QueryBuilder createCanceledFlowNodesOnlyFilterQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery
      .mustNot(termQuery(nestedFieldReference(ACTIVITY_TYPE), MI_BODY))
      .must(termQuery(nestedFieldReference(ACTIVITY_CANCELED), true));
  }

  public static QueryBuilder createCompletedOrCanceledFlowNodesOnlyFilterQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery
      .mustNot(termQuery(nestedFieldReference(ACTIVITY_TYPE), MI_BODY))
      .must(existsQuery(nestedFieldReference(ACTIVITY_END_DATE)));
  }

  public static Optional<NestedQueryBuilder> addInstanceFilterForRelevantViewLevelFilters(final List<ProcessFilterDto<?>> filters) {
    final List<ProcessFilterDto<?>> viewLevelFiltersForInstanceMatch = filters.stream()
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .filter(filter -> flowNodeStatusViewFilterInstanceQueries.containsKey(filter.getClass())
        || filter instanceof FlowNodeDurationFilterDto)
      .collect(Collectors.toList());
    if (!viewLevelFiltersForInstanceMatch.isEmpty()) {
      final BoolQueryBuilder viewFilterInstanceQuery = boolQuery();
      viewLevelFiltersForInstanceMatch
        .forEach(filter -> {
          if (filter instanceof FlowNodeDurationFilterDto) {
            final FlowNodeDurationFiltersDataDto filterData = (FlowNodeDurationFiltersDataDto) filter.getData();
            createFlowNodeDurationFilterQuery(filterData, getFlowNodeDurationProperties(), viewFilterInstanceQuery);
          } else {
            flowNodeStatusViewFilterInstanceQueries.get(filter.getClass()).apply(viewFilterInstanceQuery);
          }
        });
      return Optional.of(nestedQuery(NESTED_DOC, viewFilterInstanceQuery, ScoreMode.None));
    }
    return Optional.empty();
  }

  private static void addFlowNodeStatusFilter(final BoolQueryBuilder boolQuery,
                                              final ProcessReportDataDto reportDataDto) {
    reportDataDto.getFilter().stream()
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .filter(filter -> flowNodeStatusViewFilterInstanceQueries.containsKey(filter.getClass()))
      .forEach(filter -> boolQuery.filter(
        flowNodeStatusViewFilterInstanceQueries.get(filter.getClass()).apply(boolQuery())));
  }

  private static String nestedFieldReference(final String nestedField) {
    return nestedFieldBuilder(NESTED_DOC, nestedField);
  }

  private static FlowNodeDurationFilterProperties getFlowNodeDurationProperties() {
    return new FlowNodeDurationFilterProperties(NESTED_DOC, ACTIVITY_ID, ACTIVITY_DURATION, ACTIVITY_START_DATE);
  }

}
