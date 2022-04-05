/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es;

import static io.camunda.tasklist.schema.indices.MetricIndex.EVENT;
import static io.camunda.tasklist.schema.indices.MetricIndex.EVENT_TIME;
import static io.camunda.tasklist.schema.indices.MetricIndex.VALUE;
import static io.camunda.tasklist.webapp.es.dao.Query.range;
import static io.camunda.tasklist.webapp.es.dao.Query.whereEquals;

import io.camunda.tasklist.entities.MetricEntity;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.es.contract.UsageMetricsContract;
import io.camunda.tasklist.webapp.es.dao.Query;
import io.camunda.tasklist.webapp.es.dao.UsageMetricDAO;
import io.camunda.tasklist.webapp.es.dao.response.AggregationResponse;
import io.camunda.tasklist.webapp.es.dao.response.DAOResponse;
import io.camunda.tasklist.webapp.management.dto.UsageMetricDTO;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MetricReaderWriter implements UsageMetricsContract {

  public static final String EVENT_TASK_COMPLETED_BY_ASSIGNEE = "task_completed_by_assignee";
  public static final String ASSIGNEE = "assignee";
  private static final Logger LOGGER = LoggerFactory.getLogger(MetricReaderWriter.class);
  @Autowired private UsageMetricDAO dao;

  @Override
  public void registerTaskCompleteEvent(TaskEntity task) {
    final MetricEntity metric = createTaskCompleteEvent(task);
    final DAOResponse response = dao.insert(metric);
    if (response.hasError()) {
      final String message = "Wrong response status while logging event";
      LOGGER.error(message);
      throw new TasklistRuntimeException(message);
    }
  }

  @Override
  public UsageMetricDTO retrieveDistinctAssigneesBetweenDates(
      OffsetDateTime startTime, OffsetDateTime endTime) {
    final Query query =
        whereEquals(EVENT, EVENT_TASK_COMPLETED_BY_ASSIGNEE)
            .and(range(EVENT_TIME, startTime, endTime))
            .aggregate(ASSIGNEE, VALUE);

    final AggregationResponse response = dao.searchWithAggregation(query);

    if (response.hasError()) {
      final String message = "Error while retrieving assigned users between dates";
      LOGGER.error(message);
      throw new TasklistRuntimeException(message);
    }

    final List<String> assignees =
        response.getHits().stream()
            .map(AggregationResponse.AggregationValue::getKey)
            .collect(Collectors.toList());
    return new UsageMetricDTO(assignees);
  }

  @NotNull
  private MetricEntity createTaskCompleteEvent(TaskEntity task) {
    return new MetricEntity()
        .setEvent(EVENT_TASK_COMPLETED_BY_ASSIGNEE)
        .setValue(task.getAssignee())
        .setEventTime(task.getCompletionTime());
  }
}
