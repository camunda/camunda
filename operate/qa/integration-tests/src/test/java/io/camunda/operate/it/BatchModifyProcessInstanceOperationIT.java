/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

// This test will be merged with ProcessRestServiceIT and ModifyProcessInstanceOperationZeebeIT in
// the next round of test refactoring
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.j5templates.OperateZeebeSearchAbstractIT;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.zeebe.operation.process.modify.ModifyProcessInstanceHandler;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class BatchModifyProcessInstanceOperationIT extends OperateZeebeSearchAbstractIT {

  @Autowired
  @Qualifier("operateVariableTemplate")
  private VariableTemplate variableTemplate;

  @Autowired
  @Qualifier("operateFlowNodeInstanceTemplate")
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired private ModifyProcessInstanceHandler modifyProcessInstanceHandler;

  @Override
  protected void runAdditionalBeforeAllSetup() {
    // Zeebe client utilized by the handler needs to be set to the one manually created during test
    // startup to correctly communicate with zeebe
    modifyProcessInstanceHandler.setCamundaClient(zeebeContainerManager.getClient());

    final Long processDefinitionKey = operateTester.deployProcess("demoProcess_v_2.bpmn");
    operateTester.waitForProcessDeployed(processDefinitionKey);
  }

  @Disabled("To be re-enabled with the fix in https://github.com/camunda/camunda/issues/24084")
  public void shouldMoveTokenInBatchCall() throws Exception {
    final String bpmnProcessId = "demoProcess";
    final String sourceFlowNodeId = "taskA";
    final String targetFlowNodeId = "taskB";

    // Deploy two processes
    final Long firstProcessInstanceKey =
        operateTester.startProcess(bpmnProcessId, "{\"a\": \"b\"}");
    final Long secondProcessInstanceKey =
        operateTester.startProcess(bpmnProcessId, "{\"c\": \"d\"}");
    operateTester.waitForProcessInstanceStarted(firstProcessInstanceKey);
    operateTester.waitForProcessInstanceStarted(secondProcessInstanceKey);

    // Create the modification that should apply to all active processes.
    final CreateBatchOperationRequestDto op =
        new CreateBatchOperationRequestDto()
            .setOperationType(OperationType.MODIFY_PROCESS_INSTANCE)
            .setQuery(
                new ListViewQueryDto()
                    .setRunning(true)
                    .setActive(true)
                    .setIds(
                        List.of(
                            firstProcessInstanceKey.toString(),
                            "123",
                            secondProcessInstanceKey.toString())))
            .setName("Batch modification")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Modification.Type.MOVE_TOKEN)
                        .setFromFlowNodeId(sourceFlowNodeId)
                        .setToFlowNodeId(targetFlowNodeId)
                        .setVariables(Map.of(targetFlowNodeId, List.of(Map.of("e", "f"))))));

    operateTester.batchProcessInstanceOperation(op);

    operateTester.waitForOperationFinished(firstProcessInstanceKey);
    operateTester.waitForOperationFinished(secondProcessInstanceKey);

    operateTester.waitForFlowNodeActive(firstProcessInstanceKey, targetFlowNodeId);
    operateTester.waitForFlowNodeActive(secondProcessInstanceKey, targetFlowNodeId);

    // Source flow node on first process should be cancelled
    var result = queryFlowNode(sourceFlowNodeId, firstProcessInstanceKey);
    assertThat(result.get(0).getState()).isEqualTo(FlowNodeState.TERMINATED);

    // Target flow node on first process should be active
    result = queryFlowNode(targetFlowNodeId, firstProcessInstanceKey);
    assertThat(result).isNotEmpty();
    assertThat(result.get(0).getState()).isEqualTo(FlowNodeState.ACTIVE);

    // Variables should be present on target flow node
    var variableResult = queryVariables("e", result.get(0).getKey());
    assertThat(variableResult).isNotEmpty();
    assertThat(variableResult.get(0).getValue()).isEqualTo("\"f\"");

    // Source flow node on second process should be cancelled
    result = queryFlowNode(sourceFlowNodeId, secondProcessInstanceKey);
    assertThat(result.get(0).getState()).isEqualTo(FlowNodeState.TERMINATED);

    // Target flow node on second process should be active
    result = queryFlowNode(targetFlowNodeId, secondProcessInstanceKey);
    assertThat(result).isNotEmpty();
    assertThat(result.get(0).getState()).isEqualTo(FlowNodeState.ACTIVE);

    // Variables should be present on target flow node
    variableResult = queryVariables("e", result.get(0).getKey());
    assertThat(variableResult).isNotEmpty();
    assertThat(variableResult.get(0).getValue()).isEqualTo("\"f\"");
  }

  private List<FlowNodeInstanceEntity> queryFlowNode(
      final String flowNodeId, final Long processInstanceKey) throws IOException {
    return testSearchRepository.searchTerms(
        flowNodeInstanceTemplate.getFullQualifiedName(),
        Map.of(
            FlowNodeInstanceTemplate.FLOW_NODE_ID,
            flowNodeId,
            FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY,
            processInstanceKey),
        FlowNodeInstanceEntity.class,
        10);
  }

  private List<VariableEntity> queryVariables(final String varName, final Long scopeKey)
      throws IOException {
    return testSearchRepository.searchTerms(
        variableTemplate.getFullQualifiedName(),
        Map.of(VariableTemplate.NAME, varName, VariableTemplate.SCOPE_KEY, scopeKey),
        VariableEntity.class,
        10);
  }
}
