/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.api.command.ClientException;
import io.camunda.operate.Metrics;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.OperationExecutorProperties;
import io.camunda.operate.webapp.elasticsearch.writer.BatchOperationWriter;
import io.camunda.operate.webapp.zeebe.operation.adapter.OperateServicesAdapter;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UpdateVariableHandlerTest {

  @Mock private OperateServicesAdapter operateServicesAdapter;
  @Mock private BatchOperationWriter batchOperationWriter;

  @Mock(strictness = Strictness.LENIENT)
  private OperateProperties operateProperties;

  @Mock private Metrics metrics;

  @InjectMocks private UpdateVariableHandler handler;

  private final String operationId = "123";
  private final Long scopeKey = 456L;
  private final Long variableDocumentKey = 995L;
  private final String variableName = "x";
  private final int variableValue = 1;
  private final String workerId = "testWorker";

  @BeforeEach
  void setup() {
    final var executorProps = new OperationExecutorProperties();
    executorProps.setWorkerId(workerId);
    when(operateProperties.getOperationExecutor()).thenReturn(executorProps);
  }

  @Test
  void shouldSendSetVariablesCommandSuccessfully() throws Exception {
    // given
    final var operation = createLockedOperation();
    doReturn(variableDocumentKey)
        .when(operateServicesAdapter)
        .setVariables(scopeKey, Map.of(variableName, variableValue), true, operationId);

    // when
    handler.handle(operation);

    // then: assert that the operation was marked as `SENT` and `variableDocumentKey` captured
    verify(batchOperationWriter).updateOperation(operation);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getZeebeCommandKey()).isEqualTo(variableDocumentKey);

    // assert that command metrics were recorded correctly
    verify(metrics)
        .recordCounts(
            Metrics.COUNTER_NAME_COMMANDS,
            1,
            Metrics.TAG_KEY_STATUS,
            OperationState.SENT.name(),
            Metrics.TAG_KEY_TYPE,
            OperationType.UPDATE_VARIABLE.name());
  }

  @ParameterizedTest
  @EnumSource(
      value = Code.class,
      names = {"UNAVAILABLE", "RESOURCE_EXHAUSTED", "DEADLINE_EXCEEDED"})
  void shouldRetryOnRetriableGrpcStatus(final Code retriableStatusCode) throws Exception {
    // given
    final var operation = createLockedOperation();
    // simulate a retriable gRPC status code wrapped in a client exception
    final var clientException =
        new ClientException(
            new StatusRuntimeException(
                Status.fromCode(retriableStatusCode).withDescription("Will be retried")));
    doThrow(clientException)
        .when(operateServicesAdapter)
        .setVariables(scopeKey, Map.of(variableName, variableValue), true, operationId);
    when(operateServicesAdapter.isExceptionRetriable(clientException)).thenReturn(Boolean.TRUE);

    // when
    handler.handle(operation);

    // then: should NOT mark operation as failed, allowing retry later
    verify(batchOperationWriter, never()).updateOperation(any());
    assertThat(operation.getState()).isEqualTo(OperationState.LOCKED);
    // no metrics updates for retried operation
    verifyNoInteractions(metrics);
  }

  @Test
  void shouldFailOperationWhenDeniedByTaskListener() throws Exception {
    // given
    final var operation = createLockedOperation();

    final var statusException =
        new StatusRuntimeException(
            Status.fromCode(Code.FAILED_PRECONDITION).withDescription("Update was denied"));
    doThrow(statusException)
        .when(operateServicesAdapter)
        .setVariables(scopeKey, Map.of(variableName, variableValue), true, operationId);

    // when
    handler.handle(operation);

    // then: assert that the operation was marked as `FAILED` and error message captured
    verify(batchOperationWriter).updateOperation(operation);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage())
        .startsWith("Unable to process operation")
        .endsWith("Update was denied");

    // assert that failure metric was recorded
    verify(metrics)
        .recordCounts(
            Metrics.COUNTER_NAME_COMMANDS,
            1,
            Metrics.TAG_KEY_STATUS,
            OperationState.FAILED.name(),
            Metrics.TAG_KEY_TYPE,
            OperationType.UPDATE_VARIABLE.name());
  }

  /**
   * Creates a locked UPDATE_VARIABLE operation, simulating an operation that was picked up by the
   * executor for processing.
   */
  private OperationEntity createLockedOperation() {
    return new OperationEntity()
        .setId(operationId)
        .setType(OperationType.UPDATE_VARIABLE)
        .setVariableName(variableName)
        .setVariableValue(String.valueOf(variableValue))
        .setScopeKey(scopeKey)
        .setState(OperationState.LOCKED)
        .setLockOwner(workerId);
  }
}
