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

import static io.zeebe.broker.test.MsgPackUtil.JSON_MAPPER;
import static io.zeebe.broker.test.MsgPackUtil.MSGPACK_MAPPER;
import static io.zeebe.broker.test.MsgPackUtil.MSGPACK_PAYLOAD;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
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

  private TestTopicClient testClient;

  @Before
  public void init() {
    testClient = apiRule.topic();
  }

  @Test
  public void shouldUpdatePayload() throws Exception {
    // given
    testClient.deploy(WORKFLOW);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    final SubscribedRecord activityInstanceEvent = waitForActivityActivatedEvent();

    // when
    final ExecuteCommandResponse response =
        updatePayload(
            activityInstanceEvent.position(),
            workflowInstanceKey,
            activityInstanceEvent.key(),
            MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'foo':'bar'}")));

    // then
    assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.PAYLOAD_UPDATED);

    final SubscribedRecord updateCommand =
        testClient.receiveFirstWorkflowInstanceCommand(WorkflowInstanceIntent.UPDATE_PAYLOAD);
    final SubscribedRecord updatedEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.PAYLOAD_UPDATED);

    assertThat(updateCommand.sourceRecordPosition()).isEqualTo(activityInstanceEvent.position());
    assertThat(updatedEvent.sourceRecordPosition()).isEqualTo(updateCommand.position());
    assertThat(updatedEvent.position()).isGreaterThan(response.position());
    assertThat(updatedEvent.key()).isEqualTo(activityInstanceEvent.key());
    assertThat(updatedEvent.value()).containsEntry("workflowInstanceKey", workflowInstanceKey);

    final byte[] payload = (byte[]) updatedEvent.value().get("payload");

    assertThat(MSGPACK_MAPPER.readTree(payload)).isEqualTo(JSON_MAPPER.readTree("{'foo':'bar'}"));
  }

  @Test
  public void shouldUpdateWithNilPayload() throws Exception {
    // given
    testClient.deploy(WORKFLOW);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    final SubscribedRecord activityInstanceEvent = waitForActivityActivatedEvent();

    // when
    final ExecuteCommandResponse response =
        updatePayload(
            activityInstanceEvent.position(),
            workflowInstanceKey,
            activityInstanceEvent.key(),
            MsgPackHelper.NIL);

    // then
    assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.PAYLOAD_UPDATED);
    final SubscribedRecord updatedEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.PAYLOAD_UPDATED);

    final byte[] payload = (byte[]) updatedEvent.value().get("payload");
    assertThat(payload).isEqualTo(MsgPackHelper.EMTPY_OBJECT);
  }

  @Test
  public void shouldUpdateWithZeroLengthPayload() throws Exception {
    // given
    testClient.deploy(WORKFLOW);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    final SubscribedRecord activityInstanceEvent = waitForActivityActivatedEvent();

    // when
    final ExecuteCommandResponse response =
        updatePayload(
            activityInstanceEvent.position(),
            workflowInstanceKey,
            activityInstanceEvent.key(),
            new byte[0]);

    // then
    assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.PAYLOAD_UPDATED);
    final SubscribedRecord updatedEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.PAYLOAD_UPDATED);

    final byte[] payload = (byte[]) updatedEvent.value().get("payload");
    assertThat(payload).isEqualTo(MsgPackHelper.EMTPY_OBJECT);
  }

  @Test
  public void shouldUpdatePayloadWhenActivityActivated() throws Exception {
    // given
    testClient.deploy(WORKFLOW);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    final SubscribedRecord activityInstanceEvent = waitForActivityActivatedEvent();

    // when
    updatePayload(
        activityInstanceEvent.position(),
        workflowInstanceKey,
        activityInstanceEvent.key(),
        MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'b':'wf'}")));

    testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.PAYLOAD_UPDATED);

    testClient.completeJobOfType("task-1", MSGPACK_PAYLOAD);

    // then
    final SubscribedRecord activityCompletedEvent = waitForActivityCompletedEvent();

    final byte[] payload = (byte[]) activityCompletedEvent.value().get("payload");
    assertThat(MSGPACK_MAPPER.readTree(payload))
        .isEqualTo(JSON_MAPPER.readTree("{'obj':{'testAttr':'test'}, 'b':'wf'}"));
  }

  @Test
  public void shouldUpdatePayloadWhenCatchEventIsEntered() throws Exception {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess("wf")
            .startEvent()
            .intermediateCatchEvent("catch-event")
            .message(b -> b.name("msg").zeebeCorrelationKey("$.id"))
            .done());

    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("id", "123"));

    final SubscribedRecord catchEventEntered =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    updatePayload(
        catchEventEntered.position(),
        workflowInstanceKey,
        catchEventEntered.key(),
        MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'id':'123', 'x': 1}")));

    testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.PAYLOAD_UPDATED);

    testClient.publishMessage("msg", "123", asMsgPack("y", 2));

    // then
    final SubscribedRecord catchEventOccurred =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETED);

    final byte[] payload = (byte[]) catchEventOccurred.value().get("payload");
    assertThat(MSGPACK_MAPPER.readTree(payload))
        .isEqualTo(JSON_MAPPER.readTree("{'id':'123', 'x': 1, 'y': 2}"));
  }

  @Test
  public void shouldThrowExceptionForInvalidPayload() {
    // given
    testClient.deploy(WORKFLOW);
    final long workflowInstanceKey = testClient.createWorkflowInstance("process");
    final SubscribedRecord activityInstanceEvent = waitForActivityActivatedEvent();

    // when
    final Throwable throwable =
        catchThrowable(
            () ->
                updatePayload(
                    activityInstanceEvent.position(),
                    workflowInstanceKey,
                    activityInstanceEvent.key(),
                    MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("'foo'"))));

    // then
    assertThat(throwable).isInstanceOf(RuntimeException.class);
    assertThat(throwable.getMessage()).contains("Could not read property 'payload'.");
    assertThat(throwable.getMessage())
        .contains("Document has invalid format. On root level an object is only allowed.");
  }

  @Test
  public void shouldRejectUpdateForNonExistingWorkflowInstance() throws Exception {
    // when
    final ExecuteCommandResponse response = updatePayload(-1, -1L, -1L, MSGPACK_PAYLOAD);

    // then
    final SubscribedRecord updateCommand =
        testClient.receiveFirstWorkflowInstanceCommand(WorkflowInstanceIntent.UPDATE_PAYLOAD);

    assertThat(response.sourceRecordPosition()).isEqualTo(updateCommand.position());
    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.rejectionReason()).isEqualTo("Workflow instance is not running");

    final SubscribedRecord rejection =
        testClient.receiveRejections().withIntent(WorkflowInstanceIntent.UPDATE_PAYLOAD).getFirst();

    assertThat(rejection).isNotNull();
  }

  @Test
  public void shouldRejectUpdateForCompletedWorkflowInstance() throws Exception {
    // given
    testClient.deploy(WORKFLOW);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    final SubscribedRecord activityInstanceEvent = waitForActivityActivatedEvent();

    testClient.completeJobOfType("task-1", MSGPACK_PAYLOAD);

    waitForActivityCompletedEvent();
    testClient.completeJobOfType("task-2");

    testClient.receiveElementInState("process", WorkflowInstanceIntent.ELEMENT_COMPLETED);

    // when
    final ExecuteCommandResponse response =
        updatePayload(
            activityInstanceEvent.position(),
            workflowInstanceKey,
            activityInstanceEvent.key(),
            MSGPACK_PAYLOAD);

    // then
    final SubscribedRecord updateCommand =
        testClient.receiveFirstWorkflowInstanceCommand(WorkflowInstanceIntent.UPDATE_PAYLOAD);

    assertThat(response.sourceRecordPosition()).isEqualTo(updateCommand.position());
    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.rejectionReason()).isEqualTo("Workflow instance is not running");

    final SubscribedRecord rejection =
        testClient.receiveRejections().withIntent(WorkflowInstanceIntent.UPDATE_PAYLOAD).getFirst();

    assertThat(rejection).isNotNull();
  }

  private SubscribedRecord waitForActivityCompletedEvent() {
    return testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  private SubscribedRecord waitForActivityActivatedEvent() {
    return testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
  }

  private ExecuteCommandResponse updatePayload(
      long sourceRecordPosition,
      final long workflowInstanceKey,
      final long activityInstanceKey,
      byte[] payload)
      throws Exception {
    return apiRule
        .createCmdRequest()
        .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.UPDATE_PAYLOAD)
        .key(activityInstanceKey)
        .sourceRecordPosition(sourceRecordPosition)
        .command()
        .put("workflowInstanceKey", workflowInstanceKey)
        .put("payload", payload)
        .done()
        .sendAndAwait();
  }
}
