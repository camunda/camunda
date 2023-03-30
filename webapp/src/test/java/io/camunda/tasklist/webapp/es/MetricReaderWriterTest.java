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
import static io.camunda.tasklist.webapp.es.MetricReaderWriter.ASSIGNEE;
import static io.camunda.tasklist.webapp.es.MetricReaderWriter.EVENT_TASK_COMPLETED_BY_ASSIGNEE;
import static io.camunda.tasklist.webapp.es.dao.Query.range;
import static io.camunda.tasklist.webapp.es.dao.Query.whereEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.entities.MetricEntity;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.es.dao.Query;
import io.camunda.tasklist.webapp.es.dao.UsageMetricDAO;
import io.camunda.tasklist.webapp.es.dao.response.AggregationResponse;
import io.camunda.tasklist.webapp.es.dao.response.InsertResponse;
import io.camunda.tasklist.webapp.management.dto.UsageMetricDTO;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MetricReaderWriterTest {

  @Mock private UsageMetricDAO dao;

  @InjectMocks private MetricReaderWriter instance;

  @Test
  public void verifyRegisterEventWasCalledWithRightArgument() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final String assignee = "John Lennon";
    final TaskEntity task = new TaskEntity().setCompletionTime(now).setAssignee(assignee);

    // When
    when(dao.insert(any())).thenReturn(InsertResponse.success());
    instance.registerTaskCompleteEvent(task);

    // Then
    final MetricEntity expectedEntry = new MetricEntity();
    expectedEntry.setValue(assignee);
    expectedEntry.setEventTime(now);
    expectedEntry.setEvent(EVENT_TASK_COMPLETED_BY_ASSIGNEE);

    verify(dao).insert(expectedEntry);
  }

  @Test
  public void exceptionIsNotHandledOnReaderWriterLevel() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    final Query query =
        whereEquals(EVENT, EVENT_TASK_COMPLETED_BY_ASSIGNEE)
            .and(range(EVENT_TIME, oneHourBefore, now))
            .aggregate(ASSIGNEE, VALUE);

    when(dao.searchWithAggregation(query)).thenThrow(new TasklistRuntimeException("issue"));

    // When - Then
    assertThatThrownBy(() -> instance.retrieveDistinctAssigneesBetweenDates(oneHourBefore, now))
        .isInstanceOf(TasklistRuntimeException.class);
  }

  @Test
  public void throwErrorWhenErrorResponse() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    final Query query =
        whereEquals(EVENT, EVENT_TASK_COMPLETED_BY_ASSIGNEE)
            .and(range(EVENT_TIME, oneHourBefore, now))
            .aggregate(ASSIGNEE, VALUE);

    final AggregationResponse response = new AggregationResponse(true);
    when(dao.searchWithAggregation(query)).thenReturn(response);

    // When - Then
    assertThatThrownBy(() -> instance.retrieveDistinctAssigneesBetweenDates(oneHourBefore, now))
        .isInstanceOf(TasklistRuntimeException.class)
        .hasMessage("Error while retrieving assigned users between dates");
  }

  @Test
  public void expectedResponseWhenResultsAreEmpty() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    final Query query =
        whereEquals(EVENT, EVENT_TASK_COMPLETED_BY_ASSIGNEE)
            .and(range(EVENT_TIME, oneHourBefore, now))
            .aggregate(ASSIGNEE, VALUE);

    final AggregationResponse elsResponse = new AggregationResponse(false, List.of());
    when(dao.searchWithAggregation(query)).thenReturn(elsResponse);

    // When
    final UsageMetricDTO response =
        instance.retrieveDistinctAssigneesBetweenDates(oneHourBefore, now);

    // Then
    final UsageMetricDTO expectedResponse = new UsageMetricDTO(List.of());
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  public void expectedResponseWhenResultsAreReturned() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    final Query query =
        whereEquals(EVENT, EVENT_TASK_COMPLETED_BY_ASSIGNEE)
            .and(range(EVENT_TIME, oneHourBefore, now))
            .aggregate(ASSIGNEE, VALUE);

    final AggregationResponse elsResponse =
        new AggregationResponse(false, List.of(new AggregationResponse.AggregationValue("key", 0)));
    when(dao.searchWithAggregation(query)).thenReturn(elsResponse);

    // When
    final UsageMetricDTO response =
        instance.retrieveDistinctAssigneesBetweenDates(oneHourBefore, now);

    // Then
    final UsageMetricDTO expectedResponse = new UsageMetricDTO(List.of("key"));
    assertThat(response).isEqualTo(expectedResponse);
  }
}
