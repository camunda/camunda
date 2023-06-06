/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es.writer;

import io.camunda.operate.entities.MetricEntity;
import io.camunda.operate.es.dao.UsageMetricDAO;
import org.elasticsearch.action.index.IndexRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static io.camunda.operate.es.contract.MetricContract.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MetricWriterTest {
  @Mock
  private UsageMetricDAO dao;

  @InjectMocks
  private MetricWriter subject;

  @Test
  public void verifyRegisterProcessEventWasCalledWithRightArgument() {
    // Given
    final String key = "processInstanceKey";

    // When
    when(dao.buildESIndexRequest(any())).thenReturn(new IndexRequest("index"));
    subject.registerProcessInstanceStartEvent(key, OffsetDateTime.now());

    // Then
    ArgumentCaptor<MetricEntity> entityCaptor = ArgumentCaptor.forClass(MetricEntity.class);
    verify(dao).buildESIndexRequest(entityCaptor.capture());

    MetricEntity calledValue = entityCaptor.getValue();
    assertEquals(EVENT_PROCESS_INSTANCE_STARTED, calledValue.getEvent());
    assertEquals(key, calledValue.getValue());
  }

  @Test
  public void verifyRegisterDecisionEventWasCalledWithRightArgument() {
    // Given
    final String key = "decisionInstanceKey";

    // When
    when(dao.buildESIndexRequest(any())).thenReturn(new IndexRequest("index"));
    subject.registerDecisionInstanceCompleteEvent(key, OffsetDateTime.now());

    // Then
    ArgumentCaptor<MetricEntity> entityCaptor = ArgumentCaptor.forClass(MetricEntity.class);
    verify(dao).buildESIndexRequest(entityCaptor.capture());

    MetricEntity calledValue = entityCaptor.getValue();
    assertEquals(EVENT_DECISION_INSTANCE_EVALUATED, calledValue.getEvent());
    assertEquals(key, calledValue.getValue());
  }
}
