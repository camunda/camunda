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
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.createProcessInstance;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.records;
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
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ExecutionListenerCancelTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  static final String CANCEL_EL_TYPE = "cancel_execution_listener_job";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateCancelElJobWhenProcessInstanceIsCanceled() {
    // given: process with a cancel execution listener
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                .endEvent()
                .done());

    // wait for the service task to be activated
    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(SERVICE_TASK_TYPE)
        .await();

    // when: cancel the process instance
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    // then: cancel EL job should be created
    assertThat(
            jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withType(CANCEL_EL_TYPE)
                .getFirst()
                .getValue())
        .satisfies(
            job -> {
              assertThat(job.getJobKind()).isEqualTo(JobKind.EXECUTION_LISTENER);
              assertThat(job.getJobListenerEventType()).isEqualTo(JobListenerEventType.CANCEL);
            });

    // complete the cancel EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(CANCEL_EL_TYPE).complete();

    // then: process instance should be terminated
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldExecuteMultipleCancelElsInOrder() {
    // given: process with multiple cancel execution listeners
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE + "_1")
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE + "_2")
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE + "_3")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                .endEvent()
                .done());

    // wait for the service task
    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(SERVICE_TASK_TYPE)
        .await();

    // when: cancel the process instance
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    // complete cancel EL jobs in order
    ENGINE.job().ofInstance(processInstanceKey).withType(CANCEL_EL_TYPE + "_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(CANCEL_EL_TYPE + "_2").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(CANCEL_EL_TYPE + "_3").complete();

    // then: all cancel EL jobs should be completed in order
    assertExecutionListenerJobsCompletedForElement(
        processInstanceKey,
        PROCESS_ID,
        CANCEL_EL_TYPE + "_1",
        CANCEL_EL_TYPE + "_2",
        CANCEL_EL_TYPE + "_3");

    // and: process instance should be terminated
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldHaveCorrectJobPropertiesForCancelElJob() {
    // given: process with cancel EL
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                .endEvent()
                .done());

    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(SERVICE_TASK_TYPE)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    // then: verify all job properties
    final var cancelElJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(CANCEL_EL_TYPE)
            .getFirst();

    assertThat(cancelElJob.getValue().getJobKind()).isEqualTo(JobKind.EXECUTION_LISTENER);
    assertThat(cancelElJob.getValue().getJobListenerEventType())
        .isEqualTo(JobListenerEventType.CANCEL);
    assertThat(cancelElJob.getValue().getType()).isEqualTo(CANCEL_EL_TYPE);
    assertThat(cancelElJob.getValue().getElementId()).isEqualTo(PROCESS_ID);
  }

  @Test
  public void shouldRetryCancelElJobAfterFailure() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                .endEvent()
                .done());

    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(SERVICE_TASK_TYPE)
        .await();

    // when: cancel the process and fail the cancel EL job
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();
    ENGINE.job().ofInstance(processInstanceKey).withType(CANCEL_EL_TYPE).withRetries(1).fail();

    // complete the retried cancel EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(CANCEL_EL_TYPE).complete();

    // then: assert the cancel EL job was completed after the failure
    assertThat(records().betweenProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.FAILED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldRecreateCancelElJobsAndProceedAfterIncidentResolution() {
    // given: process with expression-based cancel EL type that will fail
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeExecutionListener(el -> el.cancel().type(CANCEL_EL_TYPE + "_1"))
                .zeebeExecutionListener(el -> el.cancel().typeExpression("cancel_el_2_name_var"))
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                .endEvent()
                .done());

    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(SERVICE_TASK_TYPE)
        .await();

    // when: cancel the process instance
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    // complete the first cancel EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(CANCEL_EL_TYPE + "_1").complete();

    // then: incident should be raised for missing variable
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
                Expected result of the expression 'cancel_el_2_name_var' to be 'STRING', but was 'NULL'. \
                The evaluation reported the following warnings:
                [NO_VARIABLE_FOUND] No variable found with name 'cancel_el_2_name_var'""");

    // fix by resolving the incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // complete re-created first cancel EL job, providing the needed variable via job output
    final long recreatedJobKey =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(CANCEL_EL_TYPE + "_1")
            .skip(1)
            .getFirst()
            .getKey();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withKey(recreatedJobKey)
        .withVariable("cancel_el_2_name_var", CANCEL_EL_TYPE + "_evaluated_2")
        .complete();
    // complete the second cancel EL job
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(CANCEL_EL_TYPE + "_evaluated_2")
        .complete();

    // then: assert cancel EL jobs were processed correctly
    assertThat(
            jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .onlyEvents()
                .filter(r -> r.getValue().getJobListenerEventType() == JobListenerEventType.CANCEL)
                .limit(3))
        .extracting(r -> r.getValue().getType())
        .containsExactly(
            CANCEL_EL_TYPE + "_1", CANCEL_EL_TYPE + "_1", CANCEL_EL_TYPE + "_evaluated_2");

    // and: process instance should be terminated
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldExecuteCancelElWithStartAndEndEls() {
    // given: process with start, end, and cancel execution listeners
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeStartExecutionListener(START_EL_TYPE)
                .zeebeEndExecutionListener(END_EL_TYPE)
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                .endEvent()
                .done());

    // complete the start EL
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();

    // wait for service task
    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(SERVICE_TASK_TYPE)
        .await();

    // when: cancel the process instance (should trigger cancel EL, not end EL)
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    // complete the cancel EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(CANCEL_EL_TYPE).complete();

    // then: start EL was completed, cancel EL was created and completed,
    // end EL was NOT triggered
    assertThat(
            jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .onlyEvents()
                .limit(2))
        .extracting(r -> r.getValue().getType(), r -> r.getValue().getJobListenerEventType())
        .containsExactly(
            tuple(START_EL_TYPE, JobListenerEventType.START),
            tuple(CANCEL_EL_TYPE, JobListenerEventType.CANCEL));

    // and: process instance should be terminated (not completed)
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldBlockTerminationUntilCancelElIsCompleted() {
    // given: process with cancel EL
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                .endEvent()
                .done());

    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(SERVICE_TASK_TYPE)
        .await();

    // when: cancel the process instance
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    // wait for the cancel EL job to be created
    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(CANCEL_EL_TYPE)
        .await();

    // then: process should be in ELEMENT_TERMINATING state (blocked by cancel EL)
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    records
                        .processInstanceRecords()
                        .withProcessInstanceKey(processInstanceKey)
                        .withElementType(BpmnElementType.PROCESS)
                        .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                        .exists()))
        .isFalse();

    // when: complete the cancel EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(CANCEL_EL_TYPE).complete();

    // then: process should now be terminated
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .findAny())
        .isPresent();
  }

  @Test
  public void shouldEvaluateExpressionsForCancelExecutionListeners() {
    // given: process with expression-based cancel EL
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeExecutionListener(
                    el ->
                        el.cancel()
                            .typeExpression("cancelListenerTypeVar")
                            .retriesExpression("cancelRetries"))
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                .endEvent()
                .done(),
            Map.of("cancelListenerTypeVar", CANCEL_EL_TYPE, "cancelRetries", 5));

    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(SERVICE_TASK_TYPE)
        .await();

    // when: cancel the process instance
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    // complete the cancel EL
    ENGINE.job().ofInstance(processInstanceKey).withType(CANCEL_EL_TYPE).complete();

    // then: cancel EL job should have evaluated expression values
    assertThat(
            jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .onlyEvents()
                .getFirst())
        .satisfies(
            record -> {
              assertThat(record.getValue().getType()).isEqualTo(CANCEL_EL_TYPE);
              assertThat(record.getValue().getRetries()).isEqualTo(5);
              assertThat(record.getValue().getJobListenerEventType())
                  .isEqualTo(JobListenerEventType.CANCEL);
            });
  }

  @Test
  public void shouldNotTriggerCancelElWhenProcessCompletesNormally() {
    // given: process with cancel EL (and end EL for contrast)
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeEndExecutionListener(END_EL_TYPE)
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
                .startEvent()
                .manualTask()
                .endEvent()
                .done());

    // complete the end EL
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE).complete();

    // then: only end EL should be executed, not cancel EL
    assertThat(
            jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .onlyEvents()
                .limit(1))
        .extracting(r -> r.getValue().getType(), r -> r.getValue().getJobListenerEventType())
        .containsExactly(tuple(END_EL_TYPE, JobListenerEventType.END));

    // and: process instance should be completed (not terminated)
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    // and: no cancel EL job should have been created
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    records
                        .jobRecords()
                        .withProcessInstanceKey(processInstanceKey)
                        .withJobKind(JobKind.EXECUTION_LISTENER)
                        .withType(CANCEL_EL_TYPE)
                        .exists()))
        .describedAs("Cancel EL job should not be created when process completes normally")
        .isFalse();
  }

  @Test
  public void shouldCancelActiveServiceTaskBeforeCancelElExecution() {
    // given: process with cancel EL and a service task
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                .endEvent()
                .done());

    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(SERVICE_TASK_TYPE)
        .await();

    // when: cancel the process instance
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    // then: the service task job should be canceled
    assertThat(
            jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withType(SERVICE_TASK_TYPE)
                .getFirst())
        .isNotNull();

    // complete the cancel EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(CANCEL_EL_TYPE).complete();

    // then: verify order: service task canceled before cancel EL completes
    assertThat(records().betweenProcessInstance(processInstanceKey).onlyEvents())
        .filteredOn(
            r ->
                r.getValueType() == ValueType.JOB || r.getValueType() == ValueType.PROCESS_INSTANCE)
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.JOB, JobIntent.CANCELED), // service task canceled
            tuple(ValueType.JOB, JobIntent.CREATED), // cancel EL created
            tuple(ValueType.JOB, JobIntent.COMPLETED), // cancel EL completed
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldExecuteCancelElWhenProcessWithEmbeddedSubprocessIsCanceled() {
    // given: process with cancel EL and an embedded subprocess containing a service task
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
                .startEvent()
                .subProcess(
                    "subprocess",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .serviceTask("sub_task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                            .endEvent())
                .endEvent()
                .done());

    // wait for the service task inside the subprocess to be activated
    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(SERVICE_TASK_TYPE)
        .await();

    // when: cancel the process instance
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    // then: the subprocess service task job should be canceled
    assertThat(
            jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withType(SERVICE_TASK_TYPE)
                .getFirst())
        .isNotNull();

    // complete the cancel EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(CANCEL_EL_TYPE).complete();

    // then: cancel EL should have correct properties
    assertThat(
            jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .onlyEvents()
                .getFirst()
                .getValue())
        .satisfies(
            job -> {
              assertThat(job.getJobListenerEventType()).isEqualTo(JobListenerEventType.CANCEL);
              assertThat(job.getElementId()).isEqualTo(PROCESS_ID);
            });

    // and: process instance should be terminated with correct sequence:
    // process enters TERMINATING, children terminate, cancel EL fires, process terminates
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldExecuteCancelElWhenProcessWithParallelGatewayIsCanceled() {
    // given: process with cancel EL and a parallel gateway with two active tasks
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
                .startEvent()
                .parallelGateway("fork")
                .serviceTask("task_a", t -> t.zeebeJobType(SERVICE_TASK_TYPE + "_a"))
                .endEvent("end_a")
                .moveToNode("fork")
                .serviceTask("task_b", t -> t.zeebeJobType(SERVICE_TASK_TYPE + "_b"))
                .endEvent("end_b")
                .done());

    // wait for both tasks to be activated
    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(SERVICE_TASK_TYPE + "_a")
        .await();
    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(SERVICE_TASK_TYPE + "_b")
        .await();

    // when: cancel the process instance
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    // complete the cancel EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(CANCEL_EL_TYPE).complete();

    // then: both task jobs should be canceled
    assertThat(jobRecords(JobIntent.CANCELED).withProcessInstanceKey(processInstanceKey).limit(2))
        .extracting(r -> r.getValue().getType())
        .containsExactlyInAnyOrder(SERVICE_TASK_TYPE + "_a", SERVICE_TASK_TYPE + "_b");

    // and: cancel EL should have been completed
    assertThat(
            jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .onlyEvents()
                .getFirst()
                .getValue())
        .satisfies(
            job -> {
              assertThat(job.getJobListenerEventType()).isEqualTo(JobListenerEventType.CANCEL);
              assertThat(job.getElementId()).isEqualTo(PROCESS_ID);
            });

    // and: process instance should be terminated
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldExecuteCancelElWhenProcessWithCallActivityIsCanceled() {
    // given: a child process and a parent process with cancel EL + call activity
    final BpmnModelInstance childProcess =
        Bpmn.createExecutableProcess("child_process")
            .startEvent()
            .serviceTask("child_task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();
    final BpmnModelInstance parentProcess =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
            .startEvent()
            .callActivity("call_activity", c -> c.zeebeProcessId("child_process"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource("child.bpmn", childProcess).deploy();
    ENGINE.deployment().withXmlResource("parent.bpmn", parentProcess).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // wait for the child process task to be activated
    jobRecords(JobIntent.CREATED).withType(SERVICE_TASK_TYPE).await();

    // when: cancel the parent process instance
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    // complete the cancel EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(CANCEL_EL_TYPE).complete();

    // then: cancel EL should have been completed with correct properties
    assertThat(
            jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .onlyEvents()
                .getFirst()
                .getValue())
        .satisfies(
            job -> {
              assertThat(job.getJobListenerEventType()).isEqualTo(JobListenerEventType.CANCEL);
              assertThat(job.getElementId()).isEqualTo(PROCESS_ID);
            });

    // and: parent process instance should be terminated
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldNotTriggerCancelElWhenInterruptingEventSubprocessActivates() {
    // given: process with cancel EL and an interrupting message event subprocess
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
                .zeebeEndExecutionListener(END_EL_TYPE)
                .eventSubProcess(
                    "event_subprocess",
                    sub ->
                        sub.startEvent("event_start")
                            .interrupting(true)
                            .message(
                                m -> m.name("interruptMsg").zeebeCorrelationKeyExpression("key"))
                            .endEvent("event_end"))
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                .endEvent()
                .done(),
            Map.of("key", "correlationKey"));

    // wait for the service task
    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(SERVICE_TASK_TYPE)
        .await();

    // when: trigger the interrupting event subprocess
    ENGINE.message().withName("interruptMsg").withCorrelationKey("correlationKey").publish();

    // complete the end EL (event subprocess completion triggers process end EL)
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE).complete();

    // then: the process should complete (via event subprocess), not terminate
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    // and: no cancel EL job should have been created
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    records
                        .jobRecords()
                        .withProcessInstanceKey(processInstanceKey)
                        .withJobKind(JobKind.EXECUTION_LISTENER)
                        .withType(CANCEL_EL_TYPE)
                        .exists()))
        .describedAs(
            "Cancel EL job should not be created when process is interrupted by event subprocess")
        .isFalse();
  }

  @Test
  public void shouldCompleteCancelElWithVariables() {
    // given: process with cancel EL
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                .endEvent()
                .done());

    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(SERVICE_TASK_TYPE)
        .await();

    // when: cancel and complete the cancel EL with variables
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(CANCEL_EL_TYPE)
        .withVariable("cancelReason", "user_requested")
        .complete();

    // then: process instance should be terminated
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldRejectDoubleCancelWhileTerminatingWithActiveChildrenAndNoCancelEl() {
    // given: child process with cancel-EL (keeps it in TERMINATING state)
    // and parent process with cancel-EL and a call activity
    final BpmnModelInstance childProcess =
        Bpmn.createExecutableProcess("child_process")
            .zeebeCancelExecutionListener(CANCEL_EL_TYPE + "_child")
            .startEvent()
            .serviceTask("child_task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();
    final BpmnModelInstance parentProcess =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
            .startEvent()
            .callActivity("call_activity", c -> c.zeebeProcessId("child_process"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource("child.bpmn", childProcess).deploy();
    ENGINE.deployment().withXmlResource("parent.bpmn", parentProcess).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // wait for child process service task
    jobRecords(JobIntent.CREATED).withType(SERVICE_TASK_TYPE).await();

    // when: cancel the parent process instance
    // The parent enters TERMINATING → call activity enters TERMINATING
    // → child process enters TERMINATING → child cancel-EL job is created
    // Parent is still TERMINATING with active children and NO active cancel-EL job
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    // wait for child cancel-EL job to confirm parent is in correct state:
    // parent is TERMINATING, call activity is still active, parent has NO cancel-EL job
    final var childCancelElJob =
        jobRecords(JobIntent.CREATED)
            .withType(CANCEL_EL_TYPE + "_child")
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .getFirst();

    // when: send a second cancel while parent is terminating with no cancel-EL job active
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectRejection().cancel();

    // then: the second cancel should be rejected (no active cancel-EL job on parent)
    assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyCommandRejections()
                .withProcessInstanceKey(processInstanceKey)
                .withIntent(ProcessInstanceIntent.CANCEL)
                .findAny())
        .describedAs(
            "Second cancel should be rejected when process is terminating with active children "
                + "and no active cancel-EL job")
        .isPresent();

    // complete the child cancel-EL job so the process can finish normally
    ENGINE.job().withKey(childCancelElJob.getKey()).complete();

    // complete the parent cancel-EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(CANCEL_EL_TYPE).complete();

    // and: process instance should eventually be terminated
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }
}
