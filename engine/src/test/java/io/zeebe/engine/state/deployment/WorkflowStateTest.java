/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.state.deployment;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.workflow.deployment.model.element.AbstractFlowElement;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import org.assertj.core.api.Assertions;
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
    Assertions.assertThat(deployedWorkflow).isNull();
  }

  @Test
  public void shouldReturnNullOnGetWorkflowByKey() {
    // given

    // when
    final DeployedWorkflow deployedWorkflow = workflowState.getWorkflowByKey(0);

    // then
    Assertions.assertThat(deployedWorkflow).isNull();
  }

  @Test
  public void shouldReturnNullOnGetWorkflowByProcessIdAndVersion() {
    // given

    // when
    final DeployedWorkflow deployedWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("foo"), 0);

    // then
    Assertions.assertThat(deployedWorkflow).isNull();
  }

  @Test
  public void shouldReturnEmptyListOnGetWorkflows() {
    // given

    // when
    final Collection<DeployedWorkflow> deployedWorkflow = workflowState.getWorkflows();

    // then
    Assertions.assertThat(deployedWorkflow).isEmpty();
  }

  @Test
  public void shouldReturnEmptyListOnGetWorkflowsByProcessId() {
    // given

    // when
    final Collection<DeployedWorkflow> deployedWorkflow =
        workflowState.getWorkflowsByBpmnProcessId(wrapString("foo"));

    // then
    Assertions.assertThat(deployedWorkflow).isEmpty();
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

    Assertions.assertThat(deployedWorkflow).isNotNull();
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

    Assertions.assertThat(deployedWorkflow.getKey())
        .isNotEqualTo(deploymentRecord.workflows().iterator().next().getKey());
    assertThat(deploymentRecord.workflows().iterator().next().getBpmnProcessId())
        .isEqualTo(BufferUtil.wrapString("other"));
    Assertions.assertThat(deployedWorkflow.getBpmnProcessId())
        .isEqualTo(BufferUtil.wrapString("processId"));
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

    Assertions.assertThat(deployedWorkflow).isNotNull();
    Assertions.assertThat(secondWorkflow).isNotNull();

    Assertions.assertThat(deployedWorkflow.getBpmnProcessId())
        .isEqualTo(secondWorkflow.getBpmnProcessId());
    Assertions.assertThat(deployedWorkflow.getResourceName())
        .isEqualTo(secondWorkflow.getResourceName());
    Assertions.assertThat(deployedWorkflow.getKey()).isNotEqualTo(secondWorkflow.getKey());

    Assertions.assertThat(deployedWorkflow.getVersion()).isEqualTo(1);
    Assertions.assertThat(secondWorkflow.getVersion()).isEqualTo(2);
  }

  @Test
  public void shouldRestartVersionCountOnDifferentProcessId() {
    // given
    workflowState.putDeployment(1, creatingDeploymentRecord(zeebeState));

    // when
    workflowState.putDeployment(2, creatingDeploymentRecord(zeebeState, "otherId"));

    // then
    final DeployedWorkflow deployedWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("processId"), 1);

    final DeployedWorkflow secondWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("otherId"), 1);

    Assertions.assertThat(deployedWorkflow).isNotNull();
    Assertions.assertThat(secondWorkflow).isNotNull();

    // getKey's should increase
    Assertions.assertThat(deployedWorkflow.getKey()).isEqualTo(FIRST_WORKFLOW_KEY);
    Assertions.assertThat(secondWorkflow.getKey()).isEqualTo(FIRST_WORKFLOW_KEY + 1);

    // but versions should restart
    Assertions.assertThat(deployedWorkflow.getVersion()).isEqualTo(1);
    Assertions.assertThat(secondWorkflow.getVersion()).isEqualTo(1);
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

    Assertions.assertThat(latestWorkflow).isNotNull();
    Assertions.assertThat(firstWorkflow).isNotNull();
    Assertions.assertThat(secondWorkflow).isNotNull();

    Assertions.assertThat(latestWorkflow.getBpmnProcessId())
        .isEqualTo(secondWorkflow.getBpmnProcessId());

    Assertions.assertThat(firstWorkflow.getKey()).isNotEqualTo(latestWorkflow.getKey());
    Assertions.assertThat(latestWorkflow.getKey()).isEqualTo(secondWorkflow.getKey());

    Assertions.assertThat(latestWorkflow.getResourceName())
        .isEqualTo(secondWorkflow.getResourceName());
    Assertions.assertThat(latestWorkflow.getResource()).isEqualTo(secondWorkflow.getResource());

    Assertions.assertThat(firstWorkflow.getVersion()).isEqualTo(1);
    Assertions.assertThat(latestWorkflow.getVersion()).isEqualTo(2);
    Assertions.assertThat(secondWorkflow.getVersion()).isEqualTo(2);
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

    Assertions.assertThat(firstLatest).isNotNull();
    Assertions.assertThat(latestWorkflow).isNotNull();

    Assertions.assertThat(firstLatest.getBpmnProcessId())
        .isEqualTo(latestWorkflow.getBpmnProcessId());

    Assertions.assertThat(latestWorkflow.getKey()).isNotEqualTo(firstLatest.getKey());

    Assertions.assertThat(firstLatest.getResourceName())
        .isEqualTo(latestWorkflow.getResourceName());

    Assertions.assertThat(latestWorkflow.getVersion()).isEqualTo(2);
    Assertions.assertThat(firstLatest.getVersion()).isEqualTo(1);
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
    Assertions.assertThat(workflow).isNotNull();
    final AbstractFlowElement serviceTask = workflow.getElementById(wrapString("test"));
    Assertions.assertThat(serviceTask).isNotNull();
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
    Assertions.assertThat(workflow).isNotNull();
    final AbstractFlowElement serviceTask = workflow.getElementById(wrapString("test"));
    Assertions.assertThat(serviceTask).isNotNull();
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
    Assertions.assertThat(workflow).isNotNull();
    final AbstractFlowElement serviceTask = workflow.getElementById(wrapString("test"));
    Assertions.assertThat(serviceTask).isNotNull();
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
    Assertions.assertThat(workflows)
        .extracting(DeployedWorkflow::getBpmnProcessId)
        .contains(wrapString("processId"), wrapString("otherId"));
    Assertions.assertThat(workflows).extracting(DeployedWorkflow::getVersion).contains(1, 2, 1);

    Assertions.assertThat(workflows)
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
    Assertions.assertThat(workflows)
        .extracting(DeployedWorkflow::getBpmnProcessId)
        .containsOnly(wrapString("processId"));
    Assertions.assertThat(workflows).extracting(DeployedWorkflow::getVersion).containsOnly(1, 2);

    Assertions.assertThat(workflows)
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
    Assertions.assertThat(workflows)
        .extracting(DeployedWorkflow::getBpmnProcessId)
        .containsOnly(wrapString("otherId"));
    Assertions.assertThat(workflows).extracting(DeployedWorkflow::getVersion).containsOnly(1);

    final long expectedWorkflowKey = Protocol.encodePartitionId(Protocol.DEPLOYMENT_PARTITION, 2);
    Assertions.assertThat(workflows)
        .extracting(DeployedWorkflow::getKey)
        .containsOnly(expectedWorkflowKey);
  }

  @Test
  public void shouldReturnHighestVersionInsteadOfMostRecent() {
    // given
    final String processId = "process";
    workflowState.putDeployment(2, creatingDeploymentRecord(zeebeState, processId, 2));
    workflowState.putDeployment(1, creatingDeploymentRecord(zeebeState, processId, 1));

    // when
    final DeployedWorkflow latestWorkflow =
        workflowState.getLatestWorkflowVersionByProcessId(wrapString(processId));

    // then
    Assertions.assertThat(latestWorkflow.getVersion()).isEqualTo(2);
  }

  public static DeploymentRecord creatingDeploymentRecord(ZeebeState zeebeState) {
    return creatingDeploymentRecord(zeebeState, "processId");
  }

  public static DeploymentRecord creatingDeploymentRecord(ZeebeState zeebeState, String processId) {
    final WorkflowState workflowState = zeebeState.getWorkflowState();
    final int version = workflowState.getNextWorkflowVersion(processId);
    return creatingDeploymentRecord(zeebeState, processId, version);
  }

  public static DeploymentRecord creatingDeploymentRecord(
      ZeebeState zeebeState, String processId, int version) {
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
