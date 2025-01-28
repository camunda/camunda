/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter.externalTask;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.command.SetVariablesCommandStep1;
import io.camunda.client.api.command.SetVariablesCommandStep1.SetVariablesCommandStep2;
import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.response.SetVariablesResponse;
import java.util.Collections;
import java.util.Map;
import org.camunda.bpm.client.task.ExternalTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class JobClientExternalTaskServiceTest {
  @Mock CamundaClient client;

  @Mock ExternalTask externalTask;
  JobClientWrappingExternalTaskService service;

  private static Map<String, Object> testVariables() {
    return Collections.singletonMap("globalValue", "xyz");
  }

  private static Map<String, Object> testLocalVariables() {
    return Collections.singletonMap("localValue", "abc");
  }

  @BeforeEach
  public void setup() {
    if (service == null) {
      service = new JobClientWrappingExternalTaskService(client, externalTask);
    }
  }

  // TODO are these all operations?
  @Test
  public void testLock() {
    when(externalTask.getId()).thenReturn("123");
    assertThrows(
        UnsupportedOperationException.class, () -> service.lock(externalTask.getId(), 123L));
    assertThrows(UnsupportedOperationException.class, () -> service.lock(externalTask, 123L));
  }

  @Test
  public void testUnlock() {
    assertThrows(UnsupportedOperationException.class, () -> service.unlock(externalTask));
  }

  @Test
  public void testComplete() {
    setupSetVariablesCommand();
    final CompleteJobCommandStep1 command = mock(CompleteJobCommandStep1.class);
    final CamundaFuture<CompleteJobResponse> future = mock(CamundaFuture.class);

    when(externalTask.getId()).thenReturn("123");
    when(externalTask.getProcessInstanceId()).thenReturn("456");
    when(client.newCompleteCommand(anyLong())).thenReturn(command);
    when(command.variables(anyMap())).thenReturn(command);
    when(command.send()).thenReturn(future);

    service.complete(externalTask);
    verify(client, times(1)).newCompleteCommand(123L);
    service.complete(externalTask, testVariables());
    verify(client, times(2)).newCompleteCommand(123L);
    service.complete(externalTask, testVariables(), testLocalVariables());
    verify(client, times(3)).newCompleteCommand(123L);
    verify(client, times(1)).newSetVariablesCommand(456L);
    service.complete(externalTask.getId(), testVariables(), testLocalVariables());
    verify(client, times(4)).newCompleteCommand(123L);
    verify(client, times(2)).newSetVariablesCommand(456L);
  }

  @Test
  public void testSetVariables() {
    setupSetVariablesCommand();
    when(externalTask.getProcessInstanceId()).thenReturn("456");
    service.setVariables("123", testVariables());
    service.setVariables(externalTask, testVariables());
  }

  @Test
  public void testHandleFailure() {
    setupSetVariablesCommand();
    final FailJobCommandStep1 failJobCommandStep1 = mock(FailJobCommandStep1.class);
    final FailJobCommandStep2 failJobCommandStep2 = mock(FailJobCommandStep2.class);
    final CamundaFuture<FailJobResponse> future = mock(CamundaFuture.class);

    when(externalTask.getId()).thenReturn("123");
    when(externalTask.getProcessInstanceId()).thenReturn("456");
    when(client.newFailCommand(anyLong())).thenReturn(failJobCommandStep1);
    when(failJobCommandStep1.retries(anyInt())).thenReturn(failJobCommandStep2);
    when(failJobCommandStep2.errorMessage(anyString())).thenReturn(failJobCommandStep2);
    when(failJobCommandStep2.requestTimeout(any())).thenReturn(failJobCommandStep2);
    when(failJobCommandStep2.send()).thenReturn(future);

    final String errorMessage = "errorMessage";
    final String errorDetails = "errorDetails";
    service.handleFailure(externalTask, errorMessage, errorDetails, 2, 2000L);
    service.handleFailure(externalTask.getId(), errorMessage, errorDetails, 2, 2000L);
    service.handleFailure(
        externalTask.getId(),
        errorMessage,
        errorDetails,
        2,
        2000L,
        testVariables(),
        testLocalVariables());
  }

  @Test
  public void testHandleBpmnError() {
    setupSetVariablesCommand();
    final ThrowErrorCommandStep1 throwErrorCommandStep1 = mock(ThrowErrorCommandStep1.class);
    final ThrowErrorCommandStep2 throwErrorCommandStep2 = mock(ThrowErrorCommandStep2.class);
    final CamundaFuture<Void> future = mock(CamundaFuture.class);

    when(externalTask.getId()).thenReturn("123");
    when(externalTask.getProcessInstanceId()).thenReturn("456");
    when(client.newThrowErrorCommand(anyLong())).thenReturn(throwErrorCommandStep1);
    when(throwErrorCommandStep1.errorCode(anyString())).thenReturn(throwErrorCommandStep2);
    when(throwErrorCommandStep2.errorMessage(anyString())).thenReturn(throwErrorCommandStep2);
    when(throwErrorCommandStep2.errorMessage(isNull())).thenReturn(throwErrorCommandStep2);
    when(throwErrorCommandStep2.send()).thenReturn(future);

    final String errorCode = "my-error-code";
    final String errorMessage = "Well this failed, let's move to the error event";
    service.handleBpmnError(externalTask, errorCode);
    service.handleBpmnError(externalTask, errorCode, errorMessage);
    service.handleBpmnError(externalTask, errorCode, errorMessage, testVariables());
    service.handleBpmnError(externalTask.getId(), errorCode, errorMessage, testVariables());
  }

  @Test
  public void testExtendLock() {
    assertThrows(
        UnsupportedOperationException.class, () -> service.extendLock(externalTask, 2000L));
    assertThrows(
        UnsupportedOperationException.class, () -> service.extendLock(externalTask.getId(), 2000L));
  }

  private void setupSetVariablesCommand() {
    final CamundaFuture<SetVariablesResponse> future = mock(CamundaFuture.class);
    final SetVariablesCommandStep1 setVariablesCommandStep1 = mock(SetVariablesCommandStep1.class);
    final SetVariablesCommandStep2 setVariablesCommandStep2 = mock(SetVariablesCommandStep2.class);

    when(client.newSetVariablesCommand(anyLong())).thenReturn(setVariablesCommandStep1);
    when(setVariablesCommandStep1.variables(anyMap())).thenReturn(setVariablesCommandStep2);
    when(setVariablesCommandStep2.local(anyBoolean())).thenReturn(setVariablesCommandStep2);
    when(setVariablesCommandStep2.send()).thenReturn(future);
  }
}
