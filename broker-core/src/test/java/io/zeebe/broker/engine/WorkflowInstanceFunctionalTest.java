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
package io.zeebe.broker.engine;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.exporter.api.record.value.VariableRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceCreationRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.io.File;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.assertj.core.util.Files;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class WorkflowInstanceFunctionalTest {

  private static EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  private static ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);
  private static PartitionTestClient testClient;

  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldCreateWorkflowInstance() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final long workflowKey =
        testClient
            .deployWorkflow(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
            .getKey();
    final DirectBuffer variables = MsgPackUtil.asMsgPack("foo", "bar");

    // when
    final WorkflowInstanceCreationRecord workflowInstance =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey).setVariables(variables));
    final long workflowInstanceKey = workflowInstance.getInstanceKey();

    // then
    final long workflowCompletedPosition =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withElementId(processId)
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst()
            .getPosition();
    final Record<WorkflowInstanceCreationRecordValue> workflowCreated =
        RecordingExporter.workflowInstanceCreationRecords()
            .withIntent(WorkflowInstanceCreationIntent.CREATED)
            .withInstanceKey(workflowInstanceKey)
            .getFirst();
    final Record<WorkflowInstanceRecordValue> workflowActivating =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId(processId)
            .getFirst();
    final List<Record<VariableRecordValue>> variablesRecords =
        RecordingExporter.records()
            .between(workflowActivating.getSourceRecordPosition(), workflowCompletedPosition)
            .variableRecords()
            .collect(Collectors.toList());

    assertThat(workflowActivating.getKey()).isGreaterThan(0).isEqualTo(workflowInstanceKey);
    assertThat(workflowActivating.getSourceRecordPosition())
        .isGreaterThan(0)
        .isEqualTo(workflowCreated.getSourceRecordPosition());
    Assertions.assertThat(workflowActivating.getValue())
        .hasBpmnElementType(BpmnElementType.PROCESS)
        .hasBpmnProcessId(processId)
        .hasElementId(processId)
        .hasWorkflowInstanceKey(workflowInstanceKey);
    assertThat(variablesRecords).hasSize(1);
    Assertions.assertThat(variablesRecords.get(0))
        .hasSourceRecordPosition(workflowActivating.getSourceRecordPosition());
    Assertions.assertThat(variablesRecords.get(0).getValue()).hasName("foo").hasValue("\"bar\"");
  }

  @Test
  public void shouldStartWorkflowInstanceAtNoneStartEvent() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String startId = Strings.newRandomValidBpmnId();
    testClient.deploy(
        Bpmn.createExecutableProcess(processId).startEvent(startId).endEvent().done());

    // when
    final WorkflowInstanceCreationRecord workflowInstance =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId));
    final long workflowInstanceKey = workflowInstance.getInstanceKey();

    // then
    final Record<WorkflowInstanceCreationRecordValue> workflowCreated =
        RecordingExporter.workflowInstanceCreationRecords()
            .withIntent(WorkflowInstanceCreationIntent.CREATED)
            .withInstanceKey(workflowInstanceKey)
            .getFirst();
    final Record<WorkflowInstanceRecordValue> startEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId(startId)
            .getFirst();

    assertThat(startEvent.getKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
    assertThat(startEvent.getPosition()).isGreaterThan(workflowCreated.getPosition());
    Assertions.assertThat(startEvent.getValue())
        .hasBpmnElementType(BpmnElementType.START_EVENT)
        .hasBpmnProcessId(processId)
        .hasElementId(startId)
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldTakeSequenceFlowFromStartEvent() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String sequenceId = Strings.newRandomValidBpmnId();
    testClient.deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .sequenceFlowId(sequenceId)
            .endEvent()
            .done());

    // when
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // then
    final Record<WorkflowInstanceRecordValue> sequenceFlow =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withElementId(sequenceId)
            .getFirst();

    assertThat(sequenceFlow.getKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
    Assertions.assertThat(sequenceFlow.getValue())
        .hasBpmnElementType(BpmnElementType.SEQUENCE_FLOW)
        .hasBpmnProcessId(processId)
        .hasElementId(sequenceId)
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldOccurEndEvent() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String endId = Strings.newRandomValidBpmnId();
    testClient.deploy(Bpmn.createExecutableProcess(processId).startEvent().endEvent(endId).done());

    // when
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // then
    final Record<WorkflowInstanceRecordValue> endEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId(endId)
            .getFirst();

    assertThat(endEvent).isNotNull();
    Assertions.assertThat(endEvent.getValue())
        .hasBpmnElementType(BpmnElementType.END_EVENT)
        .hasBpmnProcessId(processId)
        .hasElementId(endId)
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldActivateServiceTask() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String taskId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(taskId, t -> t.zeebeTaskType("bar"))
            .endEvent()
            .done();

    testClient.deploy(model);

    // when
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // then
    final Record<WorkflowInstanceRecordValue> activityReady =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId(taskId)
            .getFirst();

    final Record<WorkflowInstanceRecordValue> activatedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId(taskId)
            .getFirst();

    assertThat(activatedEvent.getKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
    assertThat(activatedEvent.getSourceRecordPosition()).isEqualTo(activityReady.getPosition());
    Assertions.assertThat(activatedEvent.getValue())
        .hasBpmnElementType(BpmnElementType.SERVICE_TASK)
        .hasBpmnProcessId(processId)
        .hasElementId(taskId)
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldCreateTaskWhenServiceTaskIsActivated() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String taskId = Strings.newRandomValidBpmnId();
    final String taskType = Strings.newRandomValidBpmnId();
    testClient.deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(taskId, t -> t.zeebeTaskType(taskType).zeebeTaskRetries(5))
            .endEvent()
            .done());

    // when
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // then
    final Record<WorkflowInstanceRecordValue> activityActivated =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId(taskId)
            .getFirst();
    final Record<JobRecordValue> createJobCmd =
        RecordingExporter.jobRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(JobIntent.CREATE)
            .withType(taskType)
            .getFirst();

    assertThat(createJobCmd.getKey()).isEqualTo(ExecuteCommandResponseDecoder.keyNullValue());
    assertThat(createJobCmd.getSourceRecordPosition()).isEqualTo(activityActivated.getPosition());
    Assertions.assertThat(createJobCmd.getValue()).hasRetries(5).hasType(taskType);
    Assertions.assertThat(createJobCmd.getValue().getHeaders())
        .hasBpmnProcessId(processId)
        .hasElementId(taskId)
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldCreateJobWithWorkflowInstanceAndCustomHeaders() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String taskId = Strings.newRandomValidBpmnId();
    final String taskType = Strings.newRandomValidBpmnId();
    testClient.deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(
                taskId,
                t -> t.zeebeTaskType(taskType).zeebeTaskHeader("a", "b").zeebeTaskHeader("c", "d"))
            .endEvent()
            .done());

    // when
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // then
    final Record<JobRecordValue> event =
        RecordingExporter.jobRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(JobIntent.CREATE)
            .withType(taskType)
            .getFirst();

    Assertions.assertThat(event.getValue().getHeaders())
        .hasBpmnProcessId(processId)
        .hasElementId(taskId)
        .hasWorkflowInstanceKey(workflowInstanceKey);

    final Map<String, Object> customHeaders = event.getValue().getCustomHeaders();
    assertThat(customHeaders).containsEntry("a", "b").containsEntry("c", "d");
  }

  @Test
  public void shouldCompleteServiceTaskWhenTaskIsCompleted() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String taskId = Strings.newRandomValidBpmnId();
    final String taskType = Strings.newRandomValidBpmnId();
    final BpmnModelInstance definition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(taskId, t -> t.zeebeTaskType(taskType))
            .endEvent()
            .done();

    testClient.deploy(definition);

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // when
    testClient.activateAndCompleteFirstJob(
        taskType, r -> r.getHeaders().getWorkflowInstanceKey() == workflowInstanceKey);

    // then
    final Record<WorkflowInstanceRecordValue> activityActivatedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId(taskId)
            .getFirst();
    final Record<JobRecordValue> jobCompleted =
        testClient.receiveFirstJobEvent(JobIntent.COMPLETED);
    final Record<WorkflowInstanceRecordValue> activityCompleting =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETING)
            .withElementId(taskId)
            .getFirst();
    final Record<WorkflowInstanceRecordValue> activityCompletedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId(taskId)
            .getFirst();

    assertThat(activityCompleting.getSourceRecordPosition()).isEqualTo(jobCompleted.getPosition());
    assertThat(activityCompletedEvent.getKey()).isEqualTo(activityActivatedEvent.getKey());
    assertThat(activityCompletedEvent.getSourceRecordPosition())
        .isEqualTo(activityCompleting.getPosition());
    Assertions.assertThat(activityCompletedEvent.getValue())
        .hasBpmnElementType(BpmnElementType.SERVICE_TASK)
        .hasBpmnProcessId(processId)
        .hasElementId(taskId)
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  // todo(npepinpe): is this a useful test?
  @Test
  public void testWorkflowInstanceStatesWithServiceTask() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String startId = Strings.newRandomValidBpmnId();
    final String taskId = Strings.newRandomValidBpmnId();
    final String endId = Strings.newRandomValidBpmnId();
    final String taskType = Strings.newRandomValidBpmnId();
    final BpmnModelInstance definition =
        Bpmn.createExecutableProcess(processId)
            .startEvent(startId)
            .serviceTask(taskId, t -> t.zeebeTaskType(taskType))
            .endEvent(endId)
            .done();

    testClient.deploy(definition);

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // when
    testClient.activateAndCompleteFirstJob(
        taskType, r -> r.getHeaders().getWorkflowInstanceKey() == workflowInstanceKey);

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowEvents =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(workflowEvents)
        .extracting(Record::getMetadata)
        .extracting(RecordMetadata::getIntent)
        .containsExactly(
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldCreateAndCompleteInstanceOfYamlWorkflow() throws URISyntaxException {
    // given
    final String processId = "yaml-workflow";
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

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // when
    testClient.activateAndCompleteFirstJob(
        "foo", r -> r.getHeaders().getWorkflowInstanceKey() == workflowInstanceKey);
    testClient.activateAndCompleteFirstJob(
        "bar", r -> r.getHeaders().getWorkflowInstanceKey() == workflowInstanceKey);

    // then
    final Record<WorkflowInstanceRecordValue> event =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId("yaml-workflow")
            .getFirst();

    assertThat(event.getKey()).isEqualTo(workflowInstanceKey);
    Assertions.assertThat(event.getValue())
        .hasBpmnElementType(BpmnElementType.PROCESS)
        .hasBpmnProcessId(processId)
        .hasElementId("yaml-workflow")
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }
}
