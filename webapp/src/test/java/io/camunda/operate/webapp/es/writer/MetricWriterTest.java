/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.es.writer;

import io.camunda.operate.entities.MetricEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.webapp.es.dao.UsageMetricDAO;
import io.camunda.operate.webapp.es.dao.response.InsertResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.camunda.operate.webapp.es.writer.MetricWriter.EVENT_PROCESS_INSTANCE_FINISHED;
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
  public void verifyRegisterEventWasCalledWithRightArgument() {
    // Given
    final String key = "processInstanceKey";

    // When
    when(dao.insert(any())).thenReturn(InsertResponse.success());
    subject.registerProcessInstanceCompleteEvent(key);

    // Then
    ArgumentCaptor<MetricEntity> entityCaptor = ArgumentCaptor.forClass(MetricEntity.class);
    verify(dao).insert(entityCaptor.capture());

    MetricEntity calledValue = entityCaptor.getValue();
    assertEquals(EVENT_PROCESS_INSTANCE_FINISHED, calledValue.getEvent());
    assertEquals(key, calledValue.getValue());
  }

  @Test
  public void throwExceptionIfResponseIsNotSuccess() {
    // Given
    final String key = "processInstanceKey";

    // When
    when(dao.insert(any())).thenReturn(InsertResponse.failure());

    // Then
    Assertions.assertThrows(
        OperateRuntimeException.class,
        () -> subject.registerProcessInstanceCompleteEvent(key));
  }
}
