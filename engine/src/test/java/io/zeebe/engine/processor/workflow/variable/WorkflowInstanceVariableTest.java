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
package io.zeebe.engine.processor.workflow.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.VariableRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.VariableIntent;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordingExporter;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class WorkflowInstanceVariableTest {

  public static final String PROCESS_ID = "process";

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeTaskType("test"))
          .endEvent()
          .done();

  @ClassRule public static final EngineRule ENGINE_RULE = new EngineRule();
  private static long workflowKey;

  @BeforeClass
  public static void init() {
    workflowKey =
        ENGINE_RULE
            .deployment()
            .withXmlResource(WORKFLOW)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0)
            .getWorkflowKey();
  }

  @Test
  public void shouldCreateVariableByWorkflowInstanceCreation() {
    // given

    // when
    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{'x':1}")
            .create();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasWorkflowKey(workflowKey)
        .hasName("x")
        .hasValue("1");
  }

  @Test
  public void shouldCreateVariableByJobCompletion() {
    // given
    final long workflowInstanceKey =
        ENGINE_RULE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE_RULE
        .job()
        .ofInstance(workflowInstanceKey)
        .withType("test")
        .withVariables("{'x':1}")
        .complete();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasWorkflowKey(workflowKey)
        .hasName("x")
        .hasValue("1");
  }

  @Test
  public void shouldCreateVariableByOutputMapping() {
    // given
    final long workflowKey =
        ENGINE_RULE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("shouldCreateVariableByOutputMapping")
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeTaskType("test").zeebeOutput("x", "y"))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0)
            .getWorkflowKey();

    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId("shouldCreateVariableByOutputMapping")
            .create();

    // when
    ENGINE_RULE
        .job()
        .ofInstance(workflowInstanceKey)
        .withType("test")
        .withVariables("{'x':1}")
        .complete();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withName("y")
            .getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasWorkflowKey(workflowKey)
        .hasName("y")
        .hasValue("1");
  }

  @Test
  public void shouldCreateVariableByUpdateVariables() {
    // given
    final long workflowInstanceKey =
        ENGINE_RULE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE_RULE
        .variables()
        .ofScope(workflowInstanceKey)
        .withDocument(Maps.of(entry("x", 1)))
        .update();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasWorkflowKey(workflowKey)
        .hasName("x")
        .hasValue("1");
  }

  @Test
  public void shouldCreateMultipleVariables() {
    // given

    // when
    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{'x':1, 'y':2}")
            .create();

    // then
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .hasSize(2)
        .contains(tuple("x", "1"), tuple("y", "2"));
  }

  @Test
  public void shouldUpdateVariableByJobCompletion() {
    // given
    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{'x':1}")
            .create();

    // when
    ENGINE_RULE
        .job()
        .ofInstance(workflowInstanceKey)
        .withType("test")
        .withVariables("{'x':2}")
        .complete();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasWorkflowKey(workflowKey)
        .hasName("x")
        .hasValue("2");
  }

  @Test
  public void shouldUpdateVariableByOutputMapping() {
    // given
    final long workflowKey =
        ENGINE_RULE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("shouldUpdateVariableByOutputMapping")
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeTaskType("test").zeebeOutput("x", "y"))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0)
            .getWorkflowKey();

    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId("shouldUpdateVariableByOutputMapping")
            .withVariables("{'y':1}")
            .create();

    // when
    ENGINE_RULE
        .job()
        .ofInstance(workflowInstanceKey)
        .withType("test")
        .withVariables("{'x':2}")
        .complete();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasWorkflowKey(workflowKey)
        .hasName("y")
        .hasValue("2");
  }

  @Test
  public void shouldUpdateVariableByUpdateVariables() {
    // given
    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{'x':1}")
            .create();

    // when
    ENGINE_RULE
        .variables()
        .ofScope(workflowInstanceKey)
        .withDocument(Maps.of(entry("x", 2)))
        .update();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(workflowInstanceKey)
        .hasWorkflowKey(workflowKey)
        .hasName("x")
        .hasValue("2");
  }

  @Test
  public void shouldUpdateMultipleVariables() {
    // given
    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{'x':1, 'y':2, 'z':3}")
            .create();

    // when
    ENGINE_RULE
        .job()
        .ofInstance(workflowInstanceKey)
        .withType("test")
        .withVariables("{'x':1, 'y':4, 'z':5}")
        .complete();

    // then
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .hasSize(2)
        .contains(tuple("y", "4"), tuple("z", "5"));
  }

  @Test
  public void shouldCreateAndUpdateVariables() {
    // given
    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{'x':1}")
            .create();

    final Record<VariableRecordValue> variableCreated =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    // when
    ENGINE_RULE
        .variables()
        .ofScope(workflowInstanceKey)
        .withDocument(Maps.of(entry("x", 2), entry("y", 3)))
        .update();

    // then
    assertThat(
            RecordingExporter.variableRecords()
                .skipUntil(r -> r.getPosition() > variableCreated.getPosition())
                .limit(2))
        .extracting(
            record ->
                tuple(
                    record.getMetadata().getIntent(),
                    record.getValue().getWorkflowKey(),
                    record.getValue().getName(),
                    record.getValue().getValue()))
        .hasSize(2)
        .contains(
            tuple(VariableIntent.UPDATED, workflowKey, "x", "2"),
            tuple(VariableIntent.CREATED, workflowKey, "y", "3"));
  }

  @Test
  public void shouldHaveSameKeyOnVariableUpdate() {
    // given
    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{'x':1}")
            .create();

    final Record<VariableRecordValue> variableCreated =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    // when
    ENGINE_RULE
        .variables()
        .ofScope(workflowInstanceKey)
        .withDocument(Maps.of(entry("x", 2)))
        .update();

    // then
    final Record<VariableRecordValue> variableUpdated =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    assertThat(variableCreated.getKey()).isEqualTo(variableUpdated.getKey());
  }
}
