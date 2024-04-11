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
package io.camunda.operate.it;

// This test will be merged with ProcessRestServiceIT and ModifyProcessInstanceOperationZeebeIT in
// the next round of test refactoring
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.util.j5templates.OperateZeebeSearchAbstractIT;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.zeebe.operation.process.modify.ModifyProcessInstanceHandler;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class BatchModifyProcessInstanceOperationIT extends OperateZeebeSearchAbstractIT {
  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  @Autowired private VariableTemplate variableTemplate;
  @Autowired private ModifyProcessInstanceHandler modifyProcessInstanceHandler;

  @Override
  protected void runAdditionalBeforeAllSetup() {
    // Zeebe client utilized by the handler needs to be set to the one manually created during test
    // startup to correctly communicate with zeebe
    modifyProcessInstanceHandler.setZeebeClient(zeebeContainerManager.getClient());

    final Long processDefinitionKey = operateTester.deployProcess("demoProcess_v_2.bpmn");
    operateTester.waitForProcessDeployed(processDefinitionKey);
  }

  @Test
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
