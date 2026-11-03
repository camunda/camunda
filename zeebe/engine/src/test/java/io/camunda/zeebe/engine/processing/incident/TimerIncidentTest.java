/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static io.camunda.zeebe.engine.processing.incident.IncidentHelper.assertIncidentCreated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Collections;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class TimerIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String ELEMENT_ID = "timer-1";
  private static final String DURATION_VARIABLE = "timer_duration";
  private static final String DURATION_EXPRESSION = "duration(" + DURATION_VARIABLE + ")";
  private static final String CYCLE_EXPRESSION = "cycle(" + DURATION_EXPRESSION + ")";
  private static final String DATETIME_EXPRESSION =
      "date and time(date(" + DURATION_VARIABLE + "),time(\"T00:00:00@UTC\"))";
  private static final String DUE_DATE_OUT_OF_RANGE_MESSAGE =
      "Expected the due date of timer '%s' to be between '-292275055-05-16T16:47:04.192Z' and "
          + "'+292278994-08-17T07:12:55.807Z', but the evaluated timer results in a due date "
          + "outside of this range.";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private static BpmnModelInstance createProcess(final String expression) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .intermediateCatchEvent(ELEMENT_ID, b -> b.timerWithDurationExpression(expression))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance createProcessWithCycle(final String expression) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(
            ELEMENT_ID,
            serviceTaskBuilder ->
                serviceTaskBuilder
                    .zeebeJobType("boundary_timer_test")
                    .boundaryEvent(
                        "boundary-event-1",
                        timerBoundaryEventBuilder ->
                            timerBoundaryEventBuilder
                                .cancelActivity(false)
                                .timerWithCycleExpression(expression)
                                .endEvent("boundary-timer-end-event")))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance createProcessWithTimeDate(final String expression) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(
            ELEMENT_ID,
            serviceTaskBuilder ->
                serviceTaskBuilder
                    .zeebeJobType("boundary_timer_test")
                    .boundaryEvent(
                        "boundary-event-1",
                        timerBoundaryEventBuilder ->
                            timerBoundaryEventBuilder
                                .cancelActivity(false)
                                .timerWithDateExpression(expression)
                                .endEvent("boundary-timer-end-event")))
        .endEvent()
        .done();
  }

  @Test
  public void shouldCreateIncidentIfTimeDateVariableNotFound() {
    // when
    ENGINE.deployment().withXmlResource(createProcessWithTimeDate(DATETIME_EXPRESSION)).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> elementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId(ELEMENT_ID)
            .getFirst();

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Expected result of the expression 'date and time(date(timer_duration),time("T00:00:00@UTC"))' \
            to be one of '[DATE_TIME, STRING]', but was 'NULL'. \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'timer_duration'
            [FUNCTION_INVOCATION_FAILURE] Failed to invoke function 'date': Illegal arguments: 'null'
            [FUNCTION_INVOCATION_FAILURE] Failed to invoke function 'date and time': Illegal arguments: 'null', '00:00:00@UTC'""");
  }

  @Test
  public void shouldCreateIncidentIfTimeDateVariableNotATimeDate() {
    // when
    ENGINE.deployment().withXmlResource(createProcessWithTimeDate(DURATION_VARIABLE)).deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(DURATION_VARIABLE, "not_a_duration_expression")
            .create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> elementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId(ELEMENT_ID)
            .getFirst();

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Invalid date-time format 'not_a_duration_expression' for expression '"
                + DURATION_VARIABLE
                + "'.");
  }

  @Test
  public void shouldCreateIncidentIfDurationVariableNotFound() {
    // when
    ENGINE.deployment().withXmlResource(createProcess(DURATION_EXPRESSION)).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> elementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId(ELEMENT_ID)
            .getFirst();

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Expected result of the expression 'duration(timer_duration)' to be one of '[DURATION, PERIOD, STRING]', but was 'NULL'. \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'timer_duration'
            [FUNCTION_INVOCATION_FAILURE] Failed to invoke function 'duration': Illegal arguments: 'null'""");
  }

  @Test
  public void shouldCreateIncidentIfDurationVariableNotADuration() {
    // when
    ENGINE.deployment().withXmlResource(createProcess(DURATION_VARIABLE)).deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(DURATION_VARIABLE, "not_a_duration_expression")
            .create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> elementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId(ELEMENT_ID)
            .getFirst();

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Invalid duration format 'not_a_duration_expression' for expression '"
                + DURATION_VARIABLE
                + "'.");
  }

  @Test
  public void shouldCreateIncidentIfCycleExpressionCannotBeEvaluated() {
    // when
    ENGINE.deployment().withXmlResource(createProcessWithCycle(CYCLE_EXPRESSION)).deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(DURATION_VARIABLE, "not_a_duration_expression")
            .create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> elementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId(ELEMENT_ID)
            .getFirst();

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Expected result of the expression 'cycle(duration(timer_duration))' to be 'STRING', but was 'NULL'. \
            The evaluation reported the following warnings:
            [FUNCTION_INVOCATION_FAILURE] Failed to invoke function 'duration': Failed to parse duration from 'not_a_duration_expression'
            [FUNCTION_INVOCATION_FAILURE] Failed to invoke function 'cycle': cycle function expected an interval (duration) parameter, but found 'null'""");
  }

  @Test
  public void shouldCreateIncidentForCustomTenant() {
    // when
    final String tenantId = "acme";
    ENGINE
        .deployment()
        .withXmlResource(createProcessWithCycle(CYCLE_EXPRESSION))
        .withTenantId(tenantId)
        .deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(DURATION_VARIABLE, "not_a_duration_expression")
            .withTenantId(tenantId)
            .create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> elementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId(ELEMENT_ID)
            .getFirst();

    assertIncidentCreated(incident, elementInstance, tenantId);
  }

  @Test
  public void shouldResolveIncident() {
    // given
    ENGINE.deployment().withXmlResource(createProcess(DURATION_EXPRESSION)).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE
        .variables()
        .ofScope(incident.getValue().getVariableScopeKey())
        .withDocument(Collections.singletonMap(DURATION_VARIABLE, Duration.ofSeconds(1).toString()))
        .update();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withRecordKey(incident.getValue().getElementInstanceKey())
                .findAny())
        .describedAs("Expect that element was activated")
        .isPresent();
  }

  @Test
  public void shouldNotCreateIncidentIfDueDateIsFarInTheFuture() {
    // given
    ENGINE.deployment().withXmlResource(createProcess(DURATION_VARIABLE)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            // about 317 years: the due date fits into epoch milliseconds, so it must not fail
            .withVariable(DURATION_VARIABLE, "PT9999999990S")
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(ELEMENT_ID)
                .findAny())
        .describedAs("Expect that the timer catch event was activated without an incident")
        .isPresent();

    final Record<TimerRecordValue> timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(timerCreated.getValue().getDueDate())
        .isCloseTo(
            timerCreated.getTimestamp() + Duration.ofSeconds(9_999_999_990L).toMillis(),
            within(Duration.ofMinutes(1).toMillis()));
  }

  @Test
  public void shouldCreateIncidentIfDurationDueDateIsOutOfRange() {
    // given
    ENGINE.deployment().withXmlResource(createProcess(DURATION_VARIABLE)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(DURATION_VARIABLE, "P300000000Y")
            .create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> elementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(ELEMENT_ID)
            .getFirst();

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasVariableScopeKey(elementInstance.getKey())
        .hasErrorMessage(DUE_DATE_OUT_OF_RANGE_MESSAGE.formatted(ELEMENT_ID));
  }

  @Test
  public void shouldCreateIncidentIfDueDateExceedsMaxDateTime() {
    // given
    ENGINE.deployment().withXmlResource(createProcess(DURATION_VARIABLE)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            // beyond the max year of java.time, so it fails with a DateTimeException, not overflow
            .withVariable(DURATION_VARIABLE, "P1000000000Y")
            .create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> elementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(ELEMENT_ID)
            .getFirst();

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasVariableScopeKey(elementInstance.getKey())
        .hasErrorMessage(DUE_DATE_OUT_OF_RANGE_MESSAGE.formatted(ELEMENT_ID));
  }

  @Test
  public void shouldCreateIncidentIfCycleDueDateIsOutOfRange() {
    // given
    ENGINE.deployment().withXmlResource(createProcessWithCycle(DURATION_VARIABLE)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(DURATION_VARIABLE, "R/P300000000Y")
            .create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> elementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(ELEMENT_ID)
            .getFirst();

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasVariableScopeKey(elementInstance.getKey())
        .hasErrorMessage(DUE_DATE_OUT_OF_RANGE_MESSAGE.formatted("boundary-event-1"));
  }

  @Test
  public void shouldCreateIncidentIfTimeDateIsOutOfRange() {
    // given
    ENGINE.deployment().withXmlResource(createProcessWithTimeDate(DURATION_VARIABLE)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(DURATION_VARIABLE, "+300000000-01-01T00:00:00Z")
            .create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> elementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(ELEMENT_ID)
            .getFirst();

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasVariableScopeKey(elementInstance.getKey())
        .hasErrorMessage(DUE_DATE_OUT_OF_RANGE_MESSAGE.formatted("boundary-event-1"));
  }

  @Test
  public void shouldResolveIncidentIfDueDateIsOutOfRange() {
    // given
    ENGINE.deployment().withXmlResource(createProcess(DURATION_VARIABLE)).deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(DURATION_VARIABLE, "P300000000Y")
            .create();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE
        .variables()
        .ofScope(incident.getValue().getVariableScopeKey())
        .withDocument(Collections.singletonMap(DURATION_VARIABLE, Duration.ofSeconds(1).toString()))
        .update();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementInstanceKey(incident.getValue().getElementInstanceKey())
                .findAny())
        .describedAs("Expect that the timer was created after resolving the incident")
        .isPresent();
  }
}
