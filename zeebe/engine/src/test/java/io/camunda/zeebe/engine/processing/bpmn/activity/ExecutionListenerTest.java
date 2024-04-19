/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ExecutionListenerTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_TYPE = "test_service_task";
  private static final String START_EL_TYPE = "start_execution_listener";
  private static final String END_EL_TYPE = "end_execution_listener";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void
      shouldCreateIncidentWhenCorrelationKeyNotProvidedBeforeProcessingTaskWithMessageBoundaryEvent() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeStartExecutionListener(START_EL_TYPE)
                        .zeebeEndExecutionListener(END_EL_TYPE))
            .boundaryEvent("boundary_event")
            .message(b -> b.name("service_task_event").zeebeCorrelationKeyExpression("order_id"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: complete EL[start] job
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();
    final Record<IncidentRecordValue> firstIncident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .skip(0)
            .getFirst();

    // then: incident created
    Assertions.assertThat(firstIncident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Failed to extract the correlation key for 'order_id': The value must be either a string or a number, but was 'NULL'. "
                + "The evaluation reported the following warnings:\n"
                + "[NO_VARIABLE_FOUND] No variable found with name 'order_id'");

    // fix issue with missing `correlationKey` variable
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("order_id", 123)).update();

    // resolve incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(firstIncident.getKey()).resolve();

    // complete EL[start] job again
    completeRecreatedJobWithType(processInstanceKey, START_EL_TYPE);

    // Job for service task should be created
    verifyJobCreationThenComplete(processInstanceKey, 2, SERVICE_TASK_TYPE, JobKind.BPMN_ELEMENT);

    // job for EL[end] should be created
    assertJobState(
        processInstanceKey, 3, END_EL_TYPE, JobIntent.CREATED, JobKind.EXECUTION_LISTENER);
  }

  @Test
  public void shouldCreateIncidentWhenServiceTaskWithExecutionListenersFailed() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeStartExecutionListener(START_EL_TYPE)
                        .zeebeEndExecutionListener(END_EL_TYPE))
            .endEvent("end")
            .done();
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: complete EL[start] job
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();
    // fail service task job
    ENGINE.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_TYPE).withRetries(0).fail();

    final Record<IncidentRecordValue> firstIncident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // and: resolve first incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(firstIncident.getKey()).resolve();
    // complete service task job (NO need to re-complete EL[start] job(s))
    ENGINE.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_TYPE).complete();

    // then: EL[end] created
    assertJobState(
        processInstanceKey, 2, END_EL_TYPE, JobIntent.CREATED, JobKind.EXECUTION_LISTENER);
  }

  @Test
  public void
      shouldReCreateFirstElJobAfterResolvingIncidentCreatedDuringResolvingServiceJobExpressions() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobTypeExpression("=service_task_job_name_var + \"_type\"")
                        .zeebeStartExecutionListener(START_EL_TYPE))
            .endEvent("end")
            .done();
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: complete EL[start] job
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();

    // an incident is created due to the unresolved job type expression in the service task
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
               Expected result of the expression 'service_task_job_name_var + "_type"' to be \
               'STRING', but was 'NULL'. The evaluation reported the following warnings:
               [NO_VARIABLE_FOUND] No variable found with name 'service_task_job_name_var'
               [INVALID_TYPE] Can't add '"_type"' to 'null'""");

    // and: resolve incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then: the first EL[start] job is re-created
    assertJobState(
        processInstanceKey, 1, START_EL_TYPE, JobIntent.CREATED, JobKind.EXECUTION_LISTENER);
  }

  @Test
  public void shouldCompleteExecutionListenerJobWithVariablesMerging() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .zeebeOutput("=\"aValue\"", "startEventVar")
            .serviceTask(
                "serviceTask",
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeStartExecutionListener(START_EL_TYPE)
                        .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                        .zeebeEndExecutionListener(END_EL_TYPE + "_2"))
            .zeebeInput("=\"bValue\"", "serviceTaskVar")
            .zeebeOutput("=startEventVar + \"+\" + serviceTaskVar", "mergedVars")
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // Validate initial variable creation
    assertVariable(processInstanceKey, VariableIntent.CREATED, "startEventVar", "\"aValue\"");
    assertVariable(processInstanceKey, VariableIntent.CREATED, "serviceTaskVar", "\"bValue\"");

    // when: Completing the start execution listener job with new variables
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(START_EL_TYPE)
        .withVariables(
            Map.of("newVarFromStartListener", "cValue", "serviceTaskVar", "bValueUpdated"))
        .complete();

    // then: Validate variables updated or created by the start execution listener
    assertVariable(
        processInstanceKey, VariableIntent.UPDATED, "serviceTaskVar", "\"bValueUpdated\"");
    assertVariable(
        processInstanceKey, VariableIntent.CREATED, "newVarFromStartListener", "\"cValue\"");

    // when: Completing the service task job
    ENGINE.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_TYPE).complete();

    // and: Completing the first EL[end] job with updated start event variable
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(END_EL_TYPE + "_1")
        .withVariable("startEventVar", "aValueUpdated")
        .complete();

    // then: Validate start event variable updated by the end execution listener
    assertVariable(
        processInstanceKey, VariableIntent.UPDATED, "startEventVar", "\"aValueUpdated\"");

    // when: Completing the second end execution listener job
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

    // then: Validate merged variables after all listeners and tasks are completed
    assertVariable(
        processInstanceKey, VariableIntent.CREATED, "mergedVars", "\"aValue+bValueUpdated\"");
  }

  @Test
  public void shouldCreateUnhandledErrorEventIncidentAfterThrowingErrorFromExecutionListenerJob() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "A",
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeStartExecutionListener(START_EL_TYPE)
                        .zeebeEndExecutionListener(END_EL_TYPE))
            .boundaryEvent("errorBoundary", b -> b.error("err"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(modelInstance).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(START_EL_TYPE)
        .withErrorCode("err")
        .throwError();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
        .hasErrorMessage(
            "Expected to throw an error event with the code 'err', but it was not caught. No error events are available in the scope.");
  }

  private static void completeRecreatedJobWithType(
      final long processInstanceKey, final String jobType) {
    final long jobKey =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(jobType)
            .skip(1)
            .getFirst()
            .getKey();
    ENGINE.job().ofInstance(processInstanceKey).withKey(jobKey).complete();
  }

  private void assertVariable(
      final long processInstanceKey,
      final VariableIntent intent,
      final String varName,
      final String expectedVarValue) {
    final Record<VariableRecordValue> variableRecordValueRecord =
        RecordingExporter.variableRecords(intent)
            .withProcessInstanceKey(processInstanceKey)
            .withName(varName)
            .getFirst();

    Assertions.assertThat(variableRecordValueRecord.getValue())
        .hasName(varName)
        .hasValue(expectedVarValue);
  }

  private void assertJobState(
      final long processInstanceKey,
      final long jobIndex,
      final String expectedJobType,
      final JobIntent expectedJobIntent,
      final JobKind expectedJobKind) {
    final Record<ProcessInstanceRecordValue> activatingJob =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    final Record<JobRecordValue> jobRecord =
        jobRecords(expectedJobIntent)
            .withProcessInstanceKey(processInstanceKey)
            .skip(jobIndex)
            .getFirst();

    Assertions.assertThat(jobRecord.getValue())
        .hasElementInstanceKey(activatingJob.getKey())
        .hasElementId(activatingJob.getValue().getElementId())
        .hasProcessDefinitionKey(activatingJob.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(activatingJob.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(activatingJob.getValue().getVersion())
        .hasJobKind(expectedJobKind)
        .hasType(expectedJobType);
  }

  private void verifyJobCreationThenComplete(
      final long processInstanceKey,
      final long jobIndex,
      final String jobType,
      final JobKind jobKind) {
    // given: assert job created
    assertJobState(processInstanceKey, jobIndex, jobType, JobIntent.CREATED, jobKind);

    // when: complete job
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).complete();

    // then: assert job completed
    assertJobState(processInstanceKey, jobIndex, jobType, JobIntent.COMPLETED, jobKind);
  }
}
