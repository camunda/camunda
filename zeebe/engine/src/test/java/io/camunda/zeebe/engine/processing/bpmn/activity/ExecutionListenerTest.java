/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.engine.processing.job.JobThrowErrorProcessor.ERROR_REJECTION_MESSAGE;
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
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
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
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ExecutionListenerTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  static final String PROCESS_ID = "process";
  static final String SUB_PROCESS_ID = "sub_".concat(PROCESS_ID);
  static final String START_EL_TYPE = "start_execution_listener_job";
  static final String END_EL_TYPE = "end_execution_listener_job";
  static final String SERVICE_TASK_TYPE = "test_service_task";

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

  // task related tests: end

  @Test
  public void shouldRejectErrorThrowingFromExecutionListenerJob() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    "service_task",
                    t ->
                        t.zeebeJobType(SERVICE_TASK_TYPE)
                            .zeebeStartExecutionListener(START_EL_TYPE))
                .boundaryEvent("error_boundary", b -> b.error("err"))
                .endEvent("error_end")
                .moveToActivity("service_task")
                .endEvent("main_end")
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: attempt to throw a BPMN error from the start execution listener job
    final Record<JobRecordValue> error =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(START_EL_TYPE)
            .withErrorCode("err")
            .throwError();

    // then: verify the rejection of the BPMN error
    final String expectedRejectionReason =
        String.format(
            ERROR_REJECTION_MESSAGE,
            JobKind.EXECUTION_LISTENER,
            error.getKey(),
            START_EL_TYPE,
            processInstanceKey);
    assertThat(jobRecords().withRecordType(RecordType.COMMAND_REJECTION).getFirst())
        .extracting(
            r -> r.getValue().getType(), Record::getRejectionType, Record::getRejectionReason)
        .containsExactly(START_EL_TYPE, RejectionType.INVALID_STATE, expectedRejectionReason);
  }

  // process related tests: start
  @Test
  public void shouldCompleteProcessWithMultipleExecutionListeners() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
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
            ENGINE,
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
            ENGINE,
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
            ENGINE,
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
            ENGINE,
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
            ENGINE,
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
            ENGINE,
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
  // subprocess related tests: start

  @Test
  public void shouldCompleteEmbeddedSubProcessWithMultipleExecutionListeners() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeStartExecutionListener(START_EL_TYPE)
                .zeebeEndExecutionListener(END_EL_TYPE)
                .startEvent()
                .manualTask()
                .subProcess(
                    SUB_PROCESS_ID,
                    s ->
                        s.zeebeStartExecutionListener(START_EL_TYPE + "_sub")
                            .zeebeEndExecutionListener(END_EL_TYPE + "_sub")
                            .embeddedSubProcess()
                            .startEvent()
                            .manualTask()
                            .endEvent())
                .manualTask()
                .endEvent()
                .done());

    // when: complete process start EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();
    // complete sub-process start/end EL jobs
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_sub").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_sub").complete();

    // complete process end EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE).complete();

    // then: EL jobs completed in expected order
    assertThat(
            RecordingExporter.jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .filter(
                    r -> Set.of(PROCESS_ID, SUB_PROCESS_ID).contains(r.getValue().getElementId()))
                .limit(4))
        .extracting(r -> r.getValue().getType())
        .containsExactly(START_EL_TYPE, START_EL_TYPE + "_sub", END_EL_TYPE + "_sub", END_EL_TYPE);

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
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldAccessVariableFromEmbeddedSubProcessStartListenerInSubProcessServiceTask() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    SUB_PROCESS_ID,
                    s ->
                        s.zeebeStartExecutionListener(START_EL_TYPE + "_sub")
                            .embeddedSubProcess()
                            .startEvent()
                            .serviceTask("task", b -> b.zeebeJobType(SERVICE_TASK_TYPE + "_sub"))
                            .endEvent())
                .manualTask()
                .endEvent()
                .done());

    // when: complete subprocess start EL job with variables
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(START_EL_TYPE + "_sub")
        .withVariable("baz", 42)
        .complete();

    // then: assert the variable was created after start EL completion
    assertThat(records().withValueTypes(ValueType.JOB, ValueType.VARIABLE).onlyEvents().limit(3))
        .extracting(Record::getValueType, Record::getIntent)
        .containsExactly(
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.VARIABLE, VariableIntent.CREATED));

    // `baz` variable accessible in subprocess service task job
    final Optional<JobRecordValue> jobActivated =
        ENGINE.jobs().withType(SERVICE_TASK_TYPE + "_sub").activate().getValue().getJobs().stream()
            .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
            .findFirst();

    assertThat(jobActivated)
        .hasValueSatisfying(job -> assertThat(job.getVariables()).contains(entry("baz", 42)));
  }

  @Test
  public void shouldCompleteCallActivitySubProcessWithMultipleExecutionListeners() {
    // given
    final var childProcess =
        Bpmn.createExecutableProcess(SUB_PROCESS_ID)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType(SERVICE_TASK_TYPE + "_sub"))
            .endEvent()
            .done();

    final var parentProcess =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeStartExecutionListener(START_EL_TYPE)
            .startEvent()
            .callActivity(SUB_PROCESS_ID, c -> c.zeebeProcessId(SUB_PROCESS_ID))
            .zeebeStartExecutionListener(START_EL_TYPE + "_sub")
            .zeebeEndExecutionListener(END_EL_TYPE + "_sub_1")
            .zeebeEndExecutionListener(END_EL_TYPE + "_sub_2")
            .manualTask()
            .endEvent()
            .done();

    ENGINE
        .deployment()
        .withXmlResource("parent.xml", parentProcess)
        .withXmlResource("child.xml", childProcess)
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: complete parent process start EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();
    // complete sub-process EL and service task jobs
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_sub").complete();
    completeJobFromSubProcess(SERVICE_TASK_TYPE + "_sub");
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_sub_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_sub_2").complete();

    // then: jobs completed in expected order
    assertThat(RecordingExporter.jobRecords().withIntent(JobIntent.COMPLETED).limit(5))
        .extracting(r -> r.getValue().getElementId(), r -> r.getValue().getType())
        .containsExactly(
            tuple(PROCESS_ID, START_EL_TYPE),
            tuple(SUB_PROCESS_ID, START_EL_TYPE + "_sub"),
            tuple("task", SERVICE_TASK_TYPE + "_sub"),
            tuple(SUB_PROCESS_ID, END_EL_TYPE + "_sub_1"),
            tuple(SUB_PROCESS_ID, END_EL_TYPE + "_sub_2"));

    // assert the process instance with call activity completed as expected
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
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteEventSubProcessWithMultipleExecutionListeners() {
    final var messageName = "subprocess-event";

    final String messageSubprocessId = "message-event-subprocess";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess(
                    messageSubprocessId,
                    sub ->
                        sub.zeebeStartExecutionListener(START_EL_TYPE + "_sub_1")
                            .zeebeStartExecutionListener(START_EL_TYPE + "_sub_2")
                            .zeebeEndExecutionListener(END_EL_TYPE + "_sub")
                            .startEvent("startEvent_sub")
                            .interrupting(false)
                            .message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key"))
                            .serviceTask(
                                "task_sub", t -> t.zeebeJobType(SERVICE_TASK_TYPE + "_sub"))
                            .endEvent("endEvent_sub"))
                .startEvent("startEvent")
                .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                .endEvent("endEvent")
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withStartInstruction("task")
            .withVariable("key", "key-1")
            .create();

    // when
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    ENGINE.message().withName(messageName).withCorrelationKey("key-1").publish();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    ENGINE.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_TYPE).complete();

    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_sub_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_sub_2").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_TYPE + "_sub").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_sub").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            record -> record.getValue().getElementId(),
            record -> record.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSequence(
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task", BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ACTIVATE_ELEMENT))
        .containsSubsequence(
            tuple(
                messageSubprocessId,
                BpmnElementType.EVENT_SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                messageSubprocessId,
                BpmnElementType.EVENT_SUB_PROCESS,
                ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(
                messageSubprocessId,
                BpmnElementType.EVENT_SUB_PROCESS,
                ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(
                messageSubprocessId,
                BpmnElementType.EVENT_SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                "task_sub", BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                messageSubprocessId,
                BpmnElementType.EVENT_SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(
                messageSubprocessId,
                BpmnElementType.EVENT_SUB_PROCESS,
                ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(
                messageSubprocessId,
                BpmnElementType.EVENT_SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  // subprocess related tests: end

  // test util methods
  static long createProcessInstance(
      final EngineRule engineRule, final BpmnModelInstance modelInstance) {
    return createProcessInstance(engineRule, modelInstance, Collections.emptyMap());
  }

  static long createProcessInstance(
      final EngineRule engineRule,
      final BpmnModelInstance modelInstance,
      final Map<String, Object> variables) {
    engineRule.deployment().withXmlResource(modelInstance).deploy();
    return engineRule
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withVariables(variables)
        .create();
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

  private void completeJobFromSubProcess(final String jobType) {
    ENGINE
        .jobs()
        .withType(jobType)
        .activate()
        .getValue()
        .getJobKeys()
        .forEach(jobKey -> ENGINE.job().withKey(jobKey).complete());
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
