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
package io.zeebe.broker.workflow.message;

import static io.zeebe.exporter.record.Assertions.assertThat;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.zeebe.test.util.record.RecordingExporter.messageStartEventSubscriptionRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.protocol.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class MessageStartEventTest {

  private static final String MESSAGE_NAME1 = "startMessage1";
  private static final String EVENT_ID1 = "startEventId1";

  private static final String MESSAGE_NAME2 = "startMessage2";
  private static final String EVENT_ID2 = "startEventId2";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldCorrelateMessageToStartEvent() {
    // given
    final ExecuteCommandResponse response =
        testClient.deployWithResponse(createWorkflowWithOneMessageStartEvent());

    final long workflowKey = getFirstDeployedWorkflowKey(response);

    // wait until subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    testClient.publishMessage(MESSAGE_NAME1, "order-123", asMsgPack("foo", "bar"));

    // then
    final Record<WorkflowInstanceRecordValue> record =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_OCCURRED).getFirst();

    assertThat(record.getValue()).hasWorkflowKey(workflowKey).hasElementId(EVENT_ID1);
  }

  @Test
  public void shouldCreateInstanceOnMessage() {
    // given
    final ExecuteCommandResponse response =
        testClient.deployWithResponse(createWorkflowWithOneMessageStartEvent());

    final long workflowKey = getFirstDeployedWorkflowKey(response);

    // wait until subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    testClient.publishMessage(MESSAGE_NAME1, "order-123", asMsgPack("foo", "bar"));

    // then
    final List<Record<WorkflowInstanceRecordValue>> records =
        RecordingExporter.workflowInstanceRecords().limit(5).asList();

    assertThat(records)
        .extracting(r -> r.getMetadata().getIntent())
        .containsExactly(
            WorkflowInstanceIntent.EVENT_OCCURRED, // message
            WorkflowInstanceIntent.ELEMENT_READY, // workflow instance
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.EVENT_TRIGGERING, // start event
            WorkflowInstanceIntent.EVENT_TRIGGERED);

    assertThat(records).allMatch(r -> r.getValue().getWorkflowKey() == workflowKey);

    assertThat(records.get(3).getValue()).hasElementId(EVENT_ID1);
    final WorkflowInstanceRecordValue eventTriggeredRecord = records.get(4).getValue();
    assertThat(eventTriggeredRecord.getPayloadAsMap()).containsEntry("foo", "bar");
  }

  @Test
  public void shouldApplyOutputMappingsOfMessageStartEvent() {
    // given
    testClient.deployWithResponse(createWorkflowWithMessageStartEventOutputMapping());

    // wait until subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    testClient.publishMessage(MESSAGE_NAME1, "order-123", asMsgPack("foo", "bar"));

    // then
    final Record<WorkflowInstanceRecordValue> record =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_TRIGGERED)
            .getFirst();

    assertThat(record.getValue().getPayloadAsMap()).containsEntry("mappedfoo", "bar");
  }

  @Test
  public void shouldCreateInstancesForMultipleMessagesOfSameName() {
    // given
    final ExecuteCommandResponse response =
        testClient.deployWithResponse(createWorkflowWithOneMessageStartEvent());

    final long workflowKey = getFirstDeployedWorkflowKey(response);

    // wait until subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    testClient.publishMessage(MESSAGE_NAME1, "order-123", asMsgPack("foo", "bar"));
    testClient.publishMessage(MESSAGE_NAME1, "order-124", asMsgPack("foo", "bar"));

    // then

    // check if two instances are created
    final List<Record<WorkflowInstanceRecordValue>> records =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_READY)
            .limit(2)
            .asList();

    assertThat(records).allMatch(r -> r.getValue().getWorkflowKey() == workflowKey);

    final WorkflowInstanceRecordValue recordValue1 = records.get(0).getValue();
    final WorkflowInstanceRecordValue recordValue2 = records.get(1).getValue();

    assertThat(recordValue1.getWorkflowInstanceKey())
        .isNotEqualTo(recordValue2.getWorkflowInstanceKey());
  }

  @Test
  public void shouldCreateInstancesForDifferentMessages() {
    // given
    final ExecuteCommandResponse response =
        testClient.deployWithResponse(createWorkflowWithTwoMessageStartEvent());

    final long workflowKey = getFirstDeployedWorkflowKey(response);

    // check if two subscriptions are opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .limit(2)
                .count())
        .isEqualTo(2);

    // when
    testClient.publishMessage(MESSAGE_NAME1, "order-123", asMsgPack("foo", "bar"));
    testClient.publishMessage(MESSAGE_NAME2, "order-124", asMsgPack("foo", "bar"));

    // then

    // check if two instances are created
    final List<Record<WorkflowInstanceRecordValue>> records =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_TRIGGERING)
            .limit(2)
            .asList();

    assertThat(records.size()).isEqualTo(2);
    assertThat(records).allMatch(r -> r.getValue().getWorkflowKey() == workflowKey);

    assertThat(records.get(0).getValue())
        .hasElementId(EVENT_ID1); // Message 1 triggers start event 1
    assertThat(records.get(1).getValue())
        .hasElementId(EVENT_ID2); // Message 2 triggers start event 2
  }

  @Test
  public void shouldNotCreateInstanceOfOldVersion() {
    // given
    testClient.deploy(createWorkflowWithOneMessageStartEvent());

    // new version
    final ExecuteCommandResponse response =
        testClient.deployWithResponse(createWorkflowWithOneMessageStartEvent());
    final long workflowKey2 = getFirstDeployedWorkflowKey(response);

    // wait until second subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .limit(2)
                .count())
        .isEqualTo(2);

    // when
    testClient.publishMessage(MESSAGE_NAME1, "order-123", asMsgPack("foo", "bar"));

    // then
    final List<Record<WorkflowInstanceRecordValue>> records =
        RecordingExporter.workflowInstanceRecords().limit(5).asList();

    assertThat(records.stream().map(r -> r.getMetadata().getIntent()))
        .containsExactly(
            WorkflowInstanceIntent.EVENT_OCCURRED, // message
            WorkflowInstanceIntent.ELEMENT_READY, // workflow instance
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.EVENT_TRIGGERING, // start event
            WorkflowInstanceIntent.EVENT_TRIGGERED);

    assertThat(records).allMatch(r -> r.getValue().getWorkflowKey() == workflowKey2);
  }

  private static BpmnModelInstance createWorkflowWithOneMessageStartEvent() {
    return Bpmn.createExecutableProcess("processId")
        .startEvent(EVENT_ID1)
        .message(m -> m.name(MESSAGE_NAME1).id("startmsgId"))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance createWorkflowWithTwoMessageStartEvent() {
    final ProcessBuilder process = Bpmn.createExecutableProcess("processId");
    process.startEvent(EVENT_ID1).message(m -> m.name(MESSAGE_NAME1).id("startmsgId1")).endEvent();
    process.startEvent(EVENT_ID2).message(m -> m.name(MESSAGE_NAME2).id("startmsgId2")).endEvent();

    return process.done();
  }

  private static BpmnModelInstance createWorkflowWithMessageStartEventOutputMapping() {
    return Bpmn.createExecutableProcess("processId")
        .startEvent(EVENT_ID1)
        .zeebeOutput("$.foo", "$.mappedfoo")
        .message(m -> m.name(MESSAGE_NAME1).id("startmsgId"))
        .endEvent()
        .done();
  }

  @SuppressWarnings("unchecked")
  private long getFirstDeployedWorkflowKey(final ExecuteCommandResponse response) {
    final List<Map<String, Object>> workflows =
        (List<Map<String, Object>>) response.getValue().get("workflows");
    return (long) workflows.get(0).get("workflowKey");
  }
}
