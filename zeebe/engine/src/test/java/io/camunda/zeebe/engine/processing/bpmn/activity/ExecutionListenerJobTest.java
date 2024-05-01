/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.protocol.record.intent.JobIntent.FAILED;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValueAssert;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.ProcessInstanceRecordStream;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ExecutionListenerJobTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_TYPE = "test_service_task";
  private static final String START_EL_TYPE = "start_execution_listener";
  private static final String END_EL_TYPE = "end_execution_listener";

  private static final BpmnModelInstance SIMPLE_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask(
              "task",
              t -> t.zeebeJobType(SERVICE_TASK_TYPE).zeebeStartExecutionListener(START_EL_TYPE))
          .endEvent()
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCompleteServiceTaskWithExecutionListeners() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .zeebeStartExecutionListener(START_EL_TYPE + "_1")
            .zeebeStartExecutionListener(START_EL_TYPE + "_2")
            .zeebeEndExecutionListener(END_EL_TYPE)
            .endEvent()
            .done();
    // when
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    verifyJobCreationThenComplete(
        processInstanceKey, 0, START_EL_TYPE + "_1", JobKind.EXECUTION_LISTENER);
    verifyJobCreationThenComplete(
        processInstanceKey, 1, START_EL_TYPE + "_2", JobKind.EXECUTION_LISTENER);
    verifyJobCreationThenComplete(processInstanceKey, 2, SERVICE_TASK_TYPE, JobKind.BPMN_ELEMENT);
    verifyJobCreationThenComplete(processInstanceKey, 3, END_EL_TYPE, JobKind.EXECUTION_LISTENER);

    final ProcessInstanceRecordStream processInstanceRecordStream =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limitToProcessInstanceCompleted();
    assertThat(processInstanceRecordStream)
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldRetryExecutionListener() {
    // given
    ENGINE.deployment().withXmlResource(SIMPLE_PROCESS).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).withRetries(1).fail();
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();

    // then: service task job should be created
    assertJobState(
        processInstanceKey, 1, SERVICE_TASK_TYPE, JobIntent.CREATED, JobKind.BPMN_ELEMENT);
  }

  @Test
  public void shouldCreateIncidentForExecutionListenerWhenNoRetriesLeft() {
    // given
    ENGINE.deployment().withXmlResource(SIMPLE_PROCESS).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: fail EL[start] job
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).withRetries(0).fail();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.JOB_NO_RETRIES)
        .hasErrorMessage("No more retries left.");

    // resolve incident & complete EL[start] job
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(START_EL_TYPE)
        .withRetries(1)
        .updateRetries();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();

    // then: service task job should be created
    assertJobState(
        processInstanceKey, 1, SERVICE_TASK_TYPE, JobIntent.CREATED, JobKind.BPMN_ELEMENT);
  }

  @Test
  public void shouldProceedWithRemainingExecutionListenersAfterResolvingIncidentForEndEL() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeStartExecutionListener(START_EL_TYPE)
                        .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                        .zeebeEndExecutionListener(END_EL_TYPE + "_2")
                        .zeebeEndExecutionListener(END_EL_TYPE + "_3"))
            .endEvent("end")
            .done();
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // complete EL[start], service task, and 1st EL[end] jobs
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_TYPE).complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();

    // when: fail 2nd EL[end] job
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").withRetries(0).fail();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.JOB_NO_RETRIES)
        .hasErrorMessage("No more retries left.");

    // and: resolve incident & complete 2nd EL[end] job
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(END_EL_TYPE + "_2")
        .withRetries(1)
        .updateRetries();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

    // then: 3rd EL[end] job should be created
    verifyJobCreationThenComplete(
        processInstanceKey, 4, END_EL_TYPE + "_3", JobKind.EXECUTION_LISTENER);
  }

  @Test
  public void
      shouldCreateIncidentDuringEvaluatingServiceTaskInputMappingsAndResumeWithStartELsAfterResolving() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeInputExpression("assert(some_var, some_var != null)", "o_var_1")
                        .zeebeStartExecutionListener(START_EL_TYPE)
                        .zeebeEndExecutionListener(END_EL_TYPE))
            .endEvent("end")
            .done();
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then: incident created
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasErrorMessage(
            """
                Assertion failure on evaluate the expression '{o_var_1:assert(some_var, some_var != null)}': \
                The condition is not fulfilled The evaluation reported the following warnings:
                [NO_VARIABLE_FOUND] No variable found with name 'some_var'
                [NO_VARIABLE_FOUND] No variable found with name 'some_var'
                [ASSERT_FAILURE] The condition is not fulfilled""");

    // fix issue with missing `some_var` variable
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("some_var", "foo_bar"))
        .update();
    // resolve incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then: job for the 1st EL[start] should be created
    verifyJobCreationThenComplete(processInstanceKey, 0, START_EL_TYPE, JobKind.EXECUTION_LISTENER);
    verifyJobCreationThenComplete(processInstanceKey, 1, SERVICE_TASK_TYPE, JobKind.BPMN_ELEMENT);
    verifyJobCreationThenComplete(processInstanceKey, 2, END_EL_TYPE, JobKind.EXECUTION_LISTENER);
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void
      shouldCreateIncidentDuringEvaluatingServiceTaskOutputMappingsAndResumeWithEndELsAfterResolving() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeOutputExpression("assert(some_var, some_var != null)", "o_var_1")
                        .zeebeStartExecutionListener(START_EL_TYPE)
                        .zeebeEndExecutionListener(END_EL_TYPE))
            .endEvent("end")
            .done();
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // complete EL[start] and service task jobs
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_TYPE).complete();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasErrorMessage(
            """
                Assertion failure on evaluate the expression '{o_var_1:assert(some_var, some_var != null)}': \
                The condition is not fulfilled The evaluation reported the following warnings:
                [NO_VARIABLE_FOUND] No variable found with name 'some_var'
                [NO_VARIABLE_FOUND] No variable found with name 'some_var'
                [ASSERT_FAILURE] The condition is not fulfilled""");

    // fix issue with missing `some_var` variable
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("some_var", "foo_bar"))
        .update();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // job for EL[end] should be created
    verifyJobCreationThenComplete(processInstanceKey, 2, END_EL_TYPE, JobKind.EXECUTION_LISTENER);
  }

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
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE)
        .withRetries(1)
        .updateRetries();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(firstIncident.getKey()).resolve();
    // complete service task job (NO need to re-complete EL[start] job(s))
    ENGINE.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_TYPE).complete();

    // then: EL[end] created
    assertJobState(
        processInstanceKey, 2, END_EL_TYPE, JobIntent.CREATED, JobKind.EXECUTION_LISTENER);
  }

  @Test
  public void shouldRecurFailedExecutionListenerJobAfterBackoff() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t -> t.zeebeJobType(SERVICE_TASK_TYPE).zeebeStartExecutionListener(START_EL_TYPE))
            .endEvent("end")
            .done();
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: fail service task job
    final Duration backOff = Duration.ofMinutes(30);
    final Record<JobRecordValue> failedStartElJobRecord =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(START_EL_TYPE)
            .withBackOff(backOff)
            .withRetries(2)
            .fail();

    Assertions.assertThat(failedStartElJobRecord).hasRecordType(RecordType.EVENT).hasIntent(FAILED);

    ENGINE.increaseTime(backOff);

    // verify that our job recurred after backoff
    final Record<JobRecordValue> recurredJob =
        jobRecords(JobIntent.RECURRED_AFTER_BACKOFF).withType(START_EL_TYPE).getFirst();
    assertThat(recurredJob.getKey()).isEqualTo(failedStartElJobRecord.getKey());

    // when: complete recreated job
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();

    // job for service task should be created
    assertJobState(
        processInstanceKey, 1, SERVICE_TASK_TYPE, JobIntent.CREATED, JobKind.BPMN_ELEMENT);
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
  public void shouldAccessJobVariablesInEndListener() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                .zeebeEndExecutionListener(END_EL_TYPE)
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE)
        .withVariable("x", 1)
        .complete();

    // then
    final Optional<JobRecordValue> jobActivated =
        ENGINE.jobs().withType(END_EL_TYPE).activate().getValue().getJobs().stream()
            .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
            .findFirst();

    assertThat(jobActivated).isPresent();
    assertThat(jobActivated.get().getVariables()).contains(entry("x", 1));
  }

  @Test
  public void shouldCompleteExecutionListenerJobWithVariablesMerging() {
    // given: a simple process with a service task and execution listeners
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
  public void shouldEvaluateExpressionsForExecutionListenerJobs() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeExecutionListener(
                            b ->
                                b.start()
                                    .typeExpression("listenerNameVar")
                                    .retriesExpression("elRetries"))
                        .zeebeExecutionListener(
                            b -> b.start().type(START_EL_TYPE + "_2").retries("5"))
                        .zeebeExecutionListener(
                            b -> b.end().type(END_EL_TYPE).retriesExpression("elRetries + 5")))
            .endEvent("end")
            .done();

    // when
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("elRetries", 6, "listenerNameVar", START_EL_TYPE + "_1"))
            .create();

    // then
    verifyJobCreationThenComplete(
            processInstanceKey, 0, START_EL_TYPE + "_1", JobKind.EXECUTION_LISTENER)
        .hasRetries(6);
    verifyJobCreationThenComplete(
            processInstanceKey, 1, START_EL_TYPE + "_2", JobKind.EXECUTION_LISTENER)
        .hasRetries(5);
    verifyJobCreationThenComplete(processInstanceKey, 2, SERVICE_TASK_TYPE, JobKind.BPMN_ELEMENT)
        .hasRetries(3);
    verifyJobCreationThenComplete(processInstanceKey, 3, END_EL_TYPE, JobKind.EXECUTION_LISTENER)
        .hasRetries(11);
  }

  @Test
  public void shouldRecreateStartExecutionListenerJobsAndProceedAfterIncidentResolution() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeExecutionListener(b -> b.start().type(START_EL_TYPE + "_1"))
                        .zeebeExecutionListener(b -> b.start().typeExpression("listenerNameVar"))
                        .zeebeExecutionListener(b -> b.start().type(START_EL_TYPE + "_3"))
                        .zeebeExecutionListener(b -> b.end().type(END_EL_TYPE)))
            .endEvent("end")
            .done();

    // when
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    verifyJobCreationThenComplete(
        processInstanceKey, 0, START_EL_TYPE + "_1", JobKind.EXECUTION_LISTENER);

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression 'listenerNameVar' to be 'STRING', but was 'NULL'. "
                + "The evaluation reported the following warnings:\n"
                + "[NO_VARIABLE_FOUND] No variable found with name 'listenerNameVar'");

    // fix issue with missing `listenerNameVar` variable, required by 2nd EL[start]
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("listenerNameVar", START_EL_TYPE + "_2"))
        .update();
    // and
    // resolve incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then: the first EL[start] job is re-created
    assertJobState(
        processInstanceKey, 1, START_EL_TYPE + "_1", JobIntent.CREATED, JobKind.EXECUTION_LISTENER);
    // complete 1st re-created EL[start] job
    completeRecreatedJobWithType(processInstanceKey, START_EL_TYPE + "_1");
    verifyJobCreationThenComplete(
        processInstanceKey, 2, START_EL_TYPE + "_2", JobKind.EXECUTION_LISTENER);
    verifyJobCreationThenComplete(
        processInstanceKey, 3, START_EL_TYPE + "_3", JobKind.EXECUTION_LISTENER);
    verifyJobCreationThenComplete(processInstanceKey, 4, SERVICE_TASK_TYPE, JobKind.BPMN_ELEMENT);
    verifyJobCreationThenComplete(processInstanceKey, 5, END_EL_TYPE, JobKind.EXECUTION_LISTENER);
  }

  @Test
  public void shouldRecreateEndExecutionListenerJobsAndProceedAfterIncidentResolution() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeExecutionListener(b -> b.start().type(START_EL_TYPE))
                        .zeebeExecutionListener(b -> b.end().type(END_EL_TYPE + "_1"))
                        .zeebeExecutionListener(b -> b.end().type(END_EL_TYPE + "_2"))
                        .zeebeExecutionListener(b -> b.end().typeExpression("listenerNameVar")))
            .endEvent("end")
            .done();

    // when
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    verifyJobCreationThenComplete(processInstanceKey, 0, START_EL_TYPE, JobKind.EXECUTION_LISTENER);
    verifyJobCreationThenComplete(processInstanceKey, 1, SERVICE_TASK_TYPE, JobKind.BPMN_ELEMENT);
    verifyJobCreationThenComplete(
        processInstanceKey, 2, END_EL_TYPE + "_1", JobKind.EXECUTION_LISTENER);
    verifyJobCreationThenComplete(
        processInstanceKey, 3, END_EL_TYPE + "_2", JobKind.EXECUTION_LISTENER);

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression 'listenerNameVar' to be 'STRING', but was 'NULL'. "
                + "The evaluation reported the following warnings:\n"
                + "[NO_VARIABLE_FOUND] No variable found with name 'listenerNameVar'");

    // fix issue with missing `listenerNameVar` variable, required by 2nd EL[start]
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("listenerNameVar", END_EL_TYPE + "_3"))
        .update();
    // and
    // resolve incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then: the first EL[end] job is re-created
    assertJobState(
        processInstanceKey, 4, END_EL_TYPE + "_1", JobIntent.CREATED, JobKind.EXECUTION_LISTENER);
    // complete 1st & 2nd re-created EL[end] jobs
    completeRecreatedJobWithType(processInstanceKey, END_EL_TYPE + "_1");
    completeRecreatedJobWithType(processInstanceKey, END_EL_TYPE + "_2");
    verifyJobCreationThenComplete(
        processInstanceKey, 6, END_EL_TYPE + "_3", JobKind.EXECUTION_LISTENER);
  }

  private static void completeRecreatedJobWithType(
      final long processInstanceKey, final String jobType) {
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
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

  private JobRecordValueAssert assertJobState(
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
        RecordingExporter.jobRecords(expectedJobIntent)
            .withProcessInstanceKey(processInstanceKey)
            .skip(jobIndex)
            .getFirst();

    return Assertions.assertThat(jobRecord.getValue())
        .hasElementInstanceKey(activatingJob.getKey())
        .hasElementId(activatingJob.getValue().getElementId())
        .hasProcessDefinitionKey(activatingJob.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(activatingJob.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(activatingJob.getValue().getVersion())
        .hasJobKind(expectedJobKind)
        .hasType(expectedJobType);
  }

  private JobRecordValueAssert verifyJobCreationThenComplete(
      final long processInstanceKey,
      final long jobIndex,
      final String jobType,
      final JobKind jobKind) {
    // given: assert job created
    assertJobState(processInstanceKey, jobIndex, jobType, JobIntent.CREATED, jobKind);

    // when: complete job
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).complete();

    // then: assert job completed
    return assertJobState(processInstanceKey, jobIndex, jobType, JobIntent.COMPLETED, jobKind);
  }
}
