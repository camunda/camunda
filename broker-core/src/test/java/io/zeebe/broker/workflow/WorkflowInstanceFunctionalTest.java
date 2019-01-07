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
package io.zeebe.broker.workflow;

import static io.zeebe.broker.workflow.JobAssert.assertJobHeaders;
import static io.zeebe.broker.workflow.JobAssert.assertJobRecord;
import static io.zeebe.broker.workflow.WorkflowAssert.assertWorkflowInstanceRecord;
import static io.zeebe.broker.workflow.gateway.ParallelGatewayStreamProcessorTest.PROCESS_ID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.JobRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
import java.io.File;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.util.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class WorkflowInstanceFunctionalTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldStartWorkflowInstanceAtNoneStartEvent() {
    // given
    testClient.deploy(Bpmn.createExecutableProcess(PROCESS_ID).startEvent("foo").endEvent().done());

    // when
    final ExecuteCommandResponse response =
        testClient.createWorkflowInstanceWithResponse(PROCESS_ID);

    // then
    final Record<WorkflowInstanceRecordValue> workflowCreateCmd =
        testClient.receiveFirstWorkflowInstanceCommand(WorkflowInstanceIntent.CREATE);
    final Record<WorkflowInstanceRecordValue> startEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.EVENT_TRIGGERED);
    final long workflowInstanceKey = response.getKey();

    assertThat(startEvent.getKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
    assertThat(startEvent.getPosition()).isGreaterThan(workflowCreateCmd.getPosition());
    assertWorkflowInstanceRecord(workflowInstanceKey, "foo", startEvent);
  }

  @Test
  public void shouldTakeSequenceFlowFromStartEvent() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .sequenceFlowId("foo")
            .endEvent()
            .done());

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final Record<WorkflowInstanceRecordValue> sequenceFlow =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN);

    assertThat(sequenceFlow.getKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
    assertWorkflowInstanceRecord(workflowInstanceKey, "foo", sequenceFlow);
  }

  @Test
  public void shouldOccureEndEvent() {
    // given
    testClient.deploy(Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent("foo").done());

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID);

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .limitToWorkflowInstanceCompleted()
                .withElementId("foo"))
        .extracting(r -> r.getMetadata().getIntent())
        .containsExactly(
            WorkflowInstanceIntent.EVENT_ACTIVATING, WorkflowInstanceIntent.EVENT_ACTIVATED);

    assertWorkflowInstanceRecord(
        workflowInstanceKey,
        "foo",
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.EVENT_ACTIVATED));
  }

  @Test
  public void shouldActivateServiceTask() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("foo", t -> t.zeebeTaskType("bar"))
            .endEvent()
            .done();

    testClient.deploy(model);

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final Record<WorkflowInstanceRecordValue> activityReady =
        testClient.receiveElementInState("foo", WorkflowInstanceIntent.ELEMENT_READY);
    final Record<WorkflowInstanceRecordValue> activatedEvent =
        testClient.receiveElementInState("foo", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    assertThat(activatedEvent.getKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
    assertThat(activatedEvent.getSourceRecordPosition()).isEqualTo(activityReady.getPosition());
    assertWorkflowInstanceRecord(workflowInstanceKey, "foo", activatedEvent);
  }

  @Test
  public void shouldCreateTaskWhenServiceTaskIsActivated() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("foo", t -> t.zeebeTaskType("bar").zeebeTaskRetries(5))
            .endEvent()
            .done());

    // when
    testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final Record<WorkflowInstanceRecordValue> activityActivated =
        testClient.receiveElementInState("foo", WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    final Record<JobRecordValue> createJobCmd = testClient.receiveFirstJobCommand(JobIntent.CREATE);

    assertThat(createJobCmd.getKey()).isEqualTo(ExecuteCommandResponseDecoder.keyNullValue());
    assertThat(createJobCmd.getSourceRecordPosition()).isEqualTo(activityActivated.getPosition());
    assertJobRecord(createJobCmd);
  }

  @Test
  public void shouldCreateJobWithWorkflowInstanceAndCustomHeaders() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "foo",
                t -> t.zeebeTaskType("bar").zeebeTaskHeader("a", "b").zeebeTaskHeader("c", "d"))
            .endEvent()
            .done());

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final Record<JobRecordValue> event = testClient.receiveFirstJobCommand(JobIntent.CREATE);
    assertJobHeaders(workflowInstanceKey, "foo", event);

    final Map<String, Object> customHeaders = event.getValue().getCustomHeaders();
    assertThat(customHeaders).containsEntry("a", "b").containsEntry("c", "d");
  }

  @Test
  public void shouldCompleteServiceTaskWhenTaskIsCompleted() {
    // given
    final BpmnModelInstance definition =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("foo", t -> t.zeebeTaskType("bar"))
            .endEvent()
            .done();

    testClient.deploy(definition);

    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("bar");

    // then
    final Record<WorkflowInstanceRecordValue> activityActivatedEvent =
        testClient.receiveElementInState("foo", WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    final Record<JobRecordValue> jobCompleted =
        testClient.receiveFirstJobEvent(JobIntent.COMPLETED);
    final Record<WorkflowInstanceRecordValue> activityCompleting =
        testClient.receiveElementInState("foo", WorkflowInstanceIntent.ELEMENT_COMPLETING);
    final Record<WorkflowInstanceRecordValue> activityCompletedEvent =
        testClient.receiveElementInState("foo", WorkflowInstanceIntent.ELEMENT_COMPLETED);

    assertThat(activityCompleting.getSourceRecordPosition()).isEqualTo(jobCompleted.getPosition());
    assertThat(activityCompletedEvent.getKey()).isEqualTo(activityActivatedEvent.getKey());
    assertThat(activityCompletedEvent.getSourceRecordPosition())
        .isEqualTo(activityCompleting.getPosition());
    assertWorkflowInstanceRecord(workflowInstanceKey, "foo", activityCompletedEvent);
  }

  @Test
  public void testWorkflowInstanceStatesWithServiceTask() {
    // given
    final BpmnModelInstance definition =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("a")
            .serviceTask("b", t -> t.zeebeTaskType("foo"))
            .endEvent("c")
            .done();

    testClient.deploy(definition);

    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("foo");

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowEvents =
        testClient
            .receiveWorkflowInstances()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(workflowEvents)
        .extracting(Record::getMetadata)
        .extracting(e -> e.getIntent())
        .containsExactly(
            WorkflowInstanceIntent.CREATE,
            WorkflowInstanceIntent.ELEMENT_READY,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.EVENT_TRIGGERING,
            WorkflowInstanceIntent.EVENT_TRIGGERED,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            WorkflowInstanceIntent.ELEMENT_READY,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            WorkflowInstanceIntent.EVENT_ACTIVATING,
            WorkflowInstanceIntent.EVENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldCreateAndCompleteInstanceOfYamlWorkflow() throws URISyntaxException {
    // given
    final File yamlFile =
        new File(getClass().getResource("/workflows/simple-workflow.yaml").toURI());
    final String yamlWorkflow = Files.contentOf(yamlFile, UTF_8);

    final Map<String, Object> deploymentResource = new HashMap<>();
    deploymentResource.put("resource", yamlWorkflow.getBytes(UTF_8));
    deploymentResource.put("resourceType", ResourceType.YAML_WORKFLOW);
    deploymentResource.put("resourceName", "simple-workflow.yaml");

    final ExecuteCommandResponse deploymentResp =
        apiRule
            .createCmdRequest()
            .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
            .command()
            .put("resources", Collections.singletonList(deploymentResource))
            .done()
            .sendAndAwait();

    testClient.receiveFirstDeploymentEvent(DeploymentIntent.CREATED, deploymentResp.getKey());

    final long workflowInstanceKey = testClient.createWorkflowInstance("yaml-workflow");

    // when
    testClient.completeJobOfType("foo");
    testClient.completeJobOfType("bar");

    // then
    final Record<WorkflowInstanceRecordValue> event =
        testClient.receiveElementInState("yaml-workflow", WorkflowInstanceIntent.ELEMENT_COMPLETED);

    assertThat(event.getKey()).isEqualTo(workflowInstanceKey);
    assertWorkflowInstanceRecord("yaml-workflow", 1, workflowInstanceKey, "yaml-workflow", event);
  }

  /**
   * Scenario: When system partition stream processor is reprocessing after restart, it may not be
   * able to return a workflow that it already returned successfully before restart.
   */
  @Test
  public void shouldReprocessWorkflowInstanceRecordsWhenWorkflowIsTemporarilyUnavailable() {
    // given
    // make a couple of deployments to ensure that the deployment stream processor will need some
    // time for reprocessing
    final int numDeployments = 25;
    for (int i = 0; i < numDeployments; i++) {
      testClient.deploy(
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent()
              .serviceTask("foo", t -> t.zeebeTaskType("bar"))
              .endEvent()
              .done());
    }

    testClient.createWorkflowInstance(PROCESS_ID);

    brokerRule.stopBroker();
    brokerRule.purgeSnapshots();

    // when
    brokerRule.startBroker();

    // then I can still start workflow instance (i.e. stream processor did not crash
    final long newWorkflowInstancekey = testClient.createWorkflowInstance(PROCESS_ID);
    assertThat(newWorkflowInstancekey).isGreaterThan(0);
  }
}
