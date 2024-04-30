/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.records;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.Optional;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ExecutionListenerTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_TYPE = "test_service_task";
  private static final String START_EL_TYPE = "start_execution_listener_job";
  private static final String END_EL_TYPE = "end_execution_listener_job";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  // task related tests: start
  @Test
  public void shouldCreateIncidentForMissingCorrelationKeyOnMessageBoundaryWithServiceTask() {
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

    // complete start EL job again
    completeRecreatedJobWithType(processInstanceKey, START_EL_TYPE);

    // complete service task & end EL jobs
    ENGINE.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_TYPE).complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE).complete();

    // assert the process instance has completed as expected
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
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
    // fail service task job with no retries
    final long failedJobKey =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(SERVICE_TASK_TYPE)
            .withRetries(0)
            .fail()
            .getKey();

    // then: incident created
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasJobKey(failedJobKey)
        .hasErrorType(ErrorType.JOB_NO_RETRIES)
        .hasErrorMessage("No more retries left.");

    // and: resolve first incident
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE)
        .withRetries(1)
        .updateRetries();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();
    // complete service task job (NO need to re-complete start EL job(s))
    ENGINE.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_TYPE).complete();
    // complete end EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE).complete();

    // assert the process instance has completed as expected
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldRecreateELJobAfterResolvingServiceJobExpressionIncident() {
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

    // when: complete start EL job
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

    // then: the first start EL job is recreated
    assertThat(
            jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .onlyEvents()
                .limit(3))
        .extracting(Record::getIntent, r -> r.getValue().getType())
        .containsSequence(
            tuple(JobIntent.CREATED, START_EL_TYPE),
            tuple(JobIntent.COMPLETED, START_EL_TYPE),
            // recreated start EL job
            tuple(JobIntent.CREATED, START_EL_TYPE));
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
                "task",
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

  // task related tests: end

  // process related tests: start
  @Test
  public void shouldCompleteProcessWithMultipleExecutionListeners() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                .zeebeEndExecutionListener(END_EL_TYPE + "_2")
                .startEvent()
                .endEvent()
                .done());

    // when: complete the start execution listener jobs
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

    // complete the end execution listener jobs
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

    // then: EL jobs completed in expected order
    assertExecutionListenerJobsCompletedForElement(
        processInstanceKey,
        PROCESS_ID,
        START_EL_TYPE + "_1",
        START_EL_TYPE + "_2",
        END_EL_TYPE + "_1",
        END_EL_TYPE + "_2");

    // assert the process instance has completed as expected
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldRetryProcessStartExecutionListenerAfterFailure() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeStartExecutionListener(START_EL_TYPE)
                .zeebeEndExecutionListener(END_EL_TYPE)
                .startEvent()
                .endEvent()
                .done());

    // when: fail start EL with retries
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).withRetries(1).fail();
    // complete failed start EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();
    // complete end EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE).complete();

    // then: assert the start EL job was completed after the failure
    assertThat(records().betweenProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.FAILED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    // assert the process instance has completed as expected
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldRetryProcessEndExecutionListenerAfterFailure() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeStartExecutionListener(START_EL_TYPE)
                .zeebeEndExecutionListener(END_EL_TYPE)
                .startEvent()
                .endEvent()
                .done());

    // complete start EL
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();

    // when: fail end EL with retries
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE).withRetries(1).fail();
    // complete failed end EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE).complete();

    // then: assert the end EL job was completed after the failure
    assertThat(records().betweenProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.FAILED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_COMPLETED));

    // assert the process instance has completed as expected
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldRecreateProcessStartExecutionListenerJobsAndProceedAfterIncidentResolution() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeExecutionListener(el -> el.start().type(START_EL_TYPE + "_1"))
                .zeebeExecutionListener(el -> el.start().typeExpression("start_el_2_name_var"))
                .zeebeExecutionListener(el -> el.end().type(END_EL_TYPE))
                .startEvent()
                .manualTask()
                .endEvent()
                .done());

    // when: compete first EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();

    // then: incident for the second EL should be raised due to missing `start_el_2_name_var` var
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
                Expected result of the expression 'start_el_2_name_var' to be 'STRING', but was 'NULL'. \
                The evaluation reported the following warnings:
                [NO_VARIABLE_FOUND] No variable found with name 'start_el_2_name_var'""");

    // fix issue with missing `start_el_2_name_var` variable, required by 2nd start EL
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("start_el_2_name_var", START_EL_TYPE + "_evaluated_2"))
        .update();
    // and: resolve incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();
    // then: complete 1st re-created start EL job
    completeRecreatedJobWithType(processInstanceKey, START_EL_TYPE + "_1");
    // complete 2nd start EL
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_evaluated_2").complete();

    // complete end EL
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE).complete();

    // then: assert that the first start EL job was re-created
    assertThat(
            jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .limit(6)
                .onlyEvents())
        .extracting(r -> r.getValue().getType(), Record::getIntent)
        .containsSequence(
            tuple(START_EL_TYPE + "_1", JobIntent.CREATED),
            tuple(START_EL_TYPE + "_1", JobIntent.COMPLETED),
            // 1st start EL job recreated
            tuple(START_EL_TYPE + "_1", JobIntent.CREATED),
            tuple(START_EL_TYPE + "_1", JobIntent.COMPLETED),
            // last start EL job processing
            tuple(START_EL_TYPE + "_evaluated_2", JobIntent.CREATED),
            tuple(START_EL_TYPE + "_evaluated_2", JobIntent.COMPLETED));

    // assert the process instance has completed as expected
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldRecreateProcessEndExecutionListenerJobsAndProceedAfterIncidentResolution() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeExecutionListener(el -> el.start().type(START_EL_TYPE))
                .zeebeExecutionListener(el -> el.end().type(END_EL_TYPE + "_1"))
                .zeebeExecutionListener(el -> el.end().typeExpression("end_el_2_name_var"))
                .startEvent()
                .manualTask()
                .endEvent()
                .done());

    // compete start EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();

    // when: complete 1st end EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();

    // then: incident for the second EL should be raised due to missing `end_el_2_name_var` var
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
                Expected result of the expression 'end_el_2_name_var' to be 'STRING', but was 'NULL'. \
                The evaluation reported the following warnings:
                [NO_VARIABLE_FOUND] No variable found with name 'end_el_2_name_var'""");

    // fix issue with missing `end_el_3_name_var` variable, required by 2nd EL[start]
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("end_el_2_name_var", END_EL_TYPE + "_evaluated_2"))
        .update();
    // resolve incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then: complete 1st re-created end EL job
    completeRecreatedJobWithType(processInstanceKey, END_EL_TYPE + "_1");
    // complete last end EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_evaluated_2").complete();

    // then: assert that the 1st & 2nd end EL job were re-created
    assertThat(
            jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .limit(8)
                .onlyEvents())
        .extracting(r -> r.getValue().getType(), Record::getIntent)
        .containsSequence(
            // start EL job processing
            tuple(START_EL_TYPE, JobIntent.CREATED),
            tuple(START_EL_TYPE, JobIntent.COMPLETED),
            // end EL jobs processing
            tuple(END_EL_TYPE + "_1", JobIntent.CREATED),
            tuple(END_EL_TYPE + "_1", JobIntent.COMPLETED),
            // re-created 1st end EL job
            tuple(END_EL_TYPE + "_1", JobIntent.CREATED),
            tuple(END_EL_TYPE + "_1", JobIntent.COMPLETED),
            // last end EL job processing after incident resolution
            tuple(END_EL_TYPE + "_evaluated_2", JobIntent.CREATED),
            tuple(END_EL_TYPE + "_evaluated_2", JobIntent.COMPLETED));

    // assert the process instance has completed as expected
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldAllowServiceTaskToAccessVariableFromProcessStartListener() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeStartExecutionListener(START_EL_TYPE)
                .startEvent()
                .serviceTask("task", b -> b.zeebeJobType(SERVICE_TASK_TYPE))
                .endEvent()
                .done());

    // when: complete start EL job with `bar` variable
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(START_EL_TYPE)
        .withVariable("bar", 1)
        .complete();

    // then: assert the variable was created after start EL completion
    assertThat(records().withValueTypes(ValueType.JOB, ValueType.VARIABLE).onlyEvents().limit(3))
        .extracting(Record::getValueType, Record::getIntent)
        .containsExactly(
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.VARIABLE, VariableIntent.CREATED));

    // `bar` variable accessible in service task job
    final Optional<JobRecordValue> jobActivated =
        ENGINE.jobs().withType(SERVICE_TASK_TYPE).activate().getValue().getJobs().stream()
            .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
            .findFirst();

    assertThat(jobActivated)
        .hasValueSatisfying(job -> assertThat(job.getVariables()).contains(entry("bar", 1)));
  }

  @Test
  public void shouldEvaluateExpressionsForProcessExecutionListeners() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeExecutionListener(el -> el.start().type(START_EL_TYPE + "_1"))
                .zeebeExecutionListener(
                    el ->
                        el.start().typeExpression("listenerNameVar").retriesExpression("elRetries"))
                .zeebeExecutionListener(
                    el -> el.end().type(END_EL_TYPE).retriesExpression("elRetries + 5"))
                .startEvent()
                .endEvent()
                .done());

    // complete 1st start EL with variables that will be used in expressions by the 2nd stat EL
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(START_EL_TYPE + "_1")
        .withVariables(Map.of("elRetries", 6, "listenerNameVar", START_EL_TYPE + "evaluated_2"))
        .complete();

    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "evaluated_2").complete();

    // complete end EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE).complete();

    // then: assert the EL job completed with expected evaluated `type` and `retries` props
    assertThat(
            jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .onlyEvents()
                .limit(3))
        .extracting(r -> r.getValue().getType(), r -> r.getValue().getRetries())
        .containsExactly(
            tuple(START_EL_TYPE + "_1", 3),
            tuple(START_EL_TYPE + "evaluated_2", 6),
            tuple(END_EL_TYPE, 11));
  }

  // process related tests: end

  // test util methods
  private static long createProcessInstance(final BpmnModelInstance modelInstance) {
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    return ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
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

  private static void assertVariable(
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

  static void assertExecutionListenerJobsCompletedForElement(
      final long processInstanceKey, final String elementId, final String... elJobTypes) {
    assertThat(
            RecordingExporter.jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .withElementId(elementId)
                .limit(elJobTypes.length))
        .extracting(r -> r.getValue().getType())
        .containsExactly(elJobTypes);
  }
}
