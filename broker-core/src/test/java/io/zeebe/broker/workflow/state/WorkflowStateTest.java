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

import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.workflow.deployment.transform.DeploymentTransformer;
import io.zeebe.broker.workflow.model.element.AbstractFlowElement;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import java.util.Collection;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WorkflowStateTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private WorkflowState workflowState;

  @Before
  public void setUp() throws Exception {
    workflowState = new WorkflowState();
    workflowState.open(folder.newFolder("rocksdb"), false);
  }

  @After
  public void tearDown() {
    workflowState.close();
  }

  @Test
  public void shouldGetNextWorkflowKey() {
    // given

    // when
    final long nextWorkflowKey = workflowState.getNextWorkflowKey();

    // then
    assertThat(nextWorkflowKey).isEqualTo(1L);
  }

  @Test
  public void shouldIncrementWorkflowKey() {
    // given
    workflowState.getNextWorkflowKey();

    // when
    final long nextWorkflowKey = workflowState.getNextWorkflowKey();

    // then
    assertThat(nextWorkflowKey).isEqualTo(2L);
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
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(workflowState);

    // when
    workflowState.putDeployment(1, deploymentRecord);

    // then
    final DeployedWorkflow deployedWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("processId"), 1);

    assertThat(deployedWorkflow).isNotNull();
  }

  @Test
  public void shouldStoreDifferentWorkflowVersionsOnPutDeployments() {
    // given

    // when
    workflowState.putDeployment(1, creatingDeploymentRecord(workflowState));
    workflowState.putDeployment(2, creatingDeploymentRecord(workflowState));

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
    workflowState.putDeployment(1, creatingDeploymentRecord(workflowState));

    // when
    workflowState.putDeployment(2, creatingDeploymentRecord(workflowState, "otherId"));

    // then
    final DeployedWorkflow deployedWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("processId"), 1);

    final DeployedWorkflow secondWorkflow =
        workflowState.getWorkflowByProcessIdAndVersion(wrapString("otherId"), 1);

    assertThat(deployedWorkflow).isNotNull();
    assertThat(secondWorkflow).isNotNull();

    // key's should increase
    assertThat(deployedWorkflow.getKey()).isEqualTo(1L);
    assertThat(secondWorkflow.getKey()).isEqualTo(2L);

    // but versions should restart
    assertThat(deployedWorkflow.getVersion()).isEqualTo(1);
    assertThat(secondWorkflow.getVersion()).isEqualTo(1);
  }

  @Test
  public void shouldGetLatestDeployedWorkflow() {
    // given
    workflowState.putDeployment(1, creatingDeploymentRecord(workflowState));
    workflowState.putDeployment(2, creatingDeploymentRecord(workflowState));

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
    workflowState.putDeployment(1, creatingDeploymentRecord(workflowState));
    final DeployedWorkflow firstLatest =
        workflowState.getLatestWorkflowVersionByProcessId(wrapString("processId"));

    // when
    workflowState.putDeployment(2, creatingDeploymentRecord(workflowState));

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
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(workflowState);
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
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(workflowState);
    final int deploymentKey = 1;
    workflowState.putDeployment(deploymentKey, deploymentRecord);

    // when
    final int workflowKey = 1;
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
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(workflowState);
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
    workflowState.putDeployment(1, creatingDeploymentRecord(workflowState));
    workflowState.putDeployment(2, creatingDeploymentRecord(workflowState));
    workflowState.putDeployment(3, creatingDeploymentRecord(workflowState, "otherId"));

    // when
    final Collection<DeployedWorkflow> workflows = workflowState.getWorkflows();

    // then
    assertThat(workflows.size()).isEqualTo(3);
    assertThat(workflows)
        .extracting(DeployedWorkflow::getBpmnProcessId)
        .contains(wrapString("processId"), wrapString("otherId"));
    assertThat(workflows).extracting(DeployedWorkflow::getVersion).contains(1, 2, 1);
    assertThat(workflows).extracting(DeployedWorkflow::getKey).containsOnly(1L, 2L, 3L);
  }

  @Test
  public void shouldGetAllWorkflowsWithProcessId() {
    // given
    workflowState.putDeployment(1, creatingDeploymentRecord(workflowState));
    workflowState.putDeployment(2, creatingDeploymentRecord(workflowState));

    // when
    final Collection<DeployedWorkflow> workflows =
        workflowState.getWorkflowsByBpmnProcessId(wrapString("processId"));

    // then
    assertThat(workflows)
        .extracting(DeployedWorkflow::getBpmnProcessId)
        .containsOnly(wrapString("processId"));
    assertThat(workflows).extracting(DeployedWorkflow::getVersion).containsOnly(1, 2);
    assertThat(workflows).extracting(DeployedWorkflow::getKey).containsOnly(1L, 2L);
  }

  @Test
  public void shouldNotGetWorkflowsWithOtherProcessId() {
    // given
    workflowState.putDeployment(1, creatingDeploymentRecord(workflowState));
    workflowState.putDeployment(2, creatingDeploymentRecord(workflowState, "otherId"));

    // when
    final Collection<DeployedWorkflow> workflows =
        workflowState.getWorkflowsByBpmnProcessId(wrapString("otherId"));

    // then
    assertThat(workflows.size()).isEqualTo(1);
    assertThat(workflows)
        .extracting(DeployedWorkflow::getBpmnProcessId)
        .containsOnly(wrapString("otherId"));
    assertThat(workflows).extracting(DeployedWorkflow::getVersion).containsOnly(1);
    assertThat(workflows).extracting(DeployedWorkflow::getKey).containsOnly(2L);
  }

  @Test
  public void shouldPutAndFindWorkflowSubscription() {
    // given
    final WorkflowSubscription workflowSubscription =
        new WorkflowSubscription("message", "correlation", 1, 2, 100);
    workflowSubscription.setOpen(true);

    // when
    workflowState.put(workflowSubscription);

    // then
    final WorkflowInstanceSubscriptionRecord workflowInstanceSubscriptionRecord =
        new WorkflowInstanceSubscriptionRecord();
    workflowInstanceSubscriptionRecord.setMessageName(wrapString("message"));
    workflowInstanceSubscriptionRecord.setWorkflowInstanceKey(1);
    workflowInstanceSubscriptionRecord.setActivityInstanceKey(2);

    final WorkflowSubscription subscription =
        workflowState.findSubscription(workflowInstanceSubscriptionRecord);

    assertThat(subscription).isNotNull();

    assertThat(subscription.getMessageName()).isEqualTo(wrapString("message"));
    assertThat(subscription.getCorrelationKey()).isEqualTo(wrapString("correlation"));
    assertThat(subscription.getWorkflowInstanceKey()).isEqualTo(1);
    assertThat(subscription.getActivityInstanceKey()).isEqualTo(2);
    assertThat(subscription.getCommandSentTime()).isEqualTo(100);
    assertThat(subscription.isNotOpen()).isFalse();
  }

  @Test
  public void shouldFindOneWorkflowSubscription() {
    // given
    workflowState.put(new WorkflowSubscription("message", "correlation", 1, 2, 100));
    workflowState.put(new WorkflowSubscription("msg1", "correlation", 2, 3, 100));
    workflowState.put(new WorkflowSubscription("msg2", "correlation", 3, 4, 100));

    // when
    final WorkflowInstanceSubscriptionRecord workflowInstanceSubscriptionRecord =
        new WorkflowInstanceSubscriptionRecord();
    workflowInstanceSubscriptionRecord.setMessageName(wrapString("message"));
    workflowInstanceSubscriptionRecord.setWorkflowInstanceKey(1);
    workflowInstanceSubscriptionRecord.setActivityInstanceKey(2);

    final WorkflowSubscription subscription =
        workflowState.findSubscription(workflowInstanceSubscriptionRecord);

    // then
    assertThat(subscription).isNotNull();

    assertThat(subscription.getMessageName()).isEqualTo(wrapString("message"));
    assertThat(subscription.getCorrelationKey()).isEqualTo(wrapString("correlation"));
    assertThat(subscription.getWorkflowInstanceKey()).isEqualTo(1);
    assertThat(subscription.getActivityInstanceKey()).isEqualTo(2);
    assertThat(subscription.getCommandSentTime()).isEqualTo(100);
  }

  @Test
  public void shouldNotFindWorkflowSubscriptionBefore() {
    // given
    final WorkflowSubscription workflowSubscription =
        new WorkflowSubscription("message", "correlation", 1, 2, 100);
    workflowState.put(workflowSubscription);

    // when
    final List<WorkflowSubscription> workflowSubscriptions =
        workflowState.findSubscriptionsBefore(50L);

    // then
    assertThat(workflowSubscriptions).isEmpty();
  }

  @Test
  public void shouldFindWorkflowSubscriptionBefore() {
    // given
    workflowState.put(new WorkflowSubscription("message", "correlation", 1, 2, 100));
    workflowState.put(new WorkflowSubscription("msg", "correlation", 3, 4, 352));

    // when
    final List<WorkflowSubscription> workflowSubscriptions =
        workflowState.findSubscriptionsBefore(101L);

    // then
    assertThat(workflowSubscriptions).hasSize(1);

    assertThat(workflowSubscriptions)
        .extracting(w -> w.getMessageName())
        .containsExactly(wrapString("message"));
    assertThat(workflowSubscriptions)
        .extracting(w -> w.getWorkflowInstanceKey())
        .containsExactly(1L);
    assertThat(workflowSubscriptions)
        .extracting(w -> w.getActivityInstanceKey())
        .containsExactly(2L);
    assertThat(workflowSubscriptions).extracting(w -> w.getCommandSentTime()).containsExactly(100L);
  }

  @Test
  public void shouldFindWorkflowSubscriptionBeforeInOrder() {
    // given
    // 100 = 00000000 00000000 00000000 01100100 (BE)
    // 352 = 00000000 00000000 00000001 01100000 (BE)
    workflowState.put(new WorkflowSubscription("message", "correlation", 1, 2, 100));
    workflowState.put(new WorkflowSubscription("msg", "correlation", 3, 4, 352));

    // when
    final List<WorkflowSubscription> workflowSubscriptions =
        workflowState.findSubscriptionsBefore(400L);

    // then
    assertThat(workflowSubscriptions).hasSize(2);
    assertThat(workflowSubscriptions)
        .extracting(s -> s.getWorkflowInstanceKey())
        .containsExactly(1L, 3L);
  }

  @Test
  public void shouldFindAllWorkflowSubscriptionBefore() {
    // given
    workflowState.put(new WorkflowSubscription("message", "correlation", 1, 2, 100));
    workflowState.put(new WorkflowSubscription("msg", "correlation", 3, 4, 150));

    // when
    final List<WorkflowSubscription> workflowSubscriptions =
        workflowState.findSubscriptionsBefore(151L);

    // then
    assertThat(workflowSubscriptions).hasSize(2);

    assertThat(workflowSubscriptions)
        .extracting(w -> w.getMessageName())
        .containsExactlyInAnyOrder(wrapString("message"), wrapString("msg"));
    assertThat(workflowSubscriptions)
        .extracting(w -> w.getWorkflowInstanceKey())
        .containsExactlyInAnyOrder(1L, 3L);
    assertThat(workflowSubscriptions)
        .extracting(w -> w.getActivityInstanceKey())
        .containsExactlyInAnyOrder(2L, 4L);
    assertThat(workflowSubscriptions)
        .extracting(w -> w.getCommandSentTime())
        .containsExactlyInAnyOrder(100L, 150L);
  }

  @Test
  public void shouldRemoveWorkflowSubscription() {
    // given
    final WorkflowSubscription workflowSubscription =
        new WorkflowSubscription("message", "correlation", 1, 2, 100);
    workflowState.put(workflowSubscription);

    // when
    workflowState.remove(workflowSubscription);

    // then
    final WorkflowInstanceSubscriptionRecord workflowInstanceSubscriptionRecord =
        new WorkflowInstanceSubscriptionRecord();
    workflowInstanceSubscriptionRecord.setMessageName(wrapString("message"));
    workflowInstanceSubscriptionRecord.setWorkflowInstanceKey(1);
    workflowInstanceSubscriptionRecord.setActivityInstanceKey(2);

    final WorkflowSubscription subscription =
        workflowState.findSubscription(workflowInstanceSubscriptionRecord);

    assertThat(subscription).isNull();

    // and
    final List<WorkflowSubscription> workflowSubscriptions =
        workflowState.findSubscriptionsBefore(150L);
    assertThat(workflowSubscriptions).isEmpty();
  }

  @Test
  public void shouldRemoveWorkflowSubscriptionWithRecord() {
    // given
    final WorkflowSubscription workflowSubscription =
        new WorkflowSubscription("message", "correlation", 1, 2, 100);
    workflowState.put(workflowSubscription);

    // when
    final WorkflowInstanceSubscriptionRecord workflowInstanceSubscriptionRecord =
        new WorkflowInstanceSubscriptionRecord();
    workflowInstanceSubscriptionRecord.setMessageName(wrapString("message"));
    workflowInstanceSubscriptionRecord.setWorkflowInstanceKey(1);
    workflowInstanceSubscriptionRecord.setActivityInstanceKey(2);
    workflowState.remove(workflowInstanceSubscriptionRecord);

    // then
    final WorkflowSubscription subscription =
        workflowState.findSubscription(workflowInstanceSubscriptionRecord);

    assertThat(subscription).isNull();

    // and
    final List<WorkflowSubscription> workflowSubscriptions =
        workflowState.findSubscriptionsBefore(150L);
    assertThat(workflowSubscriptions).isEmpty();
  }

  public static DeploymentRecord creatingDeploymentRecord(WorkflowState workflowState) {
    return creatingDeploymentRecord(workflowState, "processId");
  }

  public static DeploymentRecord creatingDeploymentRecord(
      WorkflowState workflowState, String processId) {
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
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString("process.bpmn"))
        .setResource(wrapString(Bpmn.convertToString(modelInstance)))
        .setResourceType(ResourceType.BPMN_XML);

    final DeploymentTransformer deploymentTransformer = new DeploymentTransformer(workflowState);

    deploymentTransformer.transform(deploymentRecord);
    return deploymentRecord;
  }
}
