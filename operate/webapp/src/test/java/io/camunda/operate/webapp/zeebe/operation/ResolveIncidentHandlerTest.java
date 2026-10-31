/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.impl.CamundaClientFutureImpl;
import io.camunda.client.impl.command.JobUpdateRetriesCommandImpl;
import io.camunda.client.impl.command.ResolveIncidentCommandImpl;
import io.camunda.operate.Metrics;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.OperationExecutorProperties;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.elasticsearch.writer.BatchOperationWriter;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.zeebe.operation.adapter.OperateServicesAdapter;
import io.camunda.service.exception.ServiceException;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ResolveIncidentHandlerTest {

  @Mock IncidentReader incidentReader;
  @Spy CamundaClient camundaClient;
  @Spy OperateServicesAdapter operateServicesAdapter;
  @Mock JobUpdateRetriesCommandImpl updateRetriesCommand;
  @Mock ResolveIncidentCommandImpl resolveIncidentCommand;

  @Mock BatchOperationWriter batchOperationWriter;

  @Mock Metrics metrics;

  @Mock OperateProperties operateProperties;

  @Mock OperationsManager operationsManager;

  @InjectMocks private ResolveIncidentHandler resolveIncidentHandler;

  @Test
  public void shouldUpdateRetriesBeforeResolvingTaskListenerIncident() throws Exception {
    // given
    final Long incidentKey = 123L;
    final Long jobKey = 1L;
    final Long processInstanceKey = 100L;
    final String workerId = "1";
    when(incidentReader.getIncidentById(incidentKey))
        .thenReturn(
            new IncidentEntity()
                .setErrorType(ErrorType.TASK_LISTENER_NO_RETRIES)
                .setJobKey(jobKey)
                .setKey(incidentKey)
                .setProcessInstanceKey(processInstanceKey));

    final String operationId = "456";
    final OperationEntity operationEntity =
        new OperationEntity()
            .setType(OperationType.RESOLVE_INCIDENT)
            .setIncidentKey(incidentKey)
            .setProcessInstanceKey(processInstanceKey)
            .setId(operationId)
            .setState(OperationState.LOCKED)
            .setLockOwner(workerId);

    final CamundaClientFutureImpl future1 = new CamundaClientFutureImpl<>();
    final CamundaClientFutureImpl future2 = new CamundaClientFutureImpl<>();
    future1.complete(null);
    future2.complete(null);

    doNothing().when(batchOperationWriter).updateOperation(any());
    doNothing().when(metrics).recordCounts(any(String.class), any(long.class), any(String[].class));
    final OperationExecutorProperties properties = new OperationExecutorProperties();
    properties.setWorkerId(workerId);
    when(operateProperties.getOperationExecutor()).thenReturn(properties);

    final InOrder inOrder = Mockito.inOrder(operateServicesAdapter);

    // when
    resolveIncidentHandler.handleWithException(operationEntity);

    // then
    verify(operateServicesAdapter, times(1)).updateJobRetries(jobKey, 1, operationId);
    verify(operateServicesAdapter, times(1)).resolveIncident(incidentKey, operationId);
    inOrder.verify(operateServicesAdapter).updateJobRetries(jobKey, 1, operationId);
    inOrder.verify(operateServicesAdapter).resolveIncident(incidentKey, operationId);
    verify(operationsManager, never()).completeOperation(any());
  }

  @Test
  public void shouldCompleteWhenIncidentAlreadyGoneInEngine() throws Exception {
    final Long incidentKey = 123L;
    final Long processInstanceKey = 100L;
    final String workerId = "1";
    when(incidentReader.getIncidentById(incidentKey))
        .thenReturn(
            new IncidentEntity()
                .setKey(incidentKey)
                .setProcessInstanceKey(processInstanceKey));

    final OperationEntity operationEntity =
        new OperationEntity()
            .setType(OperationType.RESOLVE_INCIDENT)
            .setIncidentKey(incidentKey)
            .setProcessInstanceKey(processInstanceKey)
            .setId("456")
            .setState(OperationState.LOCKED)
            .setLockOwner(workerId);

    doThrow(
            new CompletionException(
                new ServiceException(
                    "Expected to resolve incident with key '123', but no such incident was found",
                    ServiceException.Status.NOT_FOUND)))
        .when(operateServicesAdapter)
        .resolveIncident(incidentKey, "456");

    resolveIncidentHandler.handleWithException(operationEntity);

    verify(operateServicesAdapter).resolveIncident(incidentKey, "456");
    verify(operationsManager).completeOperation(operationEntity);
    verify(batchOperationWriter, never()).updateOperation(any());
  }

  @Test
  public void shouldRethrowWhenResolveFailsForOtherReason() {
    final Long incidentKey = 123L;
    final Long processInstanceKey = 100L;
    when(incidentReader.getIncidentById(incidentKey))
        .thenReturn(
            new IncidentEntity()
                .setKey(incidentKey)
                .setProcessInstanceKey(processInstanceKey));

    final OperationEntity operationEntity =
        new OperationEntity()
            .setType(OperationType.RESOLVE_INCIDENT)
            .setIncidentKey(incidentKey)
            .setProcessInstanceKey(processInstanceKey)
            .setId("456")
            .setState(OperationState.LOCKED)
            .setLockOwner("1");

    doThrow(
            new CompletionException(
                new ServiceException("broker unavailable", ServiceException.Status.UNAVAILABLE)))
        .when(operateServicesAdapter)
        .resolveIncident(incidentKey, "456");

    assertThatThrownBy(() -> resolveIncidentHandler.handleWithException(operationEntity))
        .isInstanceOf(CompletionException.class);
  }

  @Test
  public void isIncidentNotFoundRecognizesWrappedServiceException() {
    final var wrapped =
        new CompletionException(
            new ServiceException("no such incident", ServiceException.Status.NOT_FOUND));
    assertThat(ResolveIncidentHandler.isIncidentNotFound(wrapped)).isTrue();
    assertThat(
            ResolveIncidentHandler.isIncidentNotFound(
                new ServiceException("bad", ServiceException.Status.INVALID_ARGUMENT)))
        .isFalse();
  }

  static Stream<Arguments> mismatchingProcessInstanceKeys() {
    // incident process instance key, operation process instance key
    return Stream.of(
        Arguments.of(999L, 100L), // incident belongs to a different process instance
        Arguments.of(null, 100L), // incident carries no process instance key
        Arguments.of(100L, null), // operation carries no process instance key
        Arguments.of(null, null)); // neither carries a process instance key
  }

  @ParameterizedTest(name = "incident PI key={0}, operation PI key={1}")
  @MethodSource("mismatchingProcessInstanceKeys")
  public void shouldFailOperationWhenIncidentDoesNotBelongToProcessInstance(
      final Long incidentProcessInstanceKey, final Long operationProcessInstanceKey)
      throws Exception {
    // given
    final Long incidentKey = 123L;
    final Long jobKey = 1L;
    final String workerId = "1";
    when(incidentReader.getIncidentById(incidentKey))
        .thenReturn(
            new IncidentEntity()
                .setErrorType(ErrorType.JOB_NO_RETRIES)
                .setJobKey(jobKey)
                .setKey(incidentKey)
                .setProcessInstanceKey(incidentProcessInstanceKey));

    final String operationId = "789";
    final OperationEntity operationEntity =
        new OperationEntity()
            .setType(OperationType.RESOLVE_INCIDENT)
            .setIncidentKey(incidentKey)
            .setProcessInstanceKey(operationProcessInstanceKey)
            .setId(operationId)
            .setState(OperationState.LOCKED)
            .setLockOwner(workerId);

    doNothing().when(batchOperationWriter).updateOperation(any());
    doNothing().when(metrics).recordCounts(any(String.class), any(long.class), any(String[].class));
    final OperationExecutorProperties properties = new OperationExecutorProperties();
    properties.setWorkerId(workerId);
    when(operateProperties.getOperationExecutor()).thenReturn(properties);

    // when
    resolveIncidentHandler.handleWithException(operationEntity);

    // then: neither the job retries are updated nor the incident is resolved
    verify(operateServicesAdapter, never()).updateJobRetries(anyLong(), anyInt(), any());
    verify(operateServicesAdapter, never()).resolveIncident(anyLong(), any());

    // and the operation is failed
    final ArgumentCaptor<OperationEntity> captor = ArgumentCaptor.forClass(OperationEntity.class);
    verify(batchOperationWriter).updateOperation(captor.capture());
    assertThat(captor.getValue().getState()).isEqualTo(OperationState.FAILED);
    assertThat(captor.getValue().getErrorMessage())
        .isEqualTo(
            String.format(
                "No incident %s found for process instance %s.",
                incidentKey, operationProcessInstanceKey));
  }
}
