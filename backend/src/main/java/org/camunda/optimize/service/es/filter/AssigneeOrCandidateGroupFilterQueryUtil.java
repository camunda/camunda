/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.AssigneeCandidateGroupFilterDataDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@UtilityClass
public class AssigneeOrCandidateGroupFilterQueryUtil {

  public static final QueryBuilder createAssigneeFilterQuery(final AssigneeCandidateGroupFilterDataDto assigneeFilter) {
    return createAssigneeOrCandidateGroupFilterQuery(assigneeFilter, USER_TASK_ASSIGNEE);
  }

  public static final QueryBuilder createCandidateGroupFilterQuery(final AssigneeCandidateGroupFilterDataDto candidateGroupFilter) {
    return createAssigneeOrCandidateGroupFilterQuery(candidateGroupFilter, USER_TASK_CANDIDATE_GROUPS);
  }

  private static final QueryBuilder createAssigneeOrCandidateGroupFilterQuery(
    final AssigneeCandidateGroupFilterDataDto assigneeFilter,
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

    final NestedQueryBuilder nestedUserTaskFilter = nestedQuery(USER_TASKS, innerBoolQueryBuilder, ScoreMode.None);
    if (FilterOperatorConstants.NOT_IN.equals(assigneeFilter.getOperator())) {
      return boolQuery().mustNot(nestedUserTaskFilter);
    } else {
      return nestedUserTaskFilter;
    }
  }

}
