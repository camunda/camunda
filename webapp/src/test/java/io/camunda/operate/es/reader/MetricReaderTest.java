/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es.reader;

import io.camunda.operate.es.contract.MetricContract;
import io.camunda.operate.es.dao.Query;
import io.camunda.operate.es.dao.UsageMetricDAO;
import io.camunda.operate.es.dao.response.AggregationResponse;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.webapp.es.reader.MetricReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static io.camunda.operate.es.dao.Query.range;
import static io.camunda.operate.es.dao.Query.whereEquals;
import static io.camunda.operate.schema.indices.MetricIndex.EVENT;
import static io.camunda.operate.schema.indices.MetricIndex.EVENT_TIME;
import static io.camunda.operate.schema.indices.MetricIndex.VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MetricReaderTest {
  @Mock
  private UsageMetricDAO dao;

  @InjectMocks
  private MetricReader subject;

  @Test
  public void verifyRetrieveProcessCountReturnsExpectedValue() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    // When
    when(dao.searchWithAggregation(any())).thenReturn(new AggregationResponse(false, List.of(), 99L));
    Long result = subject.retrieveProcessInstanceCount(oneHourBefore, now);

    // Then
    assertEquals(result, 99L);
  }

  @Test
  public void verifyProcessSearchIsCalledWithRightParam() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    // When
    when(dao.searchWithAggregation(any())).thenReturn(new AggregationResponse(false, List.of(), 99L));
    subject.retrieveProcessInstanceCount(oneHourBefore, now);

    // Then
    ArgumentCaptor<Query> entityCaptor = ArgumentCaptor.forClass(Query.class);
    verify(dao).searchWithAggregation(entityCaptor.capture());

    Query expected = Query.whereEquals(EVENT, MetricContract.EVENT_PROCESS_INSTANCE_FINISHED)
        .or(whereEquals(EVENT, MetricContract.EVENT_PROCESS_INSTANCE_STARTED))
        .and(range(EVENT_TIME, oneHourBefore, now))
        .aggregate(MetricReader.PROCESS_INSTANCES_AGG_NAME, VALUE, 1);
    Query calledValue = entityCaptor.getValue();
    assertEquals(expected, calledValue);
  }

  @Test
  public void throwExceptionIfProcessResponseHasError() {
    // When
    when(dao.searchWithAggregation(any())).thenReturn(new AggregationResponse(true));

    // Then
    Assertions.assertThrows(
        OperateRuntimeException.class,
        () -> subject.retrieveProcessInstanceCount(OffsetDateTime.now(), OffsetDateTime.now()));
  }

  @Test
  public void verifyRetrieveDecisionCountReturnsExpectedValue() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    // When
    when(dao.searchWithAggregation(any())).thenReturn(new AggregationResponse(false, List.of(), 99L));
    Long result = subject.retrieveDecisionInstanceCount(oneHourBefore, now);

    // Then
    assertEquals(result, 99L);
  }

  @Test
  public void verifyDecisionSearchIsCalledWithRightParam() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    // When
    when(dao.searchWithAggregation(any())).thenReturn(new AggregationResponse(false, List.of(), 99L));
    subject.retrieveDecisionInstanceCount(oneHourBefore, now);

    // Then
    ArgumentCaptor<Query> entityCaptor = ArgumentCaptor.forClass(Query.class);
    verify(dao).searchWithAggregation(entityCaptor.capture());

    Query expected = Query.whereEquals(EVENT, MetricContract.EVENT_DECISION_INSTANCE_EVALUATED)
        .and(range(EVENT_TIME, oneHourBefore, now))
        .aggregate(MetricReader.DECISION_INSTANCES_AGG_NAME, VALUE, 1);
    Query calledValue = entityCaptor.getValue();
    assertEquals(expected, calledValue);
  }

  @Test
  public void throwExceptionIfDecisionResponseHasError() {
    // When
    when(dao.searchWithAggregation(any())).thenReturn(new AggregationResponse(true));

    // Then
    Assertions.assertThrows(
        OperateRuntimeException.class,
        () -> subject.retrieveDecisionInstanceCount(OffsetDateTime.now(), OffsetDateTime.now()));
  }
}
