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
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.ProcessFlowNodeEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.UserTaskTemplate;
import io.camunda.operate.util.j5templates.OperateZeebeSearchAbstractIT;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ZeebeImportIT extends OperateZeebeSearchAbstractIT {

  private Long processDefinitionKey;
  @Autowired private ProcessIndex processIndex;
  @Autowired private ListViewTemplate listViewTemplate;
  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  @Autowired private UserTaskTemplate userTaskTemplate;

  @Override
  protected void runAdditionalBeforeAllSetup() {
    processDefinitionKey = operateTester.deployProcess("demoProcess_v_2.bpmn");
    operateTester.waitForProcessDeployed(processDefinitionKey);
  }

  @Test
  public void shouldImportProcessDefinitions() throws IOException {
    final List<ProcessEntity> processEntityList =
        testSearchRepository.searchTerms(
            processIndex.getFullQualifiedName(),
            Map.of(ProcessIndex.KEY, processDefinitionKey),
            ProcessEntity.class,
            10);
    assertThat(processEntityList).hasSize(1);

    final ProcessEntity processDefinition = processEntityList.get(0);

    assertThat(processDefinition.getName()).isEqualTo("Demo process");
    assertThat(processDefinition.getVersion()).isEqualTo(1);
    assertThat(processDefinition.getBpmnProcessId()).isEqualTo("demoProcess");
    assertThat(processDefinition.getBpmnXml()).isNotNull();
    assertThat(processDefinition.getResourceName()).isEqualTo("demoProcess_v_2.bpmn");
    assertThat(processDefinition.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
    assertThat(processDefinition.getId()).isNotNull();
    assertThat(processDefinition.getFlowNodes()).hasSize(8);

    final ProcessFlowNodeEntity flowNode =
        processDefinition.getFlowNodes().stream()
            .filter(node -> "end".equals(node.getId()))
            .findFirst()
            .orElse(null);

    assertThat(flowNode).isNotNull();
    assertThat(flowNode.getId()).isEqualTo("end");
    assertThat(flowNode.getName()).isEqualTo("end");
  }

  @Test
  public void shouldImportProcessInstances() throws IOException {
    final Long processInstanceKey = operateTester.startProcess("demoProcess", "{\"a\": \"b\"}");
    operateTester.waitForProcessInstanceStarted(processInstanceKey);
    operateTester.waitForFlowNodeActive(processInstanceKey, "taskA");

    final List<ProcessInstanceForListViewEntity> processEntityList =
        testSearchRepository.searchTerms(
            listViewTemplate.getFullQualifiedName(),
            Map.of(ListViewTemplate.KEY, processInstanceKey),
            ProcessInstanceForListViewEntity.class,
            10);
    assertThat(processEntityList).hasSize(1);

    final ProcessInstanceForListViewEntity processInstance = processEntityList.get(0);
    assertThat(processInstance.getProcessName()).isEqualTo("Demo process");
    assertThat(processInstance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(processInstance.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(processInstance.getBpmnProcessId()).isEqualTo("demoProcess");
    assertThat(processInstance.getProcessVersion()).isEqualTo(1);
    assertThat(processInstance.getStartDate()).isNotNull();
    assertThat(processInstance.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(processInstance.getTreePath()).startsWith("PI_");
    assertThat(processInstance.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
    assertThat(processInstance.getKey()).isEqualTo(processInstanceKey);
    assertThat(processInstance.getPartitionId()).isEqualTo(1);
    assertThat(processInstance.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(processInstance.getJoinRelation()).isNotNull();

    final List<FlowNodeInstanceEntity> flowNodeEntityList =
        testSearchRepository.searchTerms(
            flowNodeInstanceTemplate.getFullQualifiedName(),
            Map.of(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey),
            FlowNodeInstanceEntity.class,
            10);
    assertThat(flowNodeEntityList).hasSize(4);

    final FlowNodeInstanceEntity flowNodeInstance = flowNodeEntityList.get(0);
    assertThat(flowNodeInstance.getFlowNodeId()).isEqualTo("start");
    assertThat(flowNodeInstance.getStartDate()).isNotNull();
    assertThat(flowNodeInstance.getEndDate()).isNotNull();
    assertThat(flowNodeInstance.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(flowNodeInstance.getType()).isEqualTo(FlowNodeType.START_EVENT);
    assertThat(flowNodeInstance.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(flowNodeInstance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(flowNodeInstance.getBpmnProcessId()).isEqualTo("demoProcess");
    assertThat(flowNodeInstance.getTreePath()).isNotNull();
    assertThat(flowNodeInstance.getLevel()).isEqualTo(1);
    assertThat(flowNodeInstance.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
    assertThat(flowNodeInstance.getKey()).isGreaterThan(0L);
    assertThat(flowNodeInstance.getId()).isEqualTo(String.valueOf(flowNodeInstance.getKey()));
  }
}
