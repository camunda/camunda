/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.AssigneeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CandidateGroupFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.IdentityLinkFilterDataDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static org.camunda.optimize.service.es.report.command.util.AggregationFilterUtil.addExecutionStateFilter;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CANCELED;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_END_DATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserTaskFilterQueryUtil {

  public static BoolQueryBuilder createUserTaskIdentityAggregationFilter(final ProcessReportDataDto reportDataDto,
                                                                         final Set<String> userTaskIds) {
    final BoolQueryBuilder filterBoolQuery = createUserTaskAggregationFilter(reportDataDto);

    // it's possible to do report evaluations over several definitions versions. However, only the most recent
    // one is used to decide which user tasks should be taken into account. To make sure that we only fetch assignees
    // related to this definition version we filter for user tasks that only occur in the latest version.
    filterBoolQuery.filter(QueryBuilders.termsQuery(USER_TASKS + "." + USER_TASK_ACTIVITY_ID, userTaskIds));

    return filterBoolQuery;
  }

  public static BoolQueryBuilder createUserTaskAggregationFilter(final ProcessReportDataDto reportDataDto) {
    final BoolQueryBuilder filterBoolQuery = boolQuery();
    final FlowNodeExecutionState flowNodeExecutionState = reportDataDto.getConfiguration().getFlowNodeExecutionState();
    addExecutionStateFilter(
      filterBoolQuery,
      flowNodeExecutionState,
      getExecutionStateFilterFieldForType(flowNodeExecutionState)
    );
    addAssigneeFilter(filterBoolQuery, reportDataDto);
    addCandidateGroupFilter(filterBoolQuery, reportDataDto);
    return filterBoolQuery;
  }

  private static void addAssigneeFilter(final BoolQueryBuilder userTaskFilterBoolQuery,
                                        final ProcessReportDataDto reportDataDto) {
    reportDataDto.getFilter().stream().filter(AssigneeFilterDto.class::isInstance)
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .map(filterDto -> (IdentityLinkFilterDataDto) filterDto.getData())
      .forEach(addAssigneeFilter -> userTaskFilterBoolQuery.filter(createAssigneeFilterQuery(addAssigneeFilter)));
  }

  private static void addCandidateGroupFilter(final BoolQueryBuilder userTaskFilterBoolQuery,
                                              final ProcessReportDataDto reportDataDto) {
    reportDataDto.getFilter().stream().filter(CandidateGroupFilterDto.class::isInstance)
      .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
      .map(filterDto -> (IdentityLinkFilterDataDto) filterDto.getData())
      .forEach(addAssigneeFilter -> userTaskFilterBoolQuery.filter(createCandidateGroupFilterQuery(addAssigneeFilter)));
  }

  public static QueryBuilder createAssigneeFilterQuery(final IdentityLinkFilterDataDto assigneeFilter) {
    return createIdentityLinkFilterQuery(assigneeFilter, USER_TASK_ASSIGNEE);
  }

  public static QueryBuilder createCandidateGroupFilterQuery(final IdentityLinkFilterDataDto candidateGroupFilter) {
    return createIdentityLinkFilterQuery(candidateGroupFilter, USER_TASK_CANDIDATE_GROUPS);
  }

  private static QueryBuilder createIdentityLinkFilterQuery(final IdentityLinkFilterDataDto assigneeFilter,
                                                            final String valueField) {
    if (CollectionUtils.isEmpty(assigneeFilter.getValues())) {
      throw new OptimizeValidationException("Filter values are not allowed to be empty.");
    }

    final AtomicBoolean includeNull = new AtomicBoolean(false);
    final Set<String> nonNullValues = assigneeFilter.getValues().stream()
      .peek(value -> {
        if (value == null) {
          includeNull.set(true);
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    final BoolQueryBuilder innerBoolQueryBuilder = boolQuery().minimumShouldMatch(1);
    if (!nonNullValues.isEmpty()) {
      innerBoolQueryBuilder.should(
        termsQuery(USER_TASKS + "." + valueField, nonNullValues)
      );
    }
    if (includeNull.get()) {
      innerBoolQueryBuilder.should(
        boolQuery().mustNot(existsQuery(USER_TASKS + "." + valueField))
      );
    }

    if (NOT_IN.equals(assigneeFilter.getOperator())) {
      return boolQuery().mustNot(innerBoolQueryBuilder);
    } else {
      return innerBoolQueryBuilder;
    }
  }

  private static String getExecutionStateFilterFieldForType(final FlowNodeExecutionState flowNodeExecutionState) {
    if (FlowNodeExecutionState.CANCELED.equals(flowNodeExecutionState)) {
      return USER_TASKS + "." + USER_TASK_CANCELED;
    }
    return USER_TASKS + "." + USER_TASK_END_DATE;
  }

}
