/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution;

import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.END_EL_TYPE;
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.PROCESS_ID;
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.SERVICE_TASK_TYPE;
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.START_EL_TYPE;
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.assertExecutionListenerJobsCompletedForElement;
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.completeRecreatedJobWithType;
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.createProcessInstance;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.records;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
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
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.Optional;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ExecutionListenerProcessInstanceTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

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
    completeRecreatedJobWithType(ENGINE, processInstanceKey, START_EL_TYPE + "_1");
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
    completeRecreatedJobWithType(ENGINE, processInstanceKey, END_EL_TYPE + "_1");
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

  @Test
  public void shouldCancelActiveStartElJobAfterProcessInstanceCancellation() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeStartExecutionListener(START_EL_TYPE)
                .startEvent()
                .endEvent()
                .done());
    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(START_EL_TYPE)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then: start EL job should be canceled
    assertThat(
            jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .onlyEvents()
                .getFirst())
        .extracting(r -> r.getValue().getType())
        .isEqualTo(START_EL_TYPE);
  }
}
