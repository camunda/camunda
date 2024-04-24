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
package io.camunda.operate.operation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.util.j5templates.OperateZeebeSearchAbstractIT;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto.MappingInstruction;
import io.camunda.operate.webapp.zeebe.operation.MigrateProcessInstanceHandler;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProcessInstanceMigrationIT extends OperateZeebeSearchAbstractIT {

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  @Autowired private MigrateProcessInstanceHandler migrateProcessInstanceHandler;

  @Test
  void shouldMigrateSubprocessToSubprocess() throws Exception {
    // given
    // process instances that are running
    final var processDefinitionFrom =
        operateTester.deployProcessAndWait("migration-subprocess.bpmn");
    final var processFrom = operateTester.startProcessAndWait("prWithSubprocess");
    operateTester.completeJob("taskA").waitForFlowNodeActive(processFrom, "subprocess");

    final var processDefinitionTo =
        operateTester.deployProcessAndWait("migration-subprocess2.bpmn");
    // when
    // execute MIGRATE_PROCESS_INSTANCE
    final var migrationPlan =
        new MigrationPlanDto()
            .setTargetProcessDefinitionKey(String.valueOf(processDefinitionTo))
            .setMappingInstructions(
                List.of(
                    new MappingInstruction()
                        .setSourceElementId("taskA")
                        .setTargetElementId("taskA"),
                    new MappingInstruction()
                        .setSourceElementId("subprocess")
                        .setTargetElementId("subprocess2"),
                    new MappingInstruction()
                        .setSourceElementId("innerSubprocess")
                        .setTargetElementId("innerSubprocess2"),
                    new MappingInstruction()
                        .setSourceElementId("taskB")
                        .setTargetElementId("taskB")));
    migrateProcessInstanceHandler.setZeebeClient(zeebeContainerManager.getClient());
    migrateProcessInstanceHandler.migrate(processFrom, migrationPlan);

    // then
    // subprocesses are migrated
    operateTester.waitForFlowNodeActive(processFrom, "subprocess2");
    final var subprocessFlowNodes =
        searchAllDocuments(flowNodeInstanceTemplate.getAlias(), FlowNodeInstanceEntity.class)
            .stream()
            .filter(fn -> fn.getType().equals(FlowNodeType.SUB_PROCESS))
            .toList();

    assertThat(subprocessFlowNodes).hasSize(2);
    assertMigratedFieldsByFlowNodeId(
        subprocessFlowNodes, "subprocess2", processFrom, processDefinitionTo, "prWithSubprocess2");
    assertMigratedFieldsByFlowNodeId(
        subprocessFlowNodes,
        "innerSubprocess2",
        processFrom,
        processDefinitionTo,
        "prWithSubprocess2");
  }

  private void assertMigratedFieldsByFlowNodeId(
      final List<FlowNodeInstanceEntity> candidates,
      final String flowNodeId,
      final Long instanceKey,
      final Long processDefinitionTo,
      final String bpmnProcessId) {
    final var flowNode =
        candidates.stream()
            .filter(fn -> fn.getFlowNodeId().equals(flowNodeId))
            .findFirst()
            .orElseThrow();
    assertThat(flowNode.getProcessInstanceKey()).isEqualTo(instanceKey);
    assertThat(flowNode.getProcessDefinitionKey()).isEqualTo(processDefinitionTo);
    assertThat(flowNode.getBpmnProcessId()).isEqualTo(bpmnProcessId);
  }

  protected <R> List<R> searchAllDocuments(final String index, final Class<R> clazz) {
    try {
      return testSearchRepository.searchAll(index, clazz);
    } catch (final IOException ex) {
      throw new OperateRuntimeException("Search failed for index " + index, ex);
    }
  }
}
