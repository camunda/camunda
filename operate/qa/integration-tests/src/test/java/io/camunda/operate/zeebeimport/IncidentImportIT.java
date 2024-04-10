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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.util.j5templates.OperateZeebeSearchAbstractIT;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.MigrationPlan;
import io.camunda.zeebe.client.api.command.MigrationPlanBuilderImpl;
import io.camunda.zeebe.client.api.command.MigrationPlanImpl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class IncidentImportIT extends OperateZeebeSearchAbstractIT {

  @Autowired private IncidentTemplate incidentTemplate;

  @Test
  public void shouldImportMigratedIncident() throws IOException {

    // given
    final String bpmnSource = "double-task-incident.bpmn";
    final String bpmnTarget = "double-task.bpmn";
    final Long processDefinitionKeySource = operateTester.deployProcessAndWait(bpmnSource);
    final Long processDefinitionKeyTarget = operateTester.deployProcessAndWait(bpmnTarget);
    final ZeebeClient zeebeClient = zeebeContainerManager.getClient();

    // when
    final Long processInstanceKey = operateTester.startProcessAndWait("doubleTaskIncident");
    operateTester.waitUntilIncidentsAreActive(processInstanceKey, 1);

    final MigrationPlan migrationPlan =
        new MigrationPlanImpl(processDefinitionKeyTarget, new ArrayList<>());
    List.of("taskA", "taskB")
        .forEach(
            item ->
                migrationPlan
                    .getMappingInstructions()
                    .add(new MigrationPlanBuilderImpl.MappingInstruction(item + "Incident", item)));
    zeebeClient
        .newMigrateProcessInstanceCommand(processInstanceKey)
        .migrationPlan(migrationPlan)
        .send()
        .join();

    operateTester.waitUntilIncidentsInProcessAreActive("doubleTask", 1);
    final List<IncidentEntity> incidents =
        testSearchRepository.searchTerm(
            incidentTemplate.getAlias(),
            IncidentTemplate.PROCESS_INSTANCE_KEY,
            processInstanceKey,
            IncidentEntity.class,
            1);

    // then
    assertThat(incidents.size()).isEqualTo(1);
    assertThat(incidents.get(0).getState()).isEqualTo(IncidentState.ACTIVE);
    assertThat(incidents.get(0).getBpmnProcessId()).isEqualTo("doubleTask");
    assertThat(incidents.get(0).getProcessDefinitionKey()).isEqualTo(processDefinitionKeyTarget);
    assertThat(incidents.get(0).getFlowNodeId()).isEqualTo("taskA");
  }
}
