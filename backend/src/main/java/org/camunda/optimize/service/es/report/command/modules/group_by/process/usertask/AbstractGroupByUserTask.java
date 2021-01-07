/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.usertask;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;

import java.util.Optional;

import static org.camunda.optimize.service.es.filter.util.modelelement.UserTaskFilterQueryUtil.createUserTaskAggregationFilter;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

@RequiredArgsConstructor
public abstract class AbstractGroupByUserTask extends GroupByPart<ProcessReportDataDto> {
  private static final String USER_TASKS_AGGREGATION = "userTasks";
  private static final String FILTERED_USER_TASKS_AGGREGATION = "filteredUserTasks";

  protected NestedAggregationBuilder createFilteredUserTaskAggregation(final ExecutionContext<ProcessReportDataDto> context,
                                                                       final AggregationBuilder subAggregation) {
    return nested(USER_TASKS_AGGREGATION, USER_TASKS)
      .subAggregation(
        filter(FILTERED_USER_TASKS_AGGREGATION, createUserTaskAggregationFilter(context.getReportData()))
          .subAggregation(subAggregation)
      )
      // sibling aggregation for distributedByPart for retrieval of all keys that
      // should be present in distributedBy result
      .subAggregation(distributedByPart.createAggregation(context));
  }

  protected Optional<Filter> getFilteredUserTaskAggregation(final SearchResponse response) {
    return getUserTasksAggregation(response)
      .map(SingleBucketAggregation::getAggregations)
      .map(aggs -> aggs.get(FILTERED_USER_TASKS_AGGREGATION));
  }

  protected Optional<Nested> getUserTasksAggregation(final SearchResponse response) {
    return Optional.ofNullable(response.getAggregations())
      .map(aggs -> aggs.get(USER_TASKS_AGGREGATION));
  }

}
