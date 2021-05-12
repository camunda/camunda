/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.util.modelelement;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.AssigneeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CandidateGroupFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.IdentityLinkFilterDataDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_CANCELED;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserTaskFilterQueryUtil extends ModelElementFilterQueryUtil {

  private static final String NESTED_DOC = FLOW_NODE_INSTANCES;

  public static BoolQueryBuilder createUserTaskFlowNodeTypeFilter() {
    return boolQuery().must(
      termQuery(
        nestedFieldReference(FLOW_NODE_TYPE),
        FLOW_NODE_TYPE_USER_TASK
      ));
  }

  public static BoolQueryBuilder createUserTaskIdentityAggregationFilter(final ProcessReportDataDto reportDataDto,
                                                                         final Set<String> userTaskIds) {
    final BoolQueryBuilder filterBoolQuery = createUserTaskAggregationFilter(reportDataDto);

    // it's possible to do report evaluations over several definitions versions. However, only the most recent
    // one is used to decide which user tasks should be taken into account. To make sure that we only fetch assignees
    // related to this definition version we filter for user tasks that only occur in the latest version.
    filterBoolQuery.filter(QueryBuilders.termsQuery(nestedFieldReference(FLOW_NODE_ID), userTaskIds));

    return filterBoolQuery;
  }

  public static BoolQueryBuilder createUserTaskAggregationFilter(final ProcessReportDataDto reportDataDto) {
    final BoolQueryBuilder filterBoolQuery = createUserTaskFlowNodeTypeFilter();
    addFlowNodeStatusFilter(filterBoolQuery, reportDataDto);
    addAssigneeFilter(filterBoolQuery, reportDataDto);
    addCandidateGroupFilter(filterBoolQuery, reportDataDto);
    addFlowNodeDurationFilter(filterBoolQuery, reportDataDto, getFlowNodeDurationProperties());
    return filterBoolQuery;
  }

  public static Optional<NestedQueryBuilder> addInstanceFilterForRelevantViewLevelFilters(final List<ProcessFilterDto<?>> filters) {
    // The user task instance filterer only considers identity, as the flow node equivalent filter already handles
    // the duration and flow node states across all flow nodes of an instance
    final List<ProcessFilterDto<?>> viewLevelFiltersForInstanceMatch = filters.stream()
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .filter(filter -> filter instanceof CandidateGroupFilterDto || filter instanceof AssigneeFilterDto)
      .collect(Collectors.toList());
    if (!viewLevelFiltersForInstanceMatch.isEmpty()) {
      final BoolQueryBuilder viewFilterInstanceQuery = createUserTaskFlowNodeTypeFilter();
      viewLevelFiltersForInstanceMatch
        .forEach(filter -> {
          if (filter instanceof CandidateGroupFilterDto) {
            final IdentityLinkFilterDataDto filterData = (IdentityLinkFilterDataDto) filter.getData();
            createIdentityLinkFilterQuery(filterData, USER_TASK_CANDIDATE_GROUPS, viewFilterInstanceQuery);
          } else if (filter instanceof AssigneeFilterDto) {
            final IdentityLinkFilterDataDto filterData = (IdentityLinkFilterDataDto) filter.getData();
            createIdentityLinkFilterQuery(filterData, USER_TASK_ASSIGNEE, viewFilterInstanceQuery);
          }
        });
      return Optional.of(nestedQuery(NESTED_DOC, viewFilterInstanceQuery, ScoreMode.None));
    }
    return Optional.empty();
  }

  private static void addFlowNodeStatusFilter(final BoolQueryBuilder boolQuery,
                                              final ProcessReportDataDto reportDataDto) {
    if (containsViewLevelFilterOfType(reportDataDto.getFilter(), RunningFlowNodesOnlyFilterDto.class)) {
      boolQuery.filter(createRunningFlowNodesOnlyFilterQuery(boolQuery()));
    }
    if (containsViewLevelFilterOfType(reportDataDto.getFilter(), CompletedFlowNodesOnlyFilterDto.class)) {
      boolQuery.filter(createCompletedFlowNodesOnlyFilterQuery(boolQuery()));
    }
    if (containsViewLevelFilterOfType(reportDataDto.getFilter(), CanceledFlowNodesOnlyFilterDto.class)) {
      boolQuery.filter(createCanceledFlowNodesOnlyFilterQuery(boolQuery()));
    }
    if (containsViewLevelFilterOfType(reportDataDto.getFilter(), CompletedOrCanceledFlowNodesOnlyFilterDto.class)) {
      boolQuery.filter(createCompletedOrCanceledFlowNodesOnlyFilterQuery(boolQuery()));
    }
  }

  private static BoolQueryBuilder createRunningFlowNodesOnlyFilterQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery.mustNot(existsQuery(nestedFieldReference(FLOW_NODE_END_DATE)));
  }

  private static BoolQueryBuilder createCompletedFlowNodesOnlyFilterQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery
      .must(existsQuery(nestedFieldReference(FLOW_NODE_END_DATE)))
      .must(termQuery(nestedFieldReference(FLOW_NODE_CANCELED), false));
  }

  private static BoolQueryBuilder createCanceledFlowNodesOnlyFilterQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery.must(termQuery(nestedFieldReference(FLOW_NODE_CANCELED), true));
  }

  private static BoolQueryBuilder createCompletedOrCanceledFlowNodesOnlyFilterQuery(final BoolQueryBuilder boolQuery) {
    return boolQuery.must(existsQuery(nestedFieldReference(FLOW_NODE_END_DATE)));
  }

  private static void addAssigneeFilter(final BoolQueryBuilder userTaskFilterBoolQuery,
                                        final ProcessReportDataDto reportDataDto) {
    findAllViewLevelFiltersOfType(reportDataDto.getFilter(), AssigneeFilterDto.class)
      .map(ProcessFilterDto::getData)
      .forEach(assigneeFilterData -> userTaskFilterBoolQuery.filter(createAssigneeFilterQuery(assigneeFilterData)));
  }

  private static void addCandidateGroupFilter(final BoolQueryBuilder userTaskFilterBoolQuery,
                                              final ProcessReportDataDto reportDataDto) {
    findAllViewLevelFiltersOfType(reportDataDto.getFilter(), CandidateGroupFilterDto.class)
      .map(ProcessFilterDto::getData)
      .forEach(candidateFilterData -> userTaskFilterBoolQuery.filter(createCandidateGroupFilterQuery(candidateFilterData)));
  }

  public static QueryBuilder createAssigneeFilterQuery(final IdentityLinkFilterDataDto assigneeFilter) {
    return createIdentityLinkFilterQuery(assigneeFilter, USER_TASK_ASSIGNEE, boolQuery());
  }

  public static QueryBuilder createCandidateGroupFilterQuery(final IdentityLinkFilterDataDto candidateGroupFilter) {
    return createIdentityLinkFilterQuery(candidateGroupFilter, USER_TASK_CANDIDATE_GROUPS, boolQuery());
  }

  // TODO make private again once OPT-5203 is done and CandidateGroupQueryFilter and AssigneeQueryFilter have been adjusted
  public static QueryBuilder createIdentityLinkFilterQuery(final IdentityLinkFilterDataDto identityFilter,
                                                           final String valueField,
                                                           final BoolQueryBuilder queryBuilder) {
    if (CollectionUtils.isEmpty(identityFilter.getValues())) {
      throw new OptimizeValidationException("Filter values are not allowed to be empty.");
    }

    final AtomicBoolean includeNull = new AtomicBoolean(false);
    final Set<String> nonNullValues = identityFilter.getValues().stream()
      .peek(value -> {
        if (value == null) {
          includeNull.set(true);
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    final BoolQueryBuilder identityQuery = boolQuery().minimumShouldMatch(1);
    if (!nonNullValues.isEmpty()) {
      identityQuery.should(termsQuery(nestedFieldReference(valueField), nonNullValues));
    }
    if (includeNull.get()) {
      identityQuery.should(boolQuery().mustNot(existsQuery(nestedFieldReference(valueField))));
    }

    if (NOT_IN.equals(identityFilter.getOperator())) {
      return queryBuilder.mustNot(identityQuery);
    } else {
      return queryBuilder.must(identityQuery);
    }
  }

  private static String nestedFieldReference(final String nestedField) {
    return nestedFieldBuilder(NESTED_DOC, nestedField);
  }

  private static FlowNodeDurationFilterProperties getFlowNodeDurationProperties() {
    return new FlowNodeDurationFilterProperties(
      NESTED_DOC, FLOW_NODE_ID, FLOW_NODE_TOTAL_DURATION, FLOW_NODE_START_DATE
    );
  }

}
