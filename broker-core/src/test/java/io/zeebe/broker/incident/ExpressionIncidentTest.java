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
package io.zeebe.broker.incident;

import static io.zeebe.broker.incident.IncidentAssert.assertIncidentRecordValue;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.RecordMetadata;
import io.zeebe.exporter.record.value.IncidentRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ExpressionIncidentTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  private static final byte[] PAYLOAD;

  static {
    final DirectBuffer buffer =
        MsgPackUtil.encodeMsgPack(
            p -> {
              p.packMapHeader(1);
              p.packString("foo");
              p.packString("bar");
            });
    PAYLOAD = new byte[buffer.capacity()];
    buffer.getBytes(0, PAYLOAD);
  }

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
    apiRule.waitForPartition(1);

    testClient.deploy(
        Bpmn.createExecutableProcess("workflow")
            .startEvent()
            .exclusiveGateway("xor")
            .sequenceFlowId("s1")
            .condition("$.foo < 5")
            .endEvent()
            .moveToLastGateway()
            .sequenceFlowId("s2")
            .condition("$.foo >= 5 && $.foo < 10")
            .endEvent()
            .done());
  }

  @Test
  public void shouldCreateIncidentIfExclusiveGatewayHasNoMatchingCondition() {
    // given

    // when
    testClient.createWorkflowInstance("workflow", asMsgPack("foo", 12));

    // then incident is created
    final Record failingEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.GATEWAY_ACTIVATED);

    final Record incidentCommand = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    assertThat(incidentCommand.getSourceRecordPosition()).isEqualTo(failingEvent.getPosition());
    assertIncidentRecordValue(
        ErrorType.CONDITION_ERROR.name(),
        "All conditions evaluated to false and no default flow is set.",
        "xor",
        incidentEvent);
  }

  @Test
  public void shouldCreateIncidentIfConditionFailsToEvaluate() {
    // given

    // when
    testClient.createWorkflowInstance("workflow", asMsgPack("foo", "bar"));

    // then incident is created
    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertIncidentRecordValue(
        ErrorType.CONDITION_ERROR.name(),
        "Cannot compare values of different types: STRING and INTEGER",
        "xor",
        incidentEvent);
  }

  @Test
  public void shouldResolveIncidentForFailedCondition() {
    // given

    // when
    testClient.createWorkflowInstance("workflow", asMsgPack("foo", "bar"));

    // then incident is created
    testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    final Record failureEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.GATEWAY_ACTIVATED);

    // when correct payload is used
    testClient.updatePayload(failureEvent.getKey(), asMsgPack("foo", 7).byteArray());

    // then
    final List<Record> incidentRecords =
        testClient
            .receiveIncidents()
            .limit(r -> r.getMetadata().getIntent() == IncidentIntent.RESOLVED)
            .collect(Collectors.toList());

    final List<Record> workflowInstanceRecords =
        testClient
            .receiveWorkflowInstances()
            .limit(
                r ->
                    r.getMetadata().getIntent() == ELEMENT_COMPLETED
                        && r.getValue().getWorkflowInstanceKey() == r.getKey())
            .collect(Collectors.toList());

    // RESOLVE triggers RESOLVED
    assertThat(incidentRecords)
        .extracting(Record::getMetadata)
        .extracting(
            RecordMetadata::getRecordType, RecordMetadata::getValueType, RecordMetadata::getIntent)
        .containsSubsequence(
            tuple(RecordType.COMMAND, ValueType.INCIDENT, IncidentIntent.RESOLVE),
            tuple(RecordType.EVENT, ValueType.INCIDENT, IncidentIntent.RESOLVED));

    // GATEWAY_ACTIVATED triggers SEQUENCE_FLOW_TAKEN, END_EVENT_OCCURED and COMPLETED
    assertThat(workflowInstanceRecords)
        .extracting(Record::getMetadata)
        .extracting(
            RecordMetadata::getRecordType, RecordMetadata::getValueType, RecordMetadata::getIntent)
        .containsSubsequence(
            tuple(
                RecordType.EVENT,
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.GATEWAY_ACTIVATED),
            tuple(
                RecordType.EVENT,
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                RecordType.EVENT,
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.END_EVENT_OCCURRED),
            tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, ELEMENT_COMPLETED));
  }

  @Test
  public void shouldResolveIncidentForFailedConditionAfterUploadingWrongPayload() {
    // given

    // when
    testClient.createWorkflowInstance("workflow", asMsgPack("foo", "bar"));

    // then incident is created
    testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    final Record failureEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.GATEWAY_ACTIVATED);

    // when not correct payload is used
    testClient.updatePayload(failureEvent.getKey(), asMsgPack("foo", 10).byteArray());

    // then
    List<Record> incidentRecords =
        testClient
            .receiveIncidents()
            .limit(r -> r.getMetadata().getIntent() == IncidentIntent.RESOLVED)
            .collect(Collectors.toList());

    List<Record> workflowInstanceRecords =
        testClient
            .receiveWorkflowInstances()
            .limit(r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.GATEWAY_ACTIVATED)
            .collect(Collectors.toList());

    assertThat(incidentRecords)
        .extracting(Record::getMetadata)
        .extracting(
            RecordMetadata::getRecordType, RecordMetadata::getValueType, RecordMetadata::getIntent)
        .containsSubsequence(
            tuple(RecordType.COMMAND, ValueType.INCIDENT, IncidentIntent.RESOLVE),
            tuple(RecordType.EVENT, ValueType.INCIDENT, IncidentIntent.RESOLVED));

    assertThat(workflowInstanceRecords)
        .extracting(Record::getMetadata)
        .extracting(
            RecordMetadata::getRecordType, RecordMetadata::getValueType, RecordMetadata::getIntent)
        .containsSubsequence(
            tuple(
                RecordType.EVENT,
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.GATEWAY_ACTIVATED));

    // when correct payload is used
    testClient.updatePayload(failureEvent.getKey(), asMsgPack("foo", 7).byteArray());

    // then
    incidentRecords =
        testClient
            .receiveIncidents()
            .skipUntil(r -> r.getMetadata().getIntent() == IncidentIntent.RESOLVED)
            .skipUntil(r -> r.getMetadata().getIntent() == IncidentIntent.CREATED)
            .limit(r -> r.getMetadata().getIntent() == IncidentIntent.RESOLVED)
            .collect(Collectors.toList());

    workflowInstanceRecords =
        testClient
            .receiveWorkflowInstances()
            .skipUntil(r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.GATEWAY_ACTIVATED)
            .limit(
                r ->
                    r.getMetadata().getIntent() == ELEMENT_COMPLETED
                        && r.getValue().getWorkflowInstanceKey() == r.getKey())
            .collect(Collectors.toList());

    // RESOLVE triggers  RESOLVED
    assertThat(incidentRecords)
        .extracting(Record::getMetadata)
        .extracting(
            RecordMetadata::getRecordType, RecordMetadata::getValueType, RecordMetadata::getIntent)
        .containsSubsequence(
            tuple(RecordType.COMMAND, ValueType.INCIDENT, IncidentIntent.RESOLVE),
            tuple(RecordType.EVENT, ValueType.INCIDENT, IncidentIntent.RESOLVED));

    // SEQUENCE_FLOW_TAKEN triggers the rest of the process
    assertThat(workflowInstanceRecords)
        .extracting(Record::getMetadata)
        .extracting(
            RecordMetadata::getRecordType, RecordMetadata::getValueType, RecordMetadata::getIntent)
        .containsSubsequence(
            tuple(
                RecordType.EVENT,
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                RecordType.EVENT,
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.END_EVENT_OCCURRED),
            tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, ELEMENT_COMPLETED));
  }

  @Test
  public void shouldResolveIncidentForExclusiveGatewayWithoutMatchingCondition() {
    // given

    // when
    testClient.createWorkflowInstance("workflow", asMsgPack("foo", 12));

    // then incident is created
    testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    final Record failureEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.GATEWAY_ACTIVATED);

    // when
    testClient.updatePayload(failureEvent.getKey(), asMsgPack("foo", 7).byteArray());

    // then
    testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVED);
    testClient.receiveElementInState("workflow", ELEMENT_COMPLETED);
  }

  @Test
  public void shouldResolveIncidentIfInstanceCanceled() {
    // given

    final long workflowInstance =
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", "bar"));

    // when
    testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);
    testClient.cancelWorkflowInstance(workflowInstance);

    // then incident is resolved
    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVED);

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertIncidentRecordValue(
        ErrorType.CONDITION_ERROR.name(),
        "Cannot compare values of different types: STRING and INTEGER",
        "xor",
        incidentEvent);
  }
}
