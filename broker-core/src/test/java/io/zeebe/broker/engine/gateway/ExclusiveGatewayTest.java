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
package io.zeebe.broker.engine.gateway;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.common.collect.Sets;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ExclusiveGatewayTest {
  private static EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  private static ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);
  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldSplitOnExclusiveGateway() {
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .exclusiveGateway("xor")
            .sequenceFlowId("s1")
            .condition("foo < 5")
            .endEvent("a")
            .moveToLastGateway()
            .sequenceFlowId("s2")
            .condition("foo >= 5 && foo < 10")
            .endEvent("b")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s3")
            .endEvent("c")
            .done();
    testClient.deploy(workflowDefinition);

    final long workflowInstance1 =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(processId).setVariables(asMsgPack("foo", 4)))
            .getInstanceKey();
    final long workflowInstance2 =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(processId).setVariables(asMsgPack("foo", 8)))
            .getInstanceKey();
    final long workflowInstance3 =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(processId).setVariables(asMsgPack("foo", 12)))
            .getInstanceKey();
    final Set<Long> workflowInstanceKeys =
        Sets.newHashSet(workflowInstance1, workflowInstance2, workflowInstance3);

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .valueFilter(r -> workflowInstanceKeys.contains(r.getWorkflowInstanceKey()))
                .withElementType(BpmnElementType.END_EVENT)
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
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .exclusiveGateway("split")
            .sequenceFlowId("s1")
            .condition("foo < 5")
            .exclusiveGateway("joinRequest")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s2")
            .connectTo("joinRequest")
            .endEvent("end")
            .done();

    testClient.deploy(workflowDefinition);

    final long workflowInstance1 =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(processId).setVariables(asMsgPack("foo", 4)))
            .getInstanceKey();
    final long workflowInstance2 =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(processId).setVariables(asMsgPack("foo", 8)))
            .getInstanceKey();

    testClient.receiveElementInState(
        workflowInstance1, processId, WorkflowInstanceIntent.ELEMENT_COMPLETED);
    testClient.receiveElementInState(
        workflowInstance2, processId, WorkflowInstanceIntent.ELEMENT_COMPLETED);

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
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .exclusiveGateway("split")
            .sequenceFlowId("s1")
            .condition("foo < 5")
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
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(processId).setVariables(asMsgPack("foo", 4)))
            .getInstanceKey();

    // then
    testClient.receiveElementInState(
        workflowInstance1, processId, WorkflowInstanceIntent.ELEMENT_COMPLETED);

    List<Record<WorkflowInstanceRecordValue>> sequenceFlows =
        testClient
            .receiveWorkflowInstances()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withWorkflowInstanceKey(workflowInstance1)
            .limit(3)
            .collect(Collectors.toList());

    List<Record<WorkflowInstanceRecordValue>> gateWays =
        testClient
            .receiveWorkflowInstances()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.EXCLUSIVE_GATEWAY)
            .withWorkflowInstanceKey(workflowInstance1)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(gateWays.get(0).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(0).getPosition());
    assertThat(sequenceFlows.get(1).getValue().getElementId()).isEqualTo("s1");
    assertThat(gateWays.get(1).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(1).getPosition());

    // when
    final long workflowInstance2 =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(processId).setVariables(asMsgPack("foo", 8)))
            .getInstanceKey();

    // then
    testClient.receiveElementInState(
        workflowInstance2, processId, WorkflowInstanceIntent.ELEMENT_ACTIVATING);

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
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.EXCLUSIVE_GATEWAY)
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
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .exclusiveGateway("xor")
            .sequenceFlowId("s1")
            .condition("foo < 5")
            .endEvent("a")
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .sequenceFlowId("s2")
            .endEvent("b")
            .done();

    testClient.deploy(workflowDefinition);

    // when
    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(processId).setVariables(asMsgPack("foo", 4)))
            .getInstanceKey();

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowEvents =
        testClient
            .receiveWorkflowInstances()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skipUntil(r -> r.getValue().getElementId().equals("xor"))
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(workflowEvents)
        .extracting(Record::getMetadata)
        .extracting(e -> e.getIntent())
        .containsExactly(
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
  public void shouldSplitIfDefaultFlowIsDeclaredFirst() {
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .exclusiveGateway()
            .defaultFlow()
            .endEvent("a")
            .moveToLastExclusiveGateway()
            .condition("foo < 5")
            .endEvent("b")
            .done();

    testClient.deploy(workflowDefinition);

    // when
    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r1 -> r1.setBpmnProcessId(processId).setVariables(asMsgPack("foo", 10)))
            .getInstanceKey();

    // then
    final List<Record<WorkflowInstanceRecordValue>> completedEvents =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementType(BpmnElementType.END_EVENT)
            .collect(Collectors.toList());

    assertThat(completedEvents).extracting(r -> r.getValue().getElementId()).containsExactly("a");
  }

  @Test
  public void shouldEndScopeIfGatewayHasNoOutgoingFlows() {
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess(processId).startEvent().exclusiveGateway("xor").done();

    testClient.deploy(workflowDefinition);

    // when
    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r1 -> r1.setBpmnProcessId(processId).setVariables(asMsgPack("foo", 10)))
            .getInstanceKey();

    // then
    final List<Record<WorkflowInstanceRecordValue>> completedEvents =
        RecordingExporter.workflowInstanceRecords()
            .onlyEvents()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skipUntil(r -> r.getValue().getElementId().equals("xor"))
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(completedEvents)
        .extracting(r -> r.getMetadata().getIntent())
        .containsExactly(
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }
}
