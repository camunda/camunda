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
package io.zeebe.broker.workflow.gateway;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ExclusiveGatewayTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldSpitOnExclusiveGateway() {
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess("workflow")
            .startEvent()
            .exclusiveGateway("xor")
            .sequenceFlowId("s1")
            .condition("$.foo < 5")
            .endEvent("a")
            .moveToLastGateway()
            .sequenceFlowId("s2")
            .condition("$.foo >= 5 && $.foo < 10")
            .endEvent("b")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s3")
            .endEvent("c")
            .done();

    testClient.deploy(workflowDefinition);

    final long workflowInstance1 =
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 4));
    final long workflowInstance2 =
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 8));
    final long workflowInstance3 =
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 12));

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_ACTIVATED)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getWorkflowInstanceKey(), v.getElementId()))
        .contains(
            tuple(workflowInstance1, "a"),
            tuple(workflowInstance2, "b"),
            tuple(workflowInstance3, "c"));
  }

  @Test
  public void shouldJoinOnExclusiveGateway() {
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess("workflow")
            .startEvent()
            .exclusiveGateway("split")
            .sequenceFlowId("s1")
            .condition("$.foo < 5")
            .exclusiveGateway("joinRequest")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s2")
            .connectTo("joinRequest")
            .endEvent("end")
            .done();

    testClient.deploy(workflowDefinition);

    final long workflowInstance1 =
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 4));
    final long workflowInstance2 =
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 8));

    testClient.receiveElementInState(
        workflowInstance1, "workflow", WorkflowInstanceIntent.ELEMENT_COMPLETED);
    testClient.receiveElementInState(
        workflowInstance2, "workflow", WorkflowInstanceIntent.ELEMENT_COMPLETED);

    List<String> takenSequenceFlows =
        testClient
            .receiveWorkflowInstances()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withWorkflowInstanceKey(workflowInstance1)
            .limit(3)
            .map(s -> s.getValue().getElementId())
            .collect(Collectors.toList());
    assertThat(takenSequenceFlows).contains("s1").doesNotContain("s2");

    takenSequenceFlows =
        testClient
            .receiveWorkflowInstances()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withWorkflowInstanceKey(workflowInstance2)
            .limit(3)
            .map(s -> s.getValue().getElementId())
            .collect(Collectors.toList());
    assertThat(takenSequenceFlows).contains("s2").doesNotContain("s1");
  }

  @Test
  public void shouldSetSourceRecordPositionCorrectOnJoinXor() {
    // given
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess("workflow")
            .startEvent()
            .exclusiveGateway("split")
            .sequenceFlowId("s1")
            .condition("$.foo < 5")
            .exclusiveGateway("joinRequest")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s2")
            .connectTo("joinRequest")
            .endEvent("end")
            .done();

    testClient.deploy(workflowDefinition);

    // when
    final long workflowInstance1 =
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 4));

    // then
    testClient.receiveElementInState(
        workflowInstance1, "workflow", WorkflowInstanceIntent.ELEMENT_COMPLETED);

    List<Record<WorkflowInstanceRecordValue>> sequenceFlows =
        testClient
            .receiveWorkflowInstances()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .limit(3)
            .collect(Collectors.toList());

    List<Record<WorkflowInstanceRecordValue>> gateWays =
        testClient
            .receiveWorkflowInstances()
            .withIntent(WorkflowInstanceIntent.GATEWAY_ACTIVATED)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(gateWays.get(0).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(0).getPosition());
    assertThat(sequenceFlows.get(1).getValue().getElementId()).isEqualTo("s1");
    assertThat(gateWays.get(1).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(1).getPosition());

    // when
    final long workflowInstance2 =
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 8));

    // then
    testClient.receiveElementInState(
        workflowInstance2, "workflow", WorkflowInstanceIntent.ELEMENT_COMPLETED);

    sequenceFlows =
        testClient
            .receiveWorkflowInstances()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withWorkflowInstanceKey(workflowInstance2)
            .limit(3)
            .collect(Collectors.toList());

    gateWays =
        testClient
            .receiveWorkflowInstances()
            .withIntent(WorkflowInstanceIntent.GATEWAY_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstance2)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(gateWays.get(0).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(0).getPosition());
    assertThat(sequenceFlows.get(1).getValue().getElementId()).isEqualTo("s2");
    assertThat(gateWays.get(1).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(1).getPosition());
  }

  @Test
  public void testWorkflowInstanceStatesWithExclusiveGateway() {
    // given
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess("workflow")
            .startEvent()
            .exclusiveGateway("xor")
            .sequenceFlowId("s1")
            .condition("$.foo < 5")
            .endEvent("a")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s2")
            .endEvent("b")
            .done();

    testClient.deploy(workflowDefinition);

    // when
    testClient.createWorkflowInstance("workflow", asMsgPack("foo", 4));

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowEvents =
        testClient
            .receiveWorkflowInstances()
            .skipUntil(r -> r.getValue().getElementId().equals("xor"))
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(workflowEvents)
        .extracting(Record::getMetadata)
        .extracting(e -> e.getIntent())
        .containsExactly(
            WorkflowInstanceIntent.GATEWAY_ACTIVATED,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            WorkflowInstanceIntent.EVENT_ACTIVATING,
            WorkflowInstanceIntent.EVENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldSplitIfDefaultFlowIsDeclaredFirst() {
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess("workflow")
            .startEvent()
            .exclusiveGateway()
            .defaultFlow()
            .endEvent("a")
            .moveToLastExclusiveGateway()
            .condition("$.foo < 5")
            .endEvent("b")
            .done();

    testClient.deploy(workflowDefinition);

    // when
    testClient.createWorkflowInstance("workflow", MsgPackUtil.asMsgPack("foo", 10));

    // then
    final List<Record<WorkflowInstanceRecordValue>> completedEvents =
        RecordingExporter.workflowInstanceRecords()
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.EVENT_ACTIVATED)
            .collect(Collectors.toList());

    assertThat(completedEvents).extracting(r -> r.getValue().getElementId()).containsExactly("a");
  }

  @Test
  public void shouldEndScopeIfGatewayHasNoOutgoingFlows() {
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess("workflow").startEvent().exclusiveGateway("xor").done();

    testClient.deploy(workflowDefinition);

    // when
    testClient.createWorkflowInstance("workflow", MsgPackUtil.asMsgPack("foo", 10));

    // then
    final List<Record<WorkflowInstanceRecordValue>> completedEvents =
        RecordingExporter.workflowInstanceRecords()
            .onlyEvents()
            .skipUntil(r -> r.getValue().getElementId().equals("xor"))
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(completedEvents)
        .extracting(r -> r.getMetadata().getIntent())
        .containsExactly(
            WorkflowInstanceIntent.GATEWAY_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }
}
