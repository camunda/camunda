/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static io.camunda.zeebe.protocol.record.intent.IncidentIntent.CREATED;
import static io.camunda.zeebe.protocol.record.intent.IncidentIntent.RESOLVE;
import static io.camunda.zeebe.protocol.record.intent.IncidentIntent.RESOLVED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class MappingIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final BpmnModelInstance PROCESS_INPUT_MAPPING =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask(
              "failingTask",
              t -> t.zeebeJobType("test").zeebeInputExpression("assert(foo, foo != null)", "foo"))
          .done();
  private static final BpmnModelInstance PROCESS_OUTPUT_MAPPING =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask(
              "failingTask",
              t -> t.zeebeJobType("test").zeebeOutputExpression("assert(foo, foo != null)", "foo"))
          .done();
  private static final Map<String, Object> VARIABLES = Maps.of(entry("foo", "bar"));
  private static final String VARIABLES_JSON =
      "{'string':'value', 'jsonObject':{'testAttr':'test'}}";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateIncidentForInputMappingFailure() {
    // given
    final long processDefinitionKey =
        ENGINE
            .deployment()
            .withXmlResource(PROCESS_INPUT_MAPPING)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // then
    final Record failureCommand =
        RecordingExporter.processInstanceRecords()
            .withElementId("failingTask")
            .withIntent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .onlyEvents()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertThat(incidentEvent.getValue().getVariableScopeKey()).isEqualTo(failureCommand.getKey());

    final IncidentRecordValue incidentEventValue = incidentEvent.getValue();
    Assertions.assertThat(incidentEventValue)
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureCommand.getKey())
        .hasVariableScopeKey(failureCommand.getKey())
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(incidentEventValue.getErrorMessage())
        .contains("Assertion failure on evaluate the expression");
  }

  @Test
  public void shouldCreateIncidentForNonMatchingAndMatchingValueOnInputMapping() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask(
                    "service",
                    t ->
                        t.zeebeJobType("external")
                            .zeebeInputExpression(
                                "assert(notExisting, notExisting != null)", "nullVal")
                            .zeebeInputExpression("string", "existing"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process").withVariables(VARIABLES_JSON).create();

    final Record<ProcessInstanceRecordValue> failureEvent =
        RecordingExporter.processInstanceRecords()
            .withElementId("service")
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // then incident is created
    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertThat(incidentEvent.getValue().getVariableScopeKey()).isEqualTo(failureEvent.getKey());

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("service")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey())
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(incidentEvent.getValue().getErrorMessage())
        .contains("Assertion failure on evaluate the expression");
  }

  @Test
  public void shouldNotApplyAnyInputMappingIfOneFails() {
    // given: the second of two input mappings has a genuinely failing source expression (an
    // unresolved variable alone is NOT sufficient to trigger a failure — see "Investigation
    // finding" earlier in this plan; assert() forces a real FEEL evaluation failure, matching the
    // trigger already used by every other case in this test class)
    final var process =
        Bpmn.createExecutableProcess("process-atomic")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("test")
                        .zeebeInputExpression("1", "first")
                        .zeebeInputExpression("assert(foo, foo != null)", "second"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process-atomic").create();

    // then: an incident is raised for the failing mapping ...
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(incident.getValue()).hasErrorType(ErrorType.IO_MAPPING_ERROR);
    assertThat(incident.getValue().getErrorMessage())
        .contains("Assertion failure on evaluate the expression");

    // ... and the successful first mapping produced no variable (bounded absence check — a bare
    // `.exists()` on a stream with nothing to find blocks for the default wait time and then
    // throws; expectNoMatchingRecords shortens that wait and lets `.exists()` return false)
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                ignored ->
                    RecordingExporter.variableRecords()
                        .withProcessInstanceKey(processInstanceKey)
                        .withName("first")
                        .exists()))
        .isFalse();

    // when: the missing variable is provided and the incident is resolved
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("foo", "bar")).update();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then: all mappings are re-evaluated from the start
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("first")
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("second")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldNotApplyAnyOutputMappingIfOneFails() {
    // given: three output mappings where the second one fails
    final var process =
        Bpmn.createExecutableProcess("process-atomic-output")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("test")
                        .zeebeOutputExpression("1", "first")
                        .zeebeOutputExpression("assert(missing, missing != null)", "second")
                        .zeebeOutputExpression("2", "third"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process-atomic-output").create();
    ENGINE.job().ofInstance(processInstanceKey).withType("test").complete();

    // then: an incident is raised for the failing mapping and NO variable is created —
    // neither 'first' (before the failing one) nor 'third' (after it)
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(incident.getValue()).hasErrorType(ErrorType.IO_MAPPING_ERROR);
    assertThat(incident.getValue().getErrorMessage())
        .contains("Assertion failure on evaluate the expression");

    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                ignored ->
                    RecordingExporter.variableRecords()
                        .withProcessInstanceKey(processInstanceKey)
                        .withName("first")
                        .exists()))
        .isFalse();
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                ignored ->
                    RecordingExporter.variableRecords()
                        .withProcessInstanceKey(processInstanceKey)
                        .withName("third")
                        .exists()))
        .isFalse();

    // when: the missing variable is provided and the incident is resolved
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("missing", 1)).update();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then: all mappings are re-evaluated from the start and applied
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("first")
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("second")
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("third")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldResolveIncidentForInputMappingFailure() {
    // given
    ENGINE.deployment().withXmlResource(PROCESS_INPUT_MAPPING).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    final Record<ProcessInstanceRecordValue> failureEvent =
        RecordingExporter.processInstanceRecords()
            .withElementId("failingTask")
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE
        .variables()
        .ofScope(failureEvent.getValue().getFlowScopeKey())
        .withDocument(VARIABLES)
        .update();
    final Record<IncidentRecordValue> incidentResolvedEvent =
        ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    final Record<ProcessInstanceRecordValue> followUpEvent =
        RecordingExporter.processInstanceRecords()
            .withElementId("failingTask")
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record incidentResolveCommand =
        RecordingExporter.incidentRecords()
            .withIntent(RESOLVE)
            .withRecordKey(incidentEvent.getKey())
            .getFirst();

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentEvent.getKey());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(followUpEvent.getSourceRecordPosition());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(incidentResolvedEvent.getSourceRecordPosition());

    Assertions.assertThat(incidentResolvedEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey())
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(incidentEvent.getValue().getErrorMessage())
        .contains("Assertion failure on evaluate the expression");
  }

  @Test
  public void shouldResolveIncidentForOutputMappingFailure() {
    // given
    ENGINE.deployment().withXmlResource(PROCESS_OUTPUT_MAPPING).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("test")
        .withVariables(VARIABLES_JSON)
        .complete();

    final Record failureEvent =
        RecordingExporter.processInstanceRecords()
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record incidentEvent =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(CREATED)
            .getFirst();

    // when
    ENGINE.variables().ofScope(failureEvent.getKey()).withDocument(VARIABLES).update();
    final Record<IncidentRecordValue> incidentResolvedEvent =
        ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    final Record<ProcessInstanceRecordValue> followUpEvent =
        RecordingExporter.processInstanceRecords()
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withIntent(ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record incidentResolveCommand =
        RecordingExporter.incidentRecords()
            .withIntent(RESOLVE)
            .withRecordKey(incidentEvent.getKey())
            .getFirst();

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentEvent.getKey());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(followUpEvent.getSourceRecordPosition());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(incidentResolvedEvent.getSourceRecordPosition());

    Assertions.assertThat(incidentResolvedEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey())
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(incidentResolvedEvent.getValue().getErrorMessage())
        .contains("Assertion failure on evaluate the expression");
  }

  @Test
  public void shouldCreateNewIncidentAfterResolvedFirstOne() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "failingTask",
                t ->
                    t.zeebeJobType("external")
                        .zeebeInputExpression("assert(foo, foo != null)", "foo")
                        .zeebeInputExpression("assert(bar, bar != null)", "bar"))
            .done();

    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    final Record failureEvent =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("failingTask")
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .getFirst();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    assertThat(incidentEvent.getValue().getErrorMessage())
        .contains("Assertion failure on evaluate the expression");

    // when
    ENGINE.variables().ofScope(failureEvent.getKey()).withDocument(VARIABLES).update();
    final Record<IncidentRecordValue> resolvedEvent =
        ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    assertThat(resolvedEvent.getKey()).isEqualTo(incidentEvent.getKey());

    final Record<IncidentRecordValue> secondIncidentEvent =
        RecordingExporter.incidentRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey)
            .skipUntil(e -> e.getIntent() == RESOLVED)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    Assertions.assertThat(secondIncidentEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());

    assertThat(secondIncidentEvent.getValue().getErrorMessage())
        .contains("Assertion failure on evaluate the expression");
  }

  @Test
  public void shouldResolveIncidentAfterPreviousResolvingFailed() {
    // given
    ENGINE.deployment().withXmlResource(PROCESS_INPUT_MAPPING).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    final Record failureEvent =
        RecordingExporter.processInstanceRecords()
            .withElementId("failingTask")
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record firstIncident =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    ENGINE.variables().ofScope(failureEvent.getKey()).withDocument(new HashMap<>()).update();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(firstIncident.getKey()).resolve();

    final Record<IncidentRecordValue> secondIncident =
        RecordingExporter.incidentRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey)
            .skipUntil(e -> e.getIntent() == RESOLVED)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // when
    ENGINE.variables().ofScope(failureEvent.getKey()).withDocument(VARIABLES).update();

    final Record<IncidentRecordValue> secondResolvedIncident =
        ENGINE.incident().ofInstance(processInstanceKey).withKey(secondIncident.getKey()).resolve();

    // then
    assertThat(secondResolvedIncident.getKey()).isGreaterThan(firstIncident.getKey());
    Assertions.assertThat(secondResolvedIncident.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());

    assertThat(secondResolvedIncident.getValue().getErrorMessage())
        .contains("Assertion failure on evaluate the expression");
  }

  @Test
  public void shouldNotCreateIncidentWhenOutputMappingSourceIsMissing() {
    // given: no assert() - a missing source is lenient and resolves to null
    final var process =
        Bpmn.createExecutableProcess("process-missing-output-source")
            .startEvent()
            .serviceTask(
                "task", t -> t.zeebeJobType("test").zeebeOutputExpression("missing", "bar"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process-missing-output-source").create();
    ENGINE.job().withType("test").ofInstance(processInstanceKey).complete();

    // then
    final var variable =
        RecordingExporter.variableRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withName("bar")
            .getFirst();
    Assertions.assertThat(variable.getValue()).hasValue("null");

    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    records
                        .incidentRecords()
                        .withIntent(CREATED)
                        .withProcessInstanceKey(processInstanceKey)
                        .exists()))
        .isFalse();
  }

  @Test
  public void shouldResolveMultipleIncidents() {
    // given
    ENGINE.deployment().withXmlResource(PROCESS_INPUT_MAPPING).deploy();

    // create and resolve an first incident
    long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();
    Record failureEvent =
        RecordingExporter.processInstanceRecords()
            .withElementId("failingTask")
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE.variables().ofScope(failureEvent.getKey()).withDocument(VARIABLES).update();
    ENGINE.incident().ofInstance(processInstanceKey).resolve();

    // create a second incident
    processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();
    failureEvent =
        RecordingExporter.processInstanceRecords()
            .withElementId("failingTask")
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final long secondIncidentKey =
        RecordingExporter.incidentRecords(CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    ENGINE.variables().ofScope(failureEvent.getKey()).withDocument(VARIABLES).update();
    final Record incidentResolvedEvent = ENGINE.incident().ofInstance(processInstanceKey).resolve();

    // then
    assertThat(incidentResolvedEvent.getKey()).isEqualTo(secondIncidentKey);
  }

  @Test
  public void shouldResolveIncidentIfActivityTerminated() {
    // given
    ENGINE.deployment().withXmlResource(PROCESS_INPUT_MAPPING).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    final Record incidentCreatedEvent =
        RecordingExporter.incidentRecords()
            .withIntent(CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    final Record terminateActivity =
        RecordingExporter.processInstanceRecords()
            .withElementId("failingTask")
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.TERMINATE_ELEMENT)
            .getFirst();

    final Record<IncidentRecordValue> incidentResolvedEvent =
        RecordingExporter.incidentRecords()
            .withIntent(RESOLVED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentCreatedEvent.getKey());

    Assertions.assertThat(incidentResolvedEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(incidentResolvedEvent.getValue().getElementInstanceKey())
        .hasVariableScopeKey(incidentResolvedEvent.getValue().getElementInstanceKey());

    assertThat(incidentResolvedEvent.getValue().getErrorMessage())
        .contains("Assertion failure on evaluate the expression");
  }

  @Test
  public void shouldProcessIncidentsAfterMultipleTerminations() {
    // given
    ENGINE.deployment().withXmlResource(PROCESS_INPUT_MAPPING).deploy();

    // create and cancel instance with incident
    long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // create and cancel instance without incident
    processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process").withVariables(VARIABLES_JSON).create();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // create another instance which creates an incidentprocessInstanceKey =
    processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();
    final Record incidentCreatedEvent =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(RESOLVED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(incidentEvent.getKey()).isEqualTo(incidentCreatedEvent.getKey());

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(incidentEvent.getValue().getElementInstanceKey())
        .hasVariableScopeKey(incidentEvent.getValue().getElementInstanceKey());

    assertThat(incidentEvent.getValue().getErrorMessage())
        .contains("Assertion failure on evaluate the expression");
  }
}
