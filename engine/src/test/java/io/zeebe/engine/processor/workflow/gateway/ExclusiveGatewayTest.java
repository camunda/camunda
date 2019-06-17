/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordMetadata;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ExclusiveGatewayTest {

  @ClassRule public static final EngineRule ENGINE = new EngineRule();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldSplitOnExclusiveGateway() {
    // given
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
    ENGINE.deployment().withXmlResource(workflowDefinition).deploy();

    // when
    final long workflowInstance1 =
        ENGINE.workflowInstance().ofBpmnProcessId(processId).withVariable("foo", 4).create();
    final long workflowInstance2 =
        ENGINE.workflowInstance().ofBpmnProcessId(processId).withVariable("foo", 8).create();
    final long workflowInstance3 =
        ENGINE.workflowInstance().ofBpmnProcessId(processId).withVariable("foo", 12).create();

    final List<Long> workflowInstanceKeys =
        Arrays.asList(workflowInstance1, workflowInstance2, workflowInstance3);

    // then
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

    ENGINE.deployment().withXmlResource(workflowDefinition).deploy();

    // when
    final long workflowInstance1 =
        ENGINE.workflowInstance().ofBpmnProcessId(processId).withVariable("foo", 4).create();
    final long workflowInstance2 =
        ENGINE.workflowInstance().ofBpmnProcessId(processId).withVariable("foo", 8).create();

    // then

    List<String> takenSequenceFlows =
        RecordingExporter.workflowInstanceRecords()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withWorkflowInstanceKey(workflowInstance1)
            .limit(3)
            .map(s -> s.getValue().getElementId())
            .collect(Collectors.toList());
    assertThat(takenSequenceFlows).contains("s1").doesNotContain("s2");

    takenSequenceFlows =
        RecordingExporter.workflowInstanceRecords()
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

    ENGINE.deployment().withXmlResource(workflowDefinition).deploy();

    // when
    final long workflowInstance1 =
        ENGINE.workflowInstance().ofBpmnProcessId(processId).withVariable("foo", 4).create();
    // then
    List<Record<WorkflowInstanceRecordValue>> sequenceFlows =
        RecordingExporter.workflowInstanceRecords()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withWorkflowInstanceKey(workflowInstance1)
            .limit(3)
            .asList();

    List<Record<WorkflowInstanceRecordValue>> gateWays =
        RecordingExporter.workflowInstanceRecords()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.EXCLUSIVE_GATEWAY)
            .withWorkflowInstanceKey(workflowInstance1)
            .limit(2)
            .asList();

    assertThat(gateWays.get(0).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(0).getPosition());
    assertThat(sequenceFlows.get(1).getValue().getElementId()).isEqualTo("s1");
    assertThat(gateWays.get(1).getSourceRecordPosition())
        .isEqualTo(sequenceFlows.get(1).getPosition());

    // when
    final long workflowInstance2 =
        ENGINE.workflowInstance().ofBpmnProcessId(processId).withVariable("foo", 8).create();
    // then
    sequenceFlows =
        RecordingExporter.workflowInstanceRecords()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withWorkflowInstanceKey(workflowInstance2)
            .limit(3)
            .collect(Collectors.toList());

    gateWays =
        RecordingExporter.workflowInstanceRecords()
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

    ENGINE.deployment().withXmlResource(workflowDefinition).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId(processId).withVariable("foo", 4).create();

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowEvents =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skipUntil(r -> r.getValue().getElementId().equals("xor"))
            .limitToWorkflowInstanceCompleted()
            .asList();

    assertThat(workflowEvents)
        .extracting(Record::getMetadata)
        .extracting(RecordMetadata::getIntent)
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
    // given
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

    ENGINE.deployment().withXmlResource(workflowDefinition).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId(processId).withVariable("foo", 10).create();

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
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance workflowDefinition =
        Bpmn.createExecutableProcess(processId).startEvent().exclusiveGateway("xor").done();

    ENGINE.deployment().withXmlResource(workflowDefinition).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId(processId).withVariable("foo", 10).create();

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
