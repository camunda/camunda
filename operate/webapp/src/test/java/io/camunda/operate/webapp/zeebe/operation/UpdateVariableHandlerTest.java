/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.SetVariablesCommandStep1;
import io.camunda.client.api.command.SetVariablesCommandStep1.SetVariablesCommandStep2;
import io.camunda.client.api.response.SetVariablesResponse;
import io.camunda.client.impl.CamundaClientFutureImpl;
import io.camunda.operate.Metrics;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.OperationExecutorProperties;
import io.camunda.operate.webapp.elasticsearch.writer.BatchOperationWriter;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UpdateVariableHandlerTest {

  @Mock private CamundaClient camundaClient;
  @Mock private BatchOperationWriter batchOperationWriter;

  @Mock(strictness = Strictness.LENIENT)
  private OperateProperties operateProperties;

  @Mock private Metrics metrics;
  @Mock private SetVariablesCommandStep1 setVariablesCommandStep1;
  @Mock private SetVariablesCommandStep2 setVariablesCommandStep2;

  @InjectMocks private UpdateVariableHandler handler;

  private final String operationId = "123";
  private final Long scopeKey = 456L;
  private final Long variableDocumentKey = 995L;
  private final String variableName = "x";
  private final String variableValue = "1";
  private final String variablesDocumentJson = "{\"%s\":%s}".formatted(variableName, variableValue);
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

    final var setVariablesRespFuture =
        new CamundaClientFutureImpl<SetVariablesResponse, GatewayOuterClass.SetVariablesResponse>();
    setVariablesRespFuture.complete(() -> variableDocumentKey);
    when(setVariablesCommandStep2.send()).thenReturn(setVariablesRespFuture);
    mockSetVariablesCommand(setVariablesRespFuture);

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

  /**
   * Creates a locked UPDATE_VARIABLE operation, simulating an operation that was picked up by the
   * executor for processing.
   */
  private OperationEntity createLockedOperation() {
    return new OperationEntity()
        .setId(operationId)
        .setType(OperationType.UPDATE_VARIABLE)
        .setVariableName(variableName)
        .setVariableValue(variableValue)
        .setScopeKey(scopeKey)
        .setState(OperationState.LOCKED)
        .setLockOwner(workerId);
  }

  /**
   * Mocks the full {@link CamundaClient#newSetVariablesCommand(long)} command pipeline up to the
   * {@code .send()} invocation, returning the specified future (success or failure).
   */
  private void mockSetVariablesCommand(
      final CamundaClientFutureImpl<SetVariablesResponse, GatewayOuterClass.SetVariablesResponse>
          expectedSetVariablesFuture) {
    when(camundaClient.newSetVariablesCommand(scopeKey)).thenReturn(setVariablesCommandStep1);
    when(setVariablesCommandStep1.variables(variablesDocumentJson))
        .thenReturn(setVariablesCommandStep2);
    when(setVariablesCommandStep2.local(true)).thenReturn(setVariablesCommandStep2);
    when(setVariablesCommandStep2.operationReference(Long.parseLong(operationId)))
        .thenReturn(setVariablesCommandStep2);
    when(setVariablesCommandStep2.send()).thenReturn(expectedSetVariablesFuture);
  }
}
