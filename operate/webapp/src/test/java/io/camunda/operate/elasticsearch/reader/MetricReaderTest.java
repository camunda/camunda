/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch.reader;

import static io.camunda.operate.store.elasticsearch.dao.Query.range;
import static io.camunda.webapps.schema.descriptors.index.UsageMetricIndex.EVENT_TIME;
import static io.camunda.webapps.schema.descriptors.index.UsageMetricIndex.EVENT_TYPE;
import static io.camunda.webapps.schema.descriptors.index.UsageMetricIndex.EVENT_VALUE;
import static io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType.EDI;
import static io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType.RPI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.store.elasticsearch.ElasticsearchMetricsStore;
import io.camunda.operate.store.elasticsearch.dao.Query;
import io.camunda.operate.store.elasticsearch.dao.UsageMetricDAO;
import io.camunda.operate.store.elasticsearch.dao.response.AggregationResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MetricReaderTest {
  @Mock private UsageMetricDAO dao;

  @InjectMocks private MetricsStore subject = new ElasticsearchMetricsStore();

  @Test
  public void verifyRetrieveProcessCountReturnsExpectedValue() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    // When
    when(dao.searchWithAggregation(any()))
        .thenReturn(new AggregationResponse(false, List.of(), 99L));
    final Long result = subject.retrieveProcessInstanceCount(oneHourBefore, now, null);

    // Then
    assertThat(result).isEqualTo(99L);
  }

  @Test
  public void verifyProcessSearchIsCalledWithRightParam() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    // When
    when(dao.searchWithAggregation(any()))
        .thenReturn(new AggregationResponse(false, List.of(), 99L));
    subject.retrieveProcessInstanceCount(oneHourBefore, now, null);

    // Then
    final ArgumentCaptor<Query> entityCaptor = ArgumentCaptor.forClass(Query.class);
    verify(dao).searchWithAggregation(entityCaptor.capture());

    final Query expected =
        Query.whereEquals(EVENT_TYPE, RPI.name())
            .and(range(EVENT_TIME, oneHourBefore, now))
            .aggregate(MetricsStore.PROCESS_INSTANCES_AGG_NAME, EVENT_VALUE, 1);
    final Query calledValue = entityCaptor.getValue();
    assertThat(calledValue).isEqualTo(expected);
  }

  @Test
  public void throwExceptionIfProcessResponseHasError() {
    // When
    when(dao.searchWithAggregation(any())).thenReturn(new AggregationResponse(true));

    // Then
    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(
            () ->
                subject.retrieveProcessInstanceCount(
                    OffsetDateTime.now(), OffsetDateTime.now(), null));
  }

  @Test
  public void verifyRetrieveDecisionCountReturnsExpectedValue() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    // When
    when(dao.searchWithAggregation(any()))
        .thenReturn(new AggregationResponse(false, List.of(), 99L));
    final Long result = subject.retrieveDecisionInstanceCount(oneHourBefore, now, null);

    // Then
    assertThat(result).isEqualTo(99L);
  }

  @Test
  public void verifyDecisionSearchIsCalledWithRightParam() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    // When
    when(dao.searchWithAggregation(any()))
        .thenReturn(new AggregationResponse(false, List.of(), 99L));
    subject.retrieveDecisionInstanceCount(oneHourBefore, now, null);

    // Then
    final ArgumentCaptor<Query> entityCaptor = ArgumentCaptor.forClass(Query.class);
    verify(dao).searchWithAggregation(entityCaptor.capture());

    final Query expected =
        Query.whereEquals(EVENT_TYPE, EDI.name())
            .and(range(EVENT_TIME, oneHourBefore, now))
            .aggregate(MetricsStore.DECISION_INSTANCES_AGG_NAME, EVENT_VALUE, 1);
    final Query calledValue = entityCaptor.getValue();
    assertThat(calledValue).isEqualTo(expected);
  }

  @Test
  public void throwExceptionIfDecisionResponseHasError() {
    // When
    when(dao.searchWithAggregation(any())).thenReturn(new AggregationResponse(true));

    // Then
    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(
            () ->
                subject.retrieveDecisionInstanceCount(
                    OffsetDateTime.now(), OffsetDateTime.now(), null));
  }
}
