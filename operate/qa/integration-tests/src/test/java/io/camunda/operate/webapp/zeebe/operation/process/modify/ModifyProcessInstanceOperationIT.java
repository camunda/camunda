/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type;
import io.camunda.operate.webapp.zeebe.operation.ModifyProcessZeebeWrapper;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class ModifyProcessInstanceOperationIT extends OperateSearchAbstractIT {
  private static final Long MOCK_PROCESS_DEFINITION_KEY = 2251799813685249L;
  private static final Long MOCK_PROCESS_INSTANCE_KEY = 2251799813685251L;

  @MockBean private ModifyProcessZeebeWrapper mockZeebeHelper;
  @Autowired private SingleStepModifyProcessInstanceHandler modifyProcessInstanceHandler;
  @Autowired private BatchOperationTemplate batchOperationTemplate;
  @Autowired private OperationTemplate operationTemplate;
  @Autowired private OperateProperties operateProperties;
  @MockBean private FlowNodeInstanceReader mockFlowNodeInstanceReader;
  private ModifyProcessInstanceCommandStep1 mockZeebeCommand;

  @Test
  public void shouldAddToken() throws Exception {
    // Setup mocks
    createMockZeebeCommand();

    // Create batch command that would exist in search before execution
    final String batchOperationId = UUID.randomUUID().toString();
    createBatchCommandDocument(batchOperationId);

    // Create operation entity to process
    final ModifyProcessInstanceRequestDto modifyInstructions =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey(String.valueOf(MOCK_PROCESS_INSTANCE_KEY))
            .setModifications(
                List.of(
                    new Modification().setModification(Type.ADD_TOKEN).setToFlowNodeId("taskB")));

    final OperationEntity operation =
        createOperationEntityDocument(batchOperationId, modifyInstructions);
    searchContainerManager.refreshIndices("*operation*");

    // Test execution of the operation entity
    modifyProcessInstanceHandler.handleWithException(operation);

    // Refresh the indices so any bulk operation updates show on queries
    searchContainerManager.refreshIndices("*operation*");

    // Validate the command sent to zeebe has all expected subcommands
    assertThat(Mockito.mockingDetails(mockZeebeCommand).getInvocations()).hasSize(1);
    verify(mockZeebeCommand, times(1)).activateElement("taskB");

    // Validate the operation entity was updated in search to COMPLETED and all locks removed
    final OperationEntity updatedOps =
        testSearchRepository
            .searchTerm(
                operationTemplate.getFullQualifiedName(),
                OperationTemplate.ID,
                operation.getId(),
                OperationEntity.class,
                1)
            .getFirst();
    assertThat(updatedOps.getLockOwner()).isNull();
    assertThat(updatedOps.getLockExpirationTime()).isNull();
    assertThat(updatedOps.getState()).isEqualTo(OperationState.COMPLETED);
  }

  @Test
  public void shouldAddTokenWithVariables() throws Exception {
    // Setup mocks
    createMockZeebeCommand();

    // Create batch command that would exist in search before execution
    final String batchOperationId = UUID.randomUUID().toString();
    createBatchCommandDocument(batchOperationId);

    // Create operation entity to process
    final ModifyProcessInstanceRequestDto modifyInstructions =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey(String.valueOf(MOCK_PROCESS_INSTANCE_KEY))
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Type.ADD_TOKEN)
                        .setToFlowNodeId("taskB")
                        .setVariables(Map.of("taskB", List.of(Map.of("c", "d"))))));

    final OperationEntity operation =
        createOperationEntityDocument(batchOperationId, modifyInstructions);
    searchContainerManager.refreshIndices("*operation*");

    // Test execution of the operation entity
    modifyProcessInstanceHandler.handleWithException(operation);

    // Refresh the indices so any bulk operation updates show on queries
    searchContainerManager.refreshIndices("*operation*");

    // Validate the command sent to zeebe has all expected subcommands
    assertThat(Mockito.mockingDetails(mockZeebeCommand).getInvocations()).hasSize(2);
    verify((ModifyProcessInstanceCommandStep3) mockZeebeCommand, times(1))
        .withVariables(Map.of("c", "d"), "taskB");
    verify(mockZeebeCommand, times(1)).activateElement("taskB");

    // Validate the operation entity was updated in search to COMPLETED and all locks removed
    final OperationEntity updatedOps =
        testSearchRepository
            .searchTerm(
                operationTemplate.getFullQualifiedName(),
                OperationTemplate.ID,
                operation.getId(),
                OperationEntity.class,
                1)
            .getFirst();
    assertThat(updatedOps.getLockOwner()).isNull();
    assertThat(updatedOps.getLockExpirationTime()).isNull();
    assertThat(updatedOps.getState()).isEqualTo(OperationState.COMPLETED);
  }

  @Test
  public void shouldCancelToken() throws Exception {
    // Setup mocks
    createMockZeebeCommand();
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
            MOCK_PROCESS_INSTANCE_KEY, "taskA", List.of(FlowNodeState.ACTIVE)))
        .thenReturn(List.of(123L));

    // Create batch command that would exist in search before execution
    final String batchOperationId = UUID.randomUUID().toString();
    createBatchCommandDocument(batchOperationId);

    // Create operation entity to process
    final String operationId = UUID.randomUUID().toString();
    final ModifyProcessInstanceRequestDto modifyInstructions =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey(String.valueOf(MOCK_PROCESS_INSTANCE_KEY))
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Type.CANCEL_TOKEN)
                        .setFromFlowNodeId("taskA")));

    final OperationEntity operation =
        createOperationEntityDocument(batchOperationId, modifyInstructions);
    searchContainerManager.refreshIndices("*operation*");

    // Test execution of the operation entity
    modifyProcessInstanceHandler.handleWithException(operation);

    // Refresh the indices so any bulk operation updates show on queries
    searchContainerManager.refreshIndices("*operation*");

    // Validate that the flow node instance ids were read
    verify(mockFlowNodeInstanceReader, times(1))
        .getFlowNodeInstanceKeysByIdAndStates(
            MOCK_PROCESS_INSTANCE_KEY, "taskA", List.of(FlowNodeState.ACTIVE));

    // Validate the command sent to zeebe has all expected subcommands
    assertThat(Mockito.mockingDetails(mockZeebeCommand).getInvocations()).hasSize(1);
    verify(mockZeebeCommand, times(1)).terminateElement(123L);

    // Validate the operation entity was updated in search to COMPLETED and all locks removed
    final OperationEntity updatedOps =
        testSearchRepository
            .searchTerm(
                operationTemplate.getFullQualifiedName(),
                OperationTemplate.ID,
                operation.getId(),
                OperationEntity.class,
                1)
            .getFirst();
    assertThat(updatedOps.getLockOwner()).isNull();
    assertThat(updatedOps.getLockExpirationTime()).isNull();
    assertThat(updatedOps.getState()).isEqualTo(OperationState.COMPLETED);
  }

  @Test
  public void shouldMoveToken() throws Exception {
    // Setup mocks
    createMockZeebeCommand();
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
            MOCK_PROCESS_INSTANCE_KEY, "taskA", List.of(FlowNodeState.ACTIVE)))
        .thenReturn(List.of(123L));

    // Create batch command that would exist in search before execution
    final String batchOperationId = UUID.randomUUID().toString();
    createBatchCommandDocument(batchOperationId);

    // Create operation entity to process
    final ModifyProcessInstanceRequestDto modifyInstructions =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey(String.valueOf(MOCK_PROCESS_INSTANCE_KEY))
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Type.MOVE_TOKEN)
                        .setFromFlowNodeId("taskA")
                        .setToFlowNodeId("taskB")));

    final OperationEntity operation =
        createOperationEntityDocument(batchOperationId, modifyInstructions);
    searchContainerManager.refreshIndices("*operation*");

    // Test execution of the operation entity
    modifyProcessInstanceHandler.handleWithException(operation);

    // Refresh the indices so any bulk operation updates show on queries
    searchContainerManager.refreshIndices("*operation*");

    // Validate that the flow node instance ids were read
    verify(mockFlowNodeInstanceReader, times(2))
        .getFlowNodeInstanceKeysByIdAndStates(
            MOCK_PROCESS_INSTANCE_KEY, "taskA", List.of(FlowNodeState.ACTIVE));

    // Validate the command sent to zeebe has all expected subcommands
    assertThat(Mockito.mockingDetails(mockZeebeCommand).getInvocations()).hasSize(3);
    verify(mockZeebeCommand, times(1)).activateElement("taskB");
    verify(mockZeebeCommand, times(1)).terminateElement(123L);
    verify((ModifyProcessInstanceCommandStep2) mockZeebeCommand, times(1)).and();

    // Validate the operation entity was updated in search to COMPLETED and all locks removed
    final OperationEntity updatedOps =
        testSearchRepository
            .searchTerm(
                operationTemplate.getFullQualifiedName(),
                OperationTemplate.ID,
                operation.getId(),
                OperationEntity.class,
                1)
            .getFirst();
    assertThat(updatedOps.getLockOwner()).isNull();
    assertThat(updatedOps.getLockExpirationTime()).isNull();
    assertThat(updatedOps.getState()).isEqualTo(OperationState.COMPLETED);
  }

  private void createMockZeebeCommand() {
    mockZeebeCommand =
        Mockito.mock(
            ModifyProcessInstanceCommandStep1.class,
            withSettings()
                .extraInterfaces(
                    ModifyProcessInstanceCommandStep2.class,
                    ModifyProcessInstanceCommandStep3.class));

    when(mockZeebeCommand.activateElement(anyString()))
        .thenReturn((ModifyProcessInstanceCommandStep3) mockZeebeCommand);
    when(mockZeebeCommand.terminateElement(anyLong()))
        .thenReturn((ModifyProcessInstanceCommandStep3) mockZeebeCommand);
    when(((ModifyProcessInstanceCommandStep2) mockZeebeCommand).and()).thenReturn(mockZeebeCommand);
    when(((ModifyProcessInstanceCommandStep3) mockZeebeCommand)
            .withVariables(anyMap(), anyString()))
        .thenReturn((ModifyProcessInstanceCommandStep3) mockZeebeCommand);

    when(mockZeebeHelper.newModifyProcessInstanceCommand(MOCK_PROCESS_INSTANCE_KEY))
        .thenReturn(mockZeebeCommand);
  }

  private void createBatchCommandDocument(final String batchOperationId) throws IOException {
    final BatchOperationEntity batchOperation =
        new BatchOperationEntity()
            .setId(batchOperationId)
            .setType(OperationType.MODIFY_PROCESS_INSTANCE)
            .setStartDate(OffsetDateTime.now())
            .setUsername("testuser")
            .setInstancesCount(1)
            .setOperationsTotalCount(1);
    testSearchRepository.createOrUpdateDocumentFromObject(
        batchOperationTemplate.getFullQualifiedName(), batchOperationId, batchOperation);
  }

  private OperationEntity createOperationEntityDocument(
      final String batchOperationId, final ModifyProcessInstanceRequestDto modifyInstructions)
      throws IOException {
    final OperationEntity operation =
        new OperationEntity()
            .withGeneratedId()
            .setProcessInstanceKey(MOCK_PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(MOCK_PROCESS_DEFINITION_KEY)
            .setBpmnProcessId("demoProcess")
            .setType(OperationType.MODIFY_PROCESS_INSTANCE)
            .setState(OperationState.LOCKED)
            .setBatchOperationId(batchOperationId)
            .setLockOwner(operateProperties.getOperationExecutor().getWorkerId())
            .setModifyInstructions(objectMapper.writeValueAsString(modifyInstructions));

    testSearchRepository.createOrUpdateDocumentFromObject(
        operationTemplate.getFullQualifiedName(), operation.getId(), operation);

    return operation;
  }
}
