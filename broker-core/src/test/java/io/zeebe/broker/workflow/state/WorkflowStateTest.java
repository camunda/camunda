/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.state;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.broker.util.ZeebeStateRule;
import io.zeebe.broker.workflow.model.element.AbstractFlowElement;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WorkflowStateTest {

  @Rule public ZeebeStateRule stateRule = new ZeebeStateRule();

  private WorkflowState workflowState;
  private ZeebeState zeebeState;
  private static final Long FIRST_WORKFLOW_KEY =
      Protocol.encodePartitionId(Protocol.DEPLOYMENT_PARTITION, 1);

  @Before
  public void setUp() {
    zeebeState = stateRule.getZeebeState();
    workflowState = zeebeState.getWorkflowState();
  }

  @Test
  public void shouldGetNextWorkflowVersion() {
    // given

    // when
    final long nextWorkflowVersion = workflowState.getNextWorkflowVersion("foo");

    // then
    assertThat(nextWorkflowVersion).isEqualTo(1L);
  }

  @Test
  public void shouldIncrementWorkflowVersion() {
    // given
    workflowState.getNextWorkflowVersion("foo");

    // when
    final long nextWorkflowVersion = workflowState.getNextWorkflowVersion("foo");

    // then
    assertThat(nextWorkflowVersion).isEqualTo(2L);
  }

  @Test
  public void shouldNotIncrementWorkflowVersionForDifferentProcessId() {
    // given
    workflowState.getNextWorkflowVersion("foo");

    // when
    final long nextWorkflowVersion = workflowState.getNextWorkflowVersion("bar");

    // then
    assertThat(nextWorkflowVersion).isEqualTo(1L);
  }

  @Test
  public void shouldReturnNullOnGetLatest() {
    // given

    // when
    final DeployedWorkflow deployedWorkflow =
        workflowState.getLatestWorkflowVersionByProcessId(wrapString("deployedWorkflow"));

    // then
    assertThat(deployedWorkflow).isNull();
  }

  @Test
  public void shouldReturnNullOnGetWorkflowByKey() {
    // given

    // when
    final DeployedWorkflow deployedWorkflow = workflowState.getWorkflowByKey(0);

    // then
    assertThat(deployedWorkflow).isNull();
  }

  @Test
  public void shouldReturnNullOnGetWorkflowByProcessIdAndVersion() {
    // given

    // when
    final DeployedWorkflow deployedWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("foo"), 0);

    // then
    assertThat(deployedWorkflow).isNull();
  }

  @Test
  public void shouldReturnEmptyListOnGetWorkflows() {
    // given

    // when
    final Collection<DeployedWorkflow> deployedWorkflow = workflowState.getWorkflows();

    // then
    assertThat(deployedWorkflow).isEmpty();
  }

  @Test
  public void shouldReturnEmptyListOnGetWorkflowsByProcessId() {
    // given

    // when
    final Collection<DeployedWorkflow> deployedWorkflow =
        workflowState.getWorkflowsByBpmnProcessId(wrapString("foo"));

    // then
    assertThat(deployedWorkflow).isEmpty();
  }

  @Test
  public void shouldPutDeploymentToState() {
    // given
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(zeebeState);

    // when
    workflowState.putDeployment(1, deploymentRecord);

    // then
    final DeployedWorkflow deployedWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("processId"), 1);

    assertThat(deployedWorkflow).isNotNull();
  }

  @Test
  public void shouldNotOverwritePreviousRecord() {
    // given
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(zeebeState);

    // when
    workflowState.putDeployment(1, deploymentRecord);
    deploymentRecord.workflows().iterator().next().setKey(212).setBpmnProcessId("other");

    // then
    final DeployedWorkflow deployedWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("processId"), 1);

    assertThat(deployedWorkflow.getKey())
        .isNotEqualTo(deploymentRecord.workflows().iterator().next().getKey());
    assertThat(deploymentRecord.workflows().iterator().next().getBpmnProcessId())
        .isEqualTo(BufferUtil.wrapString("other"));
    assertThat(deployedWorkflow.getBpmnProcessId()).isEqualTo(BufferUtil.wrapString("processId"));
  }

  @Test
  public void shouldStoreDifferentWorkflowVersionsOnPutDeployments() {
    // given

    // when
    workflowState.putDeployment(1, creatingDeploymentRecord(zeebeState));
    workflowState.putDeployment(2, creatingDeploymentRecord(zeebeState));

    // then
    final DeployedWorkflow deployedWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("processId"), 1);

    final DeployedWorkflow secondWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("processId"), 2);

    assertThat(deployedWorkflow).isNotNull();
    assertThat(secondWorkflow).isNotNull();

    assertThat(deployedWorkflow.getBpmnProcessId()).isEqualTo(secondWorkflow.getBpmnProcessId());
    assertThat(deployedWorkflow.getResourceName()).isEqualTo(secondWorkflow.getResourceName());
    assertThat(deployedWorkflow.getKey()).isNotEqualTo(secondWorkflow.getKey());

    assertThat(deployedWorkflow.getVersion()).isEqualTo(1);
    assertThat(secondWorkflow.getVersion()).isEqualTo(2);
  }

  @Test
  public void shouldRestartVersionCountOnDifferenProcessId() {
    // given
    workflowState.putDeployment(1, creatingDeploymentRecord(zeebeState));

    // when
    workflowState.putDeployment(2, creatingDeploymentRecord(zeebeState, "otherId"));

    // then
    final DeployedWorkflow deployedWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("processId"), 1);

    final DeployedWorkflow secondWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("otherId"), 1);

    assertThat(deployedWorkflow).isNotNull();
    assertThat(secondWorkflow).isNotNull();

    // getKey's should increase
    assertThat(deployedWorkflow.getKey()).isEqualTo(FIRST_WORKFLOW_KEY);
    assertThat(secondWorkflow.getKey()).isEqualTo(FIRST_WORKFLOW_KEY + 1);

    // but versions should restart
    assertThat(deployedWorkflow.getVersion()).isEqualTo(1);
    assertThat(secondWorkflow.getVersion()).isEqualTo(1);
  }

  @Test
  public void shouldGetLatestDeployedWorkflow() {
    // given
    workflowState.putDeployment(1, creatingDeploymentRecord(zeebeState));
    workflowState.putDeployment(2, creatingDeploymentRecord(zeebeState));

    // when
    final DeployedWorkflow latestWorkflow =
        workflowState.getLatestWorkflowVersionByProcessId(wrapString("processId"));

    // then
    final DeployedWorkflow firstWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("processId"), 1);
    final DeployedWorkflow secondWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("processId"), 2);

    assertThat(latestWorkflow).isNotNull();
    assertThat(firstWorkflow).isNotNull();
    assertThat(secondWorkflow).isNotNull();

    assertThat(latestWorkflow.getBpmnProcessId()).isEqualTo(secondWorkflow.getBpmnProcessId());

    assertThat(firstWorkflow.getKey()).isNotEqualTo(latestWorkflow.getKey());
    assertThat(latestWorkflow.getKey()).isEqualTo(secondWorkflow.getKey());

    assertThat(latestWorkflow.getResourceName()).isEqualTo(secondWorkflow.getResourceName());
    assertThat(latestWorkflow.getResource()).isEqualTo(secondWorkflow.getResource());

    assertThat(firstWorkflow.getVersion()).isEqualTo(1);
    assertThat(latestWorkflow.getVersion()).isEqualTo(2);
    assertThat(secondWorkflow.getVersion()).isEqualTo(2);
  }

  @Test
  public void shouldGetLatestDeployedWorkflowAfterDeploymentWasAdded() {
    // given
    workflowState.putDeployment(1, creatingDeploymentRecord(zeebeState));
    final DeployedWorkflow firstLatest =
        workflowState.getLatestWorkflowVersionByProcessId(wrapString("processId"));

    // when
    workflowState.putDeployment(2, creatingDeploymentRecord(zeebeState));

    // then
    final DeployedWorkflow latestWorkflow =
        workflowState.getLatestWorkflowVersionByProcessId(wrapString("processId"));

    assertThat(firstLatest).isNotNull();
    assertThat(latestWorkflow).isNotNull();

    assertThat(firstLatest.getBpmnProcessId()).isEqualTo(latestWorkflow.getBpmnProcessId());

    assertThat(latestWorkflow.getKey()).isNotEqualTo(firstLatest.getKey());

    assertThat(firstLatest.getResourceName()).isEqualTo(latestWorkflow.getResourceName());

    assertThat(latestWorkflow.getVersion()).isEqualTo(2);
    assertThat(firstLatest.getVersion()).isEqualTo(1);
  }

  @Test
  public void shouldGetExecutableWorkflow() {
    // given
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(zeebeState);
    workflowState.putDeployment(1, deploymentRecord);

    // when
    final DeployedWorkflow deployedWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("processId"), 1);

    // then
    final ExecutableWorkflow workflow = deployedWorkflow.getWorkflow();
    assertThat(workflow).isNotNull();
    final AbstractFlowElement serviceTask = workflow.getElementById(wrapString("test"));
    assertThat(serviceTask).isNotNull();
  }

  @Test
  public void shouldGetExecutableWorkflowByKey() {
    // given
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(zeebeState);
    final long deploymentKey = FIRST_WORKFLOW_KEY;
    workflowState.putDeployment(deploymentKey, deploymentRecord);

    // when
    final long workflowKey = FIRST_WORKFLOW_KEY;
    final DeployedWorkflow deployedWorkflow = workflowState.getWorkflowByKey(workflowKey);

    // then
    final ExecutableWorkflow workflow = deployedWorkflow.getWorkflow();
    assertThat(workflow).isNotNull();
    final AbstractFlowElement serviceTask = workflow.getElementById(wrapString("test"));
    assertThat(serviceTask).isNotNull();
  }

  @Test
  public void shouldGetExecutableWorkflowByLatestWorkflow() {
    // given
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(zeebeState);
    final int deploymentKey = 1;
    workflowState.putDeployment(deploymentKey, deploymentRecord);

    // when
    final DeployedWorkflow deployedWorkflow =
        workflowState.getLatestWorkflowVersionByProcessId(wrapString("processId"));

    // then
    final ExecutableWorkflow workflow = deployedWorkflow.getWorkflow();
    assertThat(workflow).isNotNull();
    final AbstractFlowElement serviceTask = workflow.getElementById(wrapString("test"));
    assertThat(serviceTask).isNotNull();
  }

  @Test
  public void shouldGetAllWorkflows() {
    // given
    workflowState.putDeployment(1, creatingDeploymentRecord(zeebeState));
    workflowState.putDeployment(2, creatingDeploymentRecord(zeebeState));
    workflowState.putDeployment(3, creatingDeploymentRecord(zeebeState, "otherId"));

    // when
    final Collection<DeployedWorkflow> workflows = workflowState.getWorkflows();

    // then
    assertThat(workflows.size()).isEqualTo(3);
    assertThat(workflows)
        .extracting(DeployedWorkflow::getBpmnProcessId)
        .contains(wrapString("processId"), wrapString("otherId"));
    assertThat(workflows).extracting(DeployedWorkflow::getVersion).contains(1, 2, 1);

    assertThat(workflows)
        .extracting(DeployedWorkflow::getKey)
        .containsOnly(FIRST_WORKFLOW_KEY, FIRST_WORKFLOW_KEY + 1, FIRST_WORKFLOW_KEY + 2);
  }

  @Test
  public void shouldGetAllWorkflowsWithProcessId() {
    // given
    workflowState.putDeployment(1, creatingDeploymentRecord(zeebeState));
    workflowState.putDeployment(2, creatingDeploymentRecord(zeebeState));

    // when
    final Collection<DeployedWorkflow> workflows =
        workflowState.getWorkflowsByBpmnProcessId(wrapString("processId"));

    // then
    assertThat(workflows)
        .extracting(DeployedWorkflow::getBpmnProcessId)
        .containsOnly(wrapString("processId"));
    assertThat(workflows).extracting(DeployedWorkflow::getVersion).containsOnly(1, 2);

    assertThat(workflows)
        .extracting(DeployedWorkflow::getKey)
        .containsOnly(FIRST_WORKFLOW_KEY, FIRST_WORKFLOW_KEY + 1);
  }

  @Test
  public void shouldNotGetWorkflowsWithOtherProcessId() {
    // given
    workflowState.putDeployment(1, creatingDeploymentRecord(zeebeState));
    workflowState.putDeployment(2, creatingDeploymentRecord(zeebeState, "otherId"));

    // when
    final Collection<DeployedWorkflow> workflows =
        workflowState.getWorkflowsByBpmnProcessId(wrapString("otherId"));

    // then
    assertThat(workflows.size()).isEqualTo(1);
    assertThat(workflows)
        .extracting(DeployedWorkflow::getBpmnProcessId)
        .containsOnly(wrapString("otherId"));
    assertThat(workflows).extracting(DeployedWorkflow::getVersion).containsOnly(1);

    final long expectedWorkflowKey = Protocol.encodePartitionId(Protocol.DEPLOYMENT_PARTITION, 2);
    assertThat(workflows).extracting(DeployedWorkflow::getKey).containsOnly(expectedWorkflowKey);
  }

  public static DeploymentRecord creatingDeploymentRecord(ZeebeState zeebeState) {
    return creatingDeploymentRecord(zeebeState, "processId");
  }

  public static DeploymentRecord creatingDeploymentRecord(ZeebeState zeebeState, String processId) {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(
                "test",
                task -> {
                  task.zeebeTaskType("type");
                })
            .endEvent()
            .done();

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    final String resourceName = "process.bpmn";
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString(resourceName))
        .setResource(wrapString(Bpmn.convertToString(modelInstance)))
        .setResourceType(ResourceType.BPMN_XML);

    final KeyGenerator keyGenerator = zeebeState.getKeyGenerator();
    final long key = keyGenerator.nextKey();

    final WorkflowState workflowState = zeebeState.getWorkflowState();
    final int version = workflowState.getNextWorkflowVersion(processId);

    deploymentRecord
        .workflows()
        .add()
        .setBpmnProcessId(BufferUtil.wrapString(processId))
        .setVersion(version)
        .setKey(key)
        .setResourceName(resourceName);

    return deploymentRecord;
  }
}
