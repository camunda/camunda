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
package io.zeebe.engine.processor.workflow.incident;

import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.RecordType;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordMetadata;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.protocol.record.value.IncidentRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ExpressionIncidentTest {

  @ClassRule public static final EngineRule ENGINE = new EngineRule();

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

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

  @BeforeClass
  public static void init() {

    ENGINE
        .deployment()
        .withXmlResource(
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
                .done())
        .deploy();
  }

  @Test
  public void shouldCreateIncidentIfExclusiveGatewayHasNoMatchingCondition() {
    // given

    // when
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", 12).create();

    // then incident is created
    final Record<WorkflowInstanceRecordValue> failingEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementType(BpmnElementType.EXCLUSIVE_GATEWAY)
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final Record<IncidentRecordValue> incidentCommand =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATE)
            .getFirst();
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    assertThat(incidentCommand.getSourceRecordPosition()).isEqualTo(failingEvent.getPosition());

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.CONDITION_ERROR.name())
        .hasErrorMessage(
            "Expected at least one condition to evaluate to true, or to have a default flow")
        .hasBpmnProcessId("workflow")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("xor")
        .hasElementInstanceKey(failingEvent.getKey())
        .hasVariableScopeKey(failingEvent.getKey());
  }

  @Test
  public void shouldCreateIncidentIfConditionFailsToEvaluate() {
    // given

    // when
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", "bar").create();

    // then incident is created
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.CONDITION_ERROR.name())
        .hasErrorMessage(
            "Expected to evaluate condition 'foo >= 5 && foo < 10' successfully, but failed because: Cannot compare values of different types: STRING and INTEGER")
        .hasBpmnProcessId("workflow")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("xor");
  }

  @Test
  public void shouldResolveIncidentForFailedCondition() {
    // given

    // when
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", "bar").create();

    // then incident is created
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    final Record<WorkflowInstanceRecordValue> failureEvent =
        RecordingExporter.workflowInstanceRecords()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    // when correct variables is used
    ENGINE
        .variables()
        .ofScope(failureEvent.getKey())
        .withDocument(Maps.of(entry("foo", 7)))
        .update();
    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    final List<Record<IncidentRecordValue>> incidentRecords =
        RecordingExporter.incidentRecords()
            .withRecordKey(incidentEvent.getKey())
            .limit(r -> r.getMetadata().getIntent() == IncidentIntent.RESOLVED)
            .collect(Collectors.toList());

    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceRecords =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
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
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", "bar").create();

    final long incidentKey =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst()
            .getKey();

    final long failedEventKey =
        RecordingExporter.workflowInstanceRecords()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.EXCLUSIVE_GATEWAY)
            .getFirst()
            .getKey();

    ENGINE.variables().ofScope(failedEventKey).withDocument(Maps.of(entry("foo", 10))).update();
    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incidentKey).resolve();

    final Record<IncidentRecordValue> secondIncident =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skipUntil(r -> r.getMetadata().getIntent() == IncidentIntent.RESOLVED)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // when correct variables is used
    ENGINE.variables().ofScope(failedEventKey).withDocument(Maps.of(entry("foo", 7))).update();
    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(secondIncident.getKey()).resolve();

    // then
    final List<Record<IncidentRecordValue>> incidentRecords =
        RecordingExporter.incidentRecords()
            .withRecordKey(secondIncident.getKey())
            .skipUntil(r -> r.getMetadata().getIntent() == IncidentIntent.CREATED)
            .limit(r -> r.getMetadata().getIntent() == IncidentIntent.RESOLVED)
            .collect(Collectors.toList());

    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceRecords =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
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
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", 12).create();

    // then incident is created
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    final Record<WorkflowInstanceRecordValue> failureEvent =
        RecordingExporter.workflowInstanceRecords()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.EXCLUSIVE_GATEWAY)
            .getFirst();

    // when
    ENGINE
        .variables()
        .ofScope(failureEvent.getKey())
        .withDocument(Maps.of(entry("foo", 7)))
        .update();
    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withElementId("workflow")
                .withIntent(ELEMENT_COMPLETED)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldResolveIncidentIfInstanceCanceled() {
    // given
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", "bar").create();

    // when
    assertThat(
            RecordingExporter.incidentRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withIntent(IncidentIntent.CREATED)
                .exists())
        .isTrue();
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then incident is resolved
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.RESOLVED)
            .getFirst();

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.CONDITION_ERROR.name())
        .hasErrorMessage(
            "Expected to evaluate condition 'foo >= 5 && foo < 10' successfully, but failed because: Cannot compare values of different types: STRING and INTEGER")
        .hasBpmnProcessId("workflow")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("xor");
  }
}
