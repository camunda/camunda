/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.Metrics;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.OperationExecutorProperties;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.operate.webapp.zeebe.operation.adapter.OperateServicesAdapter;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ModifyProcessInstanceHandlerTest {

  @Mock private OperateServicesAdapter operateServicesAdapter;
  @Mock private OperationsManager operationsManager;
  @Mock private BatchOperationWriter batchOperationWriter;
  @Mock private Metrics metrics;
  @Mock private FlowNodeInstanceReader flowNodeInstanceReader;

  @Mock(strictness = Strictness.LENIENT)
  private OperateProperties operateProperties;

  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private ModifyProcessInstanceHandler handler;

  @BeforeEach
  void setup() {
    final var executorProperties = new OperationExecutorProperties();
    executorProperties.setWorkerId("worker-1");
    when(operateProperties.getOperationExecutor()).thenReturn(executorProperties);
  }

  @Test
  void shouldFailOperationWhenVariableScopeBelongsToAnotherProcessInstance() throws Exception {
    // given
    final var request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("100")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Type.EDIT_VARIABLE)
                        .setScopeKey(999L)
                        .setVariables(Map.of("x", 1))));
    final var operation =
        new OperationEntity()
            .setId("op-1")
            .setProcessInstanceKey(100L)
            .setType(OperationType.MODIFY_PROCESS_INSTANCE)
            .setState(OperationState.LOCKED)
            .setLockOwner("worker-1")
            .setModifyInstructions(objectMapper.writeValueAsString(request));
    when(flowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(100L, Set.of(999L)))
        .thenReturn(Set.of());

    // when
    handler.handleWithException(operation);

    // then
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage())
        .isEqualTo("Scope 999 does not belong to process instance 100.");
    verifyNoInteractions(operateServicesAdapter);
  }

  @Test
  void shouldFailOperationWhenAncestorBelongsToAnotherProcessInstance() throws Exception {
    // given
    final var request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("100")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Type.ADD_TOKEN)
                        .setToFlowNodeId("taskB")
                        .setAncestorElementInstanceKey(999L)));
    final var operation =
        new OperationEntity()
            .setId("op-1")
            .setProcessInstanceKey(100L)
            .setType(OperationType.MODIFY_PROCESS_INSTANCE)
            .setState(OperationState.LOCKED)
            .setLockOwner("worker-1")
            .setModifyInstructions(objectMapper.writeValueAsString(request));
    when(flowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(100L, Set.of(999L)))
        .thenReturn(Set.of());

    // when
    handler.handleWithException(operation);

    // then
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage())
        .isEqualTo("Element instance 999 does not belong to process instance 100.");
    verifyNoInteractions(operateServicesAdapter);
  }

  @Test
  void shouldFailOperationWhenCancelledFlowNodeInstanceBelongsToAnotherProcessInstance()
      throws Exception {
    // given
    final var request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("100")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Type.CANCEL_TOKEN)
                        .setFromFlowNodeInstanceKey("999")));
    final var operation =
        new OperationEntity()
            .setId("op-1")
            .setProcessInstanceKey(100L)
            .setType(OperationType.MODIFY_PROCESS_INSTANCE)
            .setState(OperationState.LOCKED)
            .setLockOwner("worker-1")
            .setModifyInstructions(objectMapper.writeValueAsString(request));
    when(flowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(100L, Set.of(999L)))
        .thenReturn(Set.of());

    // when
    handler.handleWithException(operation);

    // then
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage())
        .isEqualTo("Element instance 999 does not belong to process instance 100.");
    verifyNoInteractions(operateServicesAdapter);
  }

  @Test
  void shouldDispatchNothingWhenLaterVariableModificationHasForeignScope() throws Exception {
    // given
    final var request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("100")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Type.EDIT_VARIABLE)
                        .setScopeKey(200L)
                        .setVariables(Map.of("x", 1)),
                    new Modification()
                        .setModification(Type.ADD_VARIABLE)
                        .setScopeKey(999L)
                        .setVariables(Map.of("y", 2))));
    final var operation =
        new OperationEntity()
            .setId("op-1")
            .setProcessInstanceKey(100L)
            .setType(OperationType.MODIFY_PROCESS_INSTANCE)
            .setState(OperationState.LOCKED)
            .setLockOwner("worker-1")
            .setModifyInstructions(objectMapper.writeValueAsString(request));
    when(flowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(100L, Set.of(200L, 999L)))
        .thenReturn(Set.of(200L));

    // when
    handler.handleWithException(operation);

    // then the valid modification before the foreign one is not applied either
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage())
        .isEqualTo("Scope 999 does not belong to process instance 100.");
    verifyNoInteractions(operateServicesAdapter);
  }

  @Test
  void shouldDispatchNothingWhenVariableModificationIsValidButTokenModificationIsForeign()
      throws Exception {
    // given
    final var request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("100")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Type.EDIT_VARIABLE)
                        .setScopeKey(200L)
                        .setVariables(Map.of("x", 1)),
                    new Modification()
                        .setModification(Type.ADD_TOKEN)
                        .setToFlowNodeId("taskB")
                        .setAncestorElementInstanceKey(999L)));
    final var operation =
        new OperationEntity()
            .setId("op-1")
            .setProcessInstanceKey(100L)
            .setType(OperationType.MODIFY_PROCESS_INSTANCE)
            .setState(OperationState.LOCKED)
            .setLockOwner("worker-1")
            .setModifyInstructions(objectMapper.writeValueAsString(request));
    when(flowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(100L, Set.of(200L, 999L)))
        .thenReturn(Set.of(200L));

    // when
    handler.handleWithException(operation);

    // then the variable modification is not applied either
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage())
        .isEqualTo("Element instance 999 does not belong to process instance 100.");
    verifyNoInteractions(operateServicesAdapter);
  }

  @Test
  void shouldFailOperationWhenInstructionsAreForAnotherProcessInstanceThanTheOperation()
      throws Exception {
    // given
    final var request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("200")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Type.ADD_VARIABLE)
                        .setVariables(Map.of("x", 1))));
    final var operation =
        new OperationEntity()
            .setId("op-1")
            .setProcessInstanceKey(100L)
            .setType(OperationType.MODIFY_PROCESS_INSTANCE)
            .setState(OperationState.LOCKED)
            .setLockOwner("worker-1")
            .setModifyInstructions(objectMapper.writeValueAsString(request));

    // when
    handler.handleWithException(operation);

    // then
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage())
        .isEqualTo(
            "The modifications are for process instance 200, but the operation is for process instance 100.");
    verifyNoInteractions(operateServicesAdapter);
  }

  @Test
  void shouldApplyModificationsWhenAllKeysBelongToProcessInstance() throws Exception {
    // given
    final var addToken =
        new Modification()
            .setModification(Type.ADD_TOKEN)
            .setToFlowNodeId("taskB")
            .setAncestorElementInstanceKey(200L);
    final var moveToken =
        new Modification()
            .setModification(Type.MOVE_TOKEN)
            .setFromFlowNodeInstanceKey("201")
            .setToFlowNodeId("taskC")
            .setAncestorElementInstanceKey(200L);
    final var request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("100")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Type.EDIT_VARIABLE)
                        .setScopeKey(200L)
                        .setVariables(Map.of("x", 1)),
                    addToken,
                    moveToken));
    final var operation =
        new OperationEntity()
            .setId("op-1")
            .setProcessInstanceKey(100L)
            .setType(OperationType.MODIFY_PROCESS_INSTANCE)
            .setState(OperationState.LOCKED)
            .setLockOwner("worker-1")
            .setModifyInstructions(objectMapper.writeValueAsString(request));
    when(flowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(100L, Set.of(200L, 201L)))
        .thenReturn(Set.of(200L, 201L));

    // when
    handler.handleWithException(operation);

    // then
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    verify(operateServicesAdapter).setVariables(200L, Map.of("x", 1), true, "op-1");
    verify(operateServicesAdapter)
        .modifyProcessInstance(100L, List.of(addToken, moveToken), "op-1");
  }

  @Test
  void shouldApplyVariableModificationWithoutScopeKeyOnProcessInstance() throws Exception {
    // given
    final var request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("100")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Type.ADD_VARIABLE)
                        .setVariables(Map.of("x", 1))));
    final var operation =
        new OperationEntity()
            .setId("op-1")
            .setProcessInstanceKey(100L)
            .setType(OperationType.MODIFY_PROCESS_INSTANCE)
            .setState(OperationState.LOCKED)
            .setLockOwner("worker-1")
            .setModifyInstructions(objectMapper.writeValueAsString(request));

    // when
    handler.handleWithException(operation);

    // then
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    verify(operateServicesAdapter).setVariables(100L, Map.of("x", 1), true, "op-1");
    verifyNoInteractions(flowNodeInstanceReader);
  }
}
