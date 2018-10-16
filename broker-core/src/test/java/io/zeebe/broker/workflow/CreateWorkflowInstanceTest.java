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

import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setPartitionCount;
import static io.zeebe.broker.test.MsgPackConstants.MSGPACK_PAYLOAD;
import static io.zeebe.msgpack.spec.MsgPackHelper.EMTPY_OBJECT;
import static io.zeebe.msgpack.spec.MsgPackHelper.NIL;
import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_ACTIVITY_ID;
import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_BPMN_PROCESS_ID;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.CREATE;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.CREATED;
import static io.zeebe.test.broker.protocol.clientapi.TestPartitionClient.PROP_WORKFLOW_INSTANCE_KEY;
import static io.zeebe.test.broker.protocol.clientapi.TestPartitionClient.PROP_WORKFLOW_KEY;
import static io.zeebe.test.broker.protocol.clientapi.TestPartitionClient.PROP_WORKFLOW_PAYLOAD;
import static io.zeebe.test.broker.protocol.clientapi.TestPartitionClient.PROP_WORKFLOW_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestPartitionClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.util.StreamUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CreateWorkflowInstanceTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(setPartitionCount(3));

  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);
  private TestPartitionClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partition();
  }

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Test
  public void shouldRejectWorkflowInstanceCreation() {
    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
            .command()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .done()
            .sendAndAwait();

    // then
    final SubscribedRecord createWorkflowCommand =
        testClient.receiveFirstWorkflowInstanceCommand(CREATE);

    assertThat(resp.key()).isEqualTo(ExecuteCommandResponseDecoder.keyNullValue());
    assertThat(resp.sourceRecordPosition()).isEqualTo(createWorkflowCommand.position());
    assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
    assertThat(resp.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(resp.rejectionType()).isEqualTo(RejectionType.BAD_VALUE);
    assertThat(resp.rejectionReason()).isEqualTo("Workflow is not deployed");
    assertThat(resp.getValue()).containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process");
  }

  @Test
  public void shouldCreateWorkflowInstanceByBpmnProcessId() {
    // given
    testClient.deploy(Bpmn.createExecutableProcess("process").startEvent().endEvent().done());

    // when
    // then
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
            .command()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .done()
            .sendAndAwait();

    // then
    final SubscribedRecord createWorkflowCommand =
        testClient.receiveFirstWorkflowInstanceCommand(CREATE);

    assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
    assertThat(resp.sourceRecordPosition()).isEqualTo(createWorkflowCommand.position());
    assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
    assertThat(resp.intent()).isEqualTo(CREATED);
    assertThat(resp.getValue())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_VERSION, 1L)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, resp.key());
  }

  @Test
  public void shouldCreateWorkflowInstanceByBpmnProcessIdAndLatestVersion() {
    // given
    testClient.deployWithResponse(
        Bpmn.createExecutableProcess("process").startEvent("bar").endEvent().done());

    final ExecuteCommandResponse deployment2 =
        testClient.deployWithResponse(
            Bpmn.createExecutableProcess("process").startEvent("bar").endEvent().done());

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
            .command()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .put(PROP_WORKFLOW_VERSION, -1)
            .done()
            .sendAndAwait();

    // then
    final long workflowKey = extractWorkflowKey(deployment2);

    final SubscribedRecord event =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.START_EVENT_OCCURRED)
            .getFirst();

    assertThat(event.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, resp.key())
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "bar")
        .containsEntry(PROP_WORKFLOW_VERSION, 2L)
        .containsEntry(PROP_WORKFLOW_KEY, workflowKey);
  }

  @Test
  public void shouldCreateWorkflowInstanceByBpmnProcessIdAndPreviosuVersion() {
    // given
    final ExecuteCommandResponse deployment1 =
        testClient.deployWithResponse(
            Bpmn.createExecutableProcess("process").startEvent("foo").endEvent().done());

    testClient.deployWithResponse(
        Bpmn.createExecutableProcess("process").startEvent("bar").endEvent().done());

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
            .command()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .put(PROP_WORKFLOW_VERSION, 1)
            .done()
            .sendAndAwait();

    // then
    final long workflowKey = extractWorkflowKey(deployment1);

    final SubscribedRecord event =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.START_EVENT_OCCURRED)
            .getFirst();

    assertThat(event.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, resp.key())
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo")
        .containsEntry(PROP_WORKFLOW_VERSION, 1L)
        .containsEntry(PROP_WORKFLOW_KEY, workflowKey);
  }

  @Test
  public void shouldCreateWorkflowInstanceByWorkflowKeyAndLatestVersion() {
    // given
    testClient.deploy(Bpmn.createExecutableProcess("process").startEvent("foo").endEvent().done());

    final ExecuteCommandResponse depl =
        testClient.deployWithResponse(
            Bpmn.createExecutableProcess("process").startEvent("bar").endEvent().done());

    final long workflowKey = extractWorkflowKey(depl);

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
            .command()
            .put(PROP_WORKFLOW_KEY, workflowKey)
            .done()
            .sendAndAwait();

    // then
    assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
    assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
    assertThat(resp.intent()).isEqualTo(CREATED);
    assertThat(resp.getValue())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_VERSION, 2L)
        .containsEntry(PROP_WORKFLOW_KEY, workflowKey)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, resp.key());
  }

  @Test
  public void shouldCreateWorkflowInstanceByWorkflowKeyAndPreviousVersion() {
    // given
    final ExecuteCommandResponse depl =
        testClient.deployWithResponse(
            Bpmn.createExecutableProcess("process").startEvent().endEvent().done());

    testClient.deploy(Bpmn.createExecutableProcess("process").startEvent().endEvent().done());

    final long workflowKey = extractWorkflowKey(depl);

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
            .command()
            .put(PROP_WORKFLOW_KEY, workflowKey)
            .done()
            .sendAndAwait();

    // then
    assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
    assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
    assertThat(resp.intent()).isEqualTo(CREATED);
    assertThat(resp.getValue())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_VERSION, 1L)
        .containsEntry(PROP_WORKFLOW_KEY, workflowKey)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, resp.key());
  }

  @Test
  public void shouldCreateWorkflowInstanceWithPayload() {
    // given
    testClient.deploy(Bpmn.createExecutableProcess("process").startEvent().endEvent().done());

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
            .command()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .put(PROP_WORKFLOW_PAYLOAD, MSGPACK_PAYLOAD)
            .done()
            .sendAndAwait();

    // then

    final SubscribedRecord createWorkflowCommand =
        testClient.receiveFirstWorkflowInstanceCommand(CREATE);

    assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
    assertThat(resp.sourceRecordPosition()).isEqualTo(createWorkflowCommand.position());
    assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
    assertThat(resp.intent()).isEqualTo(CREATED);
    assertThat(resp.getValue())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, resp.key())
        .containsEntry(PROP_WORKFLOW_VERSION, 1L)
        .containsEntry(PROP_WORKFLOW_PAYLOAD, MSGPACK_PAYLOAD);
  }

  @Test
  public void shouldCreateWorkflowInstanceWithNilPayload() {
    // given
    testClient.deploy(Bpmn.createExecutableProcess("process").startEvent().endEvent().done());

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
            .command()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .put(PROP_WORKFLOW_PAYLOAD, NIL)
            .done()
            .sendAndAwait();

    // then
    assertThat(resp.getValue()).containsEntry(PROP_WORKFLOW_PAYLOAD, EMTPY_OBJECT);
  }

  @Test
  public void shouldCreateWorkflowInstanceWithZeroLengthPayload() {
    // given
    testClient.deploy(Bpmn.createExecutableProcess("process").startEvent().endEvent().done());

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
            .command()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .put(PROP_WORKFLOW_PAYLOAD, new byte[0])
            .done()
            .sendAndAwait();

    // then
    assertThat(resp.getValue()).containsEntry(PROP_WORKFLOW_PAYLOAD, EMTPY_OBJECT);
  }

  @Test
  public void shouldCreateWorkflowInstanceWithNoPayload() {
    // given
    testClient.deploy(Bpmn.createExecutableProcess("process").startEvent().endEvent().done());

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
            .command()
            .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .done()
            .sendAndAwait();

    // then
    assertThat(resp.getValue()).containsEntry(PROP_WORKFLOW_PAYLOAD, EMTPY_OBJECT);
  }

  @Test
  public void shouldThrowExceptionOnCreationWithInvalidPayload() throws Exception {
    // given
    testClient.deploy(Bpmn.createExecutableProcess("process").startEvent().endEvent().done());

    // when
    final byte[] invalidPayload = MsgPackUtil.asMsgPack("'foo'");

    final Throwable throwable =
        catchThrowable(
            () ->
                apiRule
                    .createCmdRequest()
                    .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
                    .command()
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
                    .put(PROP_WORKFLOW_PAYLOAD, invalidPayload)
                    .done()
                    .sendAndAwait());

    // then
    assertThat(throwable).isInstanceOf(RuntimeException.class);
    assertThat(throwable.getMessage()).contains("Could not read property 'payload'.");
    assertThat(throwable.getMessage())
        .contains("Document has invalid format. On root level an object is only allowed.");
  }

  @Test
  public void shouldCreateMultipleWorkflowInstancesForDifferentBpmnProcessIds() {
    // given
    testClient.deploy(Bpmn.createExecutableProcess("foo").startEvent().endEvent().done());

    testClient.deploy(Bpmn.createExecutableProcess("baaaar").startEvent().endEvent().done());

    // when
    final long workflowInstanceKeyFoo = testClient.createWorkflowInstance("foo");
    final long workflowInstanceKeyBaaaar = testClient.createWorkflowInstance("baaaar");

    // then
    final List<SubscribedRecord> workflowInstanceEvents =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(CREATED)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(workflowInstanceEvents.get(0).value())
        .containsEntry("bpmnProcessId", "foo")
        .containsEntry("workflowInstanceKey", workflowInstanceKeyFoo);

    assertThat(workflowInstanceEvents.get(1).value())
        .containsEntry("bpmnProcessId", "baaaar")
        .containsEntry("workflowInstanceKey", workflowInstanceKeyBaaaar);
  }

  @Test
  public void shouldCreateMultipleWorkflowInstancesForDifferentVersionsOnForceRefresh() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                task ->
                    task.zeebeTaskType("test").zeebeTaskRetries(3).zeebeTaskHeader("foo", "bar"))
            .endEvent("end")
            .done();

    testClient.deploy(workflow);

    final long workflowInstance1 = testClient.createWorkflowInstance("process");

    // when
    testClient.deploy(workflow);

    // -2 == force refresh
    final ExecuteCommandResponse resp =
        testClient.createWorkflowInstanceWithResponse("process", -2);

    // then
    final List<SubscribedRecord> workflowInstanceEvents =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .filter(r -> "task".equals(r.value().get("activityId")))
            .limit(2)
            .collect(Collectors.toList());

    assertThat(workflowInstanceEvents.get(0).value())
        .containsEntry("workflowInstanceKey", workflowInstance1)
        .containsEntry("version", 1L);

    assertThat(workflowInstanceEvents.get(1).value())
        .containsEntry("workflowInstanceKey", resp.key())
        .containsEntry("version", 2L);

    final long createdTasks =
        testClient.receiveEvents().ofTypeJob().withIntent(JobIntent.CREATED).limit(2).count();
    assertThat(createdTasks).isEqualTo(2);
  }

  @Test
  public void shouldCreateInstanceOfYamlWorkflow() throws Exception {
    // given
    final InputStream resourceAsStream =
        getClass().getResourceAsStream("/workflows/simple-workflow.yaml");

    apiRule
        .partition()
        .deployWithResponse(
            StreamUtil.read(resourceAsStream),
            ResourceType.YAML_WORKFLOW.name(),
            "simple-workflow.yaml");

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance("yaml-workflow");

    // then
    final SubscribedRecord workflowInstanceEvent =
        testClient.receiveEvents().ofTypeWorkflowInstance().withIntent(CREATED).getFirst();

    assertThat(workflowInstanceEvent.value())
        .containsEntry("bpmnProcessId", "yaml-workflow")
        .containsEntry("workflowInstanceKey", workflowInstanceKey);
  }

  @Test
  public void shouldCreateWorkflowInstanceOnAllPartitions() {
    // given
    final int partitions = 3;

    final List<Integer> partitionIds = apiRule.getPartitionIds();

    final BpmnModelInstance definition =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

    // when
    final long deploymentKey = apiRule.partition().deploy(definition);

    // then
    final List<Long> workflowInstanceKeys = new ArrayList<>();
    partitionIds.forEach(
        partitionId -> {
          apiRule
              .partition(partitionId)
              .receiveFirstDeploymentEvent(DeploymentIntent.CREATED, deploymentKey);
          final long workflowInstanceKey =
              apiRule.partition(partitionId).createWorkflowInstance("process");

          workflowInstanceKeys.add(workflowInstanceKey);
        });

    assertThat(workflowInstanceKeys).hasSize(partitions).allMatch(k -> k > 0);
  }

  @Test
  public void shouldCreateWorkflowInstanceOfCollaboration() throws IOException {
    final InputStream resourceAsStream =
        getClass().getResourceAsStream("/workflows/collaboration.bpmn");

    apiRule
        .partition()
        .deployWithResponse(
            StreamUtil.read(resourceAsStream), ResourceType.BPMN_XML.name(), "collaboration.bpmn");

    // when
    final long wfInstance1 = testClient.createWorkflowInstance("process1");
    final long wfInstance2 = testClient.createWorkflowInstance("process2");

    // then
    final SubscribedRecord event1 =
        testClient.receiveFirstWorkflowInstanceEvent(wfInstance1, CREATED);

    assertThat(event1.value().get("bpmnProcessId")).isEqualTo("process1");

    final SubscribedRecord event2 =
        testClient.receiveFirstWorkflowInstanceEvent(wfInstance2, CREATED);

    assertThat(event2.value().get("bpmnProcessId")).isEqualTo("process2");
  }

  @SuppressWarnings("unchecked")
  private long extractWorkflowKey(final ExecuteCommandResponse deployment1) {
    final List<Map<String, Object>> deployedWorkflows =
        (List<Map<String, Object>>) deployment1.getValue().get("workflows");
    return (long) deployedWorkflows.get(0).get(PROP_WORKFLOW_KEY);
  }
}
