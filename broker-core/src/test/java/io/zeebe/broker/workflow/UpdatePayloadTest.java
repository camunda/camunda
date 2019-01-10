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

import static io.zeebe.broker.test.MsgPackConstants.MSGPACK_PAYLOAD;
import static io.zeebe.broker.workflow.WorkflowAssert.assertWorkflowInstancePayload;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class UpdatePayloadTest {

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask(
              "task-1",
              t ->
                  t.zeebeTaskType("task-1")
                      .zeebeTaskRetries(5)
                      .zeebeOutput("$.jsonObject", "$.obj"))
          .serviceTask("task-2", t -> t.zeebeTaskType("task-2").zeebeTaskRetries(5))
          .endEvent()
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldUpdatePayload() {
    // given
    testClient.deploy(WORKFLOW);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    final Record<WorkflowInstanceRecordValue> activityInstanceEvent =
        waitForActivityActivatedEvent();

    // when
    final ExecuteCommandResponse response =
        updatePayload(
            activityInstanceEvent.getKey(), MsgPackUtil.asMsgPackReturnArray("{'foo':'bar'}"));

    // then
    assertThat(response.getIntent()).isEqualTo(WorkflowInstanceIntent.PAYLOAD_UPDATED);

    final Record<WorkflowInstanceRecordValue> updateCommand =
        testClient.receiveFirstWorkflowInstanceCommand(WorkflowInstanceIntent.UPDATE_PAYLOAD);
    final Record<WorkflowInstanceRecordValue> updatedEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.PAYLOAD_UPDATED);

    assertThat(updatedEvent.getSourceRecordPosition()).isEqualTo(updateCommand.getPosition());
    assertThat(updatedEvent.getKey()).isEqualTo(activityInstanceEvent.getKey());
    assertThat(updatedEvent.getValue().getWorkflowInstanceKey()).isEqualTo(workflowInstanceKey);

    assertWorkflowInstancePayload(updatedEvent, "{'foo':'bar'}");
  }

  @Test
  public void shouldUpdateWithNilPayload() {
    // given
    testClient.deploy(WORKFLOW);

    testClient.createWorkflowInstance("process");

    final Record<WorkflowInstanceRecordValue> activityInstanceEvent =
        waitForActivityActivatedEvent();

    // when
    final ExecuteCommandResponse response =
        updatePayload(activityInstanceEvent.getKey(), MsgPackHelper.NIL);

    // then
    assertThat(response.getIntent()).isEqualTo(WorkflowInstanceIntent.PAYLOAD_UPDATED);
    final Record<WorkflowInstanceRecordValue> updatedEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.PAYLOAD_UPDATED);

    assertWorkflowInstancePayload(updatedEvent, "{}");
  }

  @Test
  public void shouldUpdateWithZeroLengthPayload() {
    // given
    testClient.deploy(WORKFLOW);

    testClient.createWorkflowInstance("process");

    final Record<WorkflowInstanceRecordValue> activityInstanceEvent =
        waitForActivityActivatedEvent();

    // when
    final ExecuteCommandResponse response =
        updatePayload(activityInstanceEvent.getKey(), new byte[0]);

    // then
    assertThat(response.getIntent()).isEqualTo(WorkflowInstanceIntent.PAYLOAD_UPDATED);
    final Record<WorkflowInstanceRecordValue> updatedEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.PAYLOAD_UPDATED);

    assertWorkflowInstancePayload(updatedEvent, "{}");
  }

  @Test
  public void shouldUpdatePayloadWhenActivityActivated() {
    // given
    testClient.deploy(WORKFLOW);

    testClient.createWorkflowInstance("process");

    final Record<WorkflowInstanceRecordValue> activityInstanceEvent =
        waitForActivityActivatedEvent();

    // when
    updatePayload(activityInstanceEvent.getKey(), MsgPackUtil.asMsgPackReturnArray("{'b':'wf'}"));

    testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.PAYLOAD_UPDATED);

    testClient.completeJobOfType("task-1", MSGPACK_PAYLOAD);

    // then
    final Record<WorkflowInstanceRecordValue> activityCompletedEvent =
        waitForActivityCompletedEvent();

    assertWorkflowInstancePayload(activityCompletedEvent, "{'obj':{'testAttr':'test'}, 'b':'wf'}");
  }

  @Test
  public void shouldUpdatePayloadWhenCatchEventIsEntered() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess("wf")
            .startEvent()
            .intermediateCatchEvent("catch-event")
            .message(b -> b.name("msg").zeebeCorrelationKey("$.id"))
            .done());

    testClient.createWorkflowInstance("wf", asMsgPack("id", "123"));

    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    updatePayload(
        catchEventEntered.getKey(), MsgPackUtil.asMsgPackReturnArray("{'id':'123', 'x': 1}"));

    testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.PAYLOAD_UPDATED);

    testClient.publishMessage("msg", "123", asMsgPack("y", 2));

    // then
    final Record<WorkflowInstanceRecordValue> catchEventOccurred =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETED);

    assertWorkflowInstancePayload(catchEventOccurred, "{'id':'123', 'x': 1, 'y': 2}");
  }

  @Test
  public void shouldThrowExceptionForInvalidPayload() {
    // given
    testClient.deploy(WORKFLOW);
    testClient.createWorkflowInstance("process");
    final Record<WorkflowInstanceRecordValue> activityInstanceEvent =
        waitForActivityActivatedEvent();

    // when
    final Throwable throwable =
        catchThrowable(
            () ->
                updatePayload(
                    activityInstanceEvent.getKey(), MsgPackUtil.asMsgPackReturnArray("'foo'")));

    // then
    assertThat(throwable).isInstanceOf(RuntimeException.class);
    assertThat(throwable.getMessage()).contains("Could not read property 'payload'.");
    assertThat(throwable.getMessage())
        .contains("Document has invalid format. On root level an object is only allowed.");
  }

  @Test
  public void shouldRejectUpdateForNonExistingWorkflowInstance() {
    // when
    final ExecuteCommandResponse response = updatePayload(-1L, MSGPACK_PAYLOAD);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);

    final Record<WorkflowInstanceRecordValue> rejection =
        testClient
            .receiveWorkflowInstances()
            .onlyCommandRejections()
            .withIntent(WorkflowInstanceIntent.UPDATE_PAYLOAD)
            .getFirst();

    assertThat(rejection).isNotNull();
  }

  @Test
  public void shouldRejectUpdateForCompletedWorkflowInstance() {
    // given
    testClient.deploy(WORKFLOW);

    testClient.createWorkflowInstance("process");

    final Record<WorkflowInstanceRecordValue> activityInstanceEvent =
        waitForActivityActivatedEvent();

    testClient.completeJobOfType("task-1", MSGPACK_PAYLOAD);

    waitForActivityCompletedEvent();
    testClient.completeJobOfType("task-2");

    testClient.receiveElementInState("process", WorkflowInstanceIntent.ELEMENT_COMPLETED);

    // when
    final ExecuteCommandResponse response =
        updatePayload(activityInstanceEvent.getKey(), MSGPACK_PAYLOAD);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);

    final Record<WorkflowInstanceRecordValue> rejection =
        testClient
            .receiveWorkflowInstances()
            .onlyCommandRejections()
            .withIntent(WorkflowInstanceIntent.UPDATE_PAYLOAD)
            .getFirst();

    assertThat(rejection).isNotNull();
  }

  private Record<WorkflowInstanceRecordValue> waitForActivityCompletedEvent() {
    return testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  private Record<WorkflowInstanceRecordValue> waitForActivityActivatedEvent() {
    return testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
  }

  private ExecuteCommandResponse updatePayload(
      final long activityInstanceKey, final byte[] payload) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.UPDATE_PAYLOAD)
        .key(activityInstanceKey)
        .command()
        .put("payload", payload)
        .done()
        .sendAndAwait();
  }
}
