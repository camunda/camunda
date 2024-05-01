/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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

import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type;
import io.camunda.operate.webapp.zeebe.operation.ModifyProcessZeebeWrapper;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3;
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
    final String operationId = UUID.randomUUID().toString();
    final ModifyProcessInstanceRequestDto modifyInstructions =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey(String.valueOf(MOCK_PROCESS_INSTANCE_KEY))
            .setModifications(
                List.of(
                    new Modification().setModification(Type.ADD_TOKEN).setToFlowNodeId("taskB")));

    final OperationEntity operation =
        createOperationEntityDocument(batchOperationId, operationId, modifyInstructions);
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
    final String operationId = UUID.randomUUID().toString();
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
        createOperationEntityDocument(batchOperationId, operationId, modifyInstructions);
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
        createOperationEntityDocument(batchOperationId, operationId, modifyInstructions);
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
    final String operationId = UUID.randomUUID().toString();
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
        createOperationEntityDocument(batchOperationId, operationId, modifyInstructions);
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
      final String batchOperationId,
      final String operationId,
      final ModifyProcessInstanceRequestDto modifyInstructions)
      throws IOException {
    final OperationEntity operation =
        new OperationEntity()
            .setId(operationId)
            .setProcessInstanceKey(MOCK_PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(MOCK_PROCESS_DEFINITION_KEY)
            .setBpmnProcessId("demoProcess")
            .setType(OperationType.MODIFY_PROCESS_INSTANCE)
            .setState(OperationState.LOCKED)
            .setBatchOperationId(batchOperationId)
            .setLockOwner(operateProperties.getOperationExecutor().getWorkerId())
            .setModifyInstructions(objectMapper.writeValueAsString(modifyInstructions));

    testSearchRepository.createOrUpdateDocumentFromObject(
        operationTemplate.getFullQualifiedName(), operationId, operation);

    return operation;
  }
}
