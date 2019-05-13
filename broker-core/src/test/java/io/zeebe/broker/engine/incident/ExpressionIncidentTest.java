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
package io.zeebe.broker.engine.incident;

import static io.zeebe.broker.engine.incident.IncidentAssert.assertIncidentRecordValue;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.exporter.api.record.value.IncidentRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.ErrorType;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.collection.Maps;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ExpressionIncidentTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  private static final byte[] VARIABLES;

  static {
    final DirectBuffer buffer =
        MsgPackUtil.encodeMsgPack(
            p -> {
              p.packMapHeader(1);
              p.packString("foo");
              p.packString("bar");
            });
    VARIABLES = new byte[buffer.capacity()];
    buffer.getBytes(0, VARIABLES);
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
            .condition("foo < 5")
            .endEvent()
            .moveToLastGateway()
            .sequenceFlowId("s2")
            .condition("foo >= 5 && foo < 10")
            .endEvent()
            .done());
  }

  @Test
  public void shouldCreateIncidentIfExclusiveGatewayHasNoMatchingCondition() {
    // given

    // when
    testClient
        .createWorkflowInstance(
            r -> r.setBpmnProcessId("workflow").setVariables(asMsgPack("foo", 12)))
        .getInstanceKey();

    // then incident is created
    final Record<WorkflowInstanceRecordValue> failingEvent =
        testClient.receiveFirstWorkflowInstanceEvent(
            WorkflowInstanceIntent.ELEMENT_ACTIVATING, BpmnElementType.EXCLUSIVE_GATEWAY);

    final Record<IncidentRecordValue> incidentCommand =
        testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    assertThat(incidentCommand.getSourceRecordPosition()).isEqualTo(failingEvent.getPosition());
    assertIncidentRecordValue(
        ErrorType.CONDITION_ERROR.name(),
        "Expected at least one condition to evaluate to true, or to have a default flow",
        "xor",
        incidentEvent);
  }

  @Test
  public void shouldCreateIncidentIfConditionFailsToEvaluate() {
    // given

    // when
    testClient
        .createWorkflowInstance(
            r -> r.setBpmnProcessId("workflow").setVariables(asMsgPack("foo", "bar")))
        .getInstanceKey();

    // then incident is created
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertIncidentRecordValue(
        ErrorType.CONDITION_ERROR.name(),
        "Expected to evaluate condition 'foo >= 5 && foo < 10' successfully, but failed because: Cannot compare values of different types: STRING and INTEGER",
        "xor",
        incidentEvent);
  }

  @Test
  public void shouldResolveIncidentForFailedCondition() {
    // given

    // when
    testClient
        .createWorkflowInstance(
            r -> r.setBpmnProcessId("workflow").setVariables(asMsgPack("foo", "bar")))
        .getInstanceKey();

    // then incident is created
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    final Record<WorkflowInstanceRecordValue> failureEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // when correct variables is used
    testClient.updateVariables(failureEvent.getKey(), Maps.of(entry("foo", 7)));
    testClient.resolveIncident(incidentEvent.getKey());

    // then
    final List<Record<IncidentRecordValue>> incidentRecords =
        testClient
            .receiveIncidents()
            .limit(r -> r.getMetadata().getIntent() == IncidentIntent.RESOLVED)
            .collect(Collectors.toList());

    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceRecords =
        testClient
            .receiveWorkflowInstances()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    // RESOLVE triggers RESOLVED
    assertThat(incidentRecords)
        .extracting(Record::getMetadata)
        .extracting(m -> tuple(m.getRecordType(), m.getIntent()))
        .containsSubsequence(
            tuple(RecordType.COMMAND, IncidentIntent.RESOLVE),
            tuple(RecordType.EVENT, IncidentIntent.RESOLVED));

    // GATEWAY_ACTIVATED triggers SEQUENCE_FLOW_TAKEN, END_EVENT_OCCURED and COMPLETED
    assertThat(workflowInstanceRecords)
        .extracting(Record::getMetadata)
        .extracting(RecordMetadata::getIntent)
        .containsSubsequence(
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldResolveIncidentForFailedConditionAfterUploadingWrongVariables() {
    // given
    testClient
        .createWorkflowInstance(
            r -> r.setBpmnProcessId("workflow").setVariables(asMsgPack("foo", "bar")))
        .getInstanceKey();

    final long incidentKey = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED).getKey();
    final long failedEventKey =
        testClient
            .receiveFirstWorkflowInstanceEvent(
                WorkflowInstanceIntent.ELEMENT_ACTIVATING, BpmnElementType.EXCLUSIVE_GATEWAY)
            .getKey();
    testClient.updateVariables(failedEventKey, Maps.of(entry("foo", 10)));
    testClient.resolveIncident(incidentKey);

    final Record<IncidentRecordValue> secondIncident =
        testClient
            .receiveIncidents()
            .skipUntil(r -> r.getMetadata().getIntent() == IncidentIntent.RESOLVED)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // when correct variables is used
    testClient.updateVariables(failedEventKey, Maps.of(entry("foo", 7)));
    testClient.resolveIncident(secondIncident.getKey());

    // then
    final List<Record<IncidentRecordValue>> incidentRecords =
        testClient
            .receiveIncidents()
            .skipUntil(r -> r.getMetadata().getIntent() == IncidentIntent.RESOLVED)
            .skipUntil(r -> r.getMetadata().getIntent() == IncidentIntent.CREATED)
            .limit(r -> r.getMetadata().getIntent() == IncidentIntent.RESOLVED)
            .collect(Collectors.toList());

    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceRecords =
        testClient
            .receiveWorkflowInstances()
            .skipUntil(
                r ->
                    r.getMetadata().getIntent() == ELEMENT_COMPLETED
                        && r.getValue().getBpmnElementType() == BpmnElementType.EXCLUSIVE_GATEWAY)
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    // RESOLVE triggers RESOLVED
    assertThat(incidentRecords)
        .extracting(Record::getMetadata)
        .extracting(m -> tuple(m.getRecordType(), m.getIntent()))
        .containsSubsequence(
            tuple(RecordType.COMMAND, IncidentIntent.RESOLVE),
            tuple(RecordType.EVENT, IncidentIntent.RESOLVED));

    // SEQUENCE_FLOW_TAKEN triggers the rest of the process
    assertThat(workflowInstanceRecords)
        .extracting(Record::getMetadata)
        .extracting(RecordMetadata::getIntent)
        .containsSubsequence(
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldResolveIncidentForExclusiveGatewayWithoutMatchingCondition() {
    // given

    // when
    testClient
        .createWorkflowInstance(
            r -> r.setBpmnProcessId("workflow").setVariables(asMsgPack("foo", 12)))
        .getInstanceKey();

    // then incident is created
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    final Record<WorkflowInstanceRecordValue> failureEvent =
        testClient.receiveFirstWorkflowInstanceEvent(
            WorkflowInstanceIntent.ELEMENT_ACTIVATING, BpmnElementType.EXCLUSIVE_GATEWAY);

    // when
    testClient.updateVariables(failureEvent.getKey(), Maps.of(entry("foo", 7)));
    testClient.resolveIncident(incidentEvent.getKey());

    // then
    testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVED);
    testClient.receiveElementInState("workflow", ELEMENT_COMPLETED);
  }

  @Test
  public void shouldResolveIncidentIfInstanceCanceled() {
    // given

    final long workflowInstance =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId("workflow").setVariables(asMsgPack("foo", "bar")))
            .getInstanceKey();

    // when
    testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);
    testClient.cancelWorkflowInstance(workflowInstance);

    // then incident is resolved
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVED);

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertIncidentRecordValue(
        ErrorType.CONDITION_ERROR.name(),
        "Expected to evaluate condition 'foo >= 5 && foo < 10' successfully, but failed because: Cannot compare values of different types: STRING and INTEGER",
        "xor",
        incidentEvent);
  }
}
