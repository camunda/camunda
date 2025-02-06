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
import static io.camunda.zeebe.protocol.record.intent.JobIntent.FAILED;
import static io.camunda.zeebe.test.util.record.RecordingExporter.incidentRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.records;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.Assume.assumeThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractTaskBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Verifies the behavior of task-based elements with execution listeners. */
@RunWith(Enclosed.class)
public class ExecutionListenerTaskElementsTest {

  @RunWith(Parameterized.class)
  public static class ParametrizedTest {

    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
    private static final String PROCESS_ID = "process";
    private static final String DMN_RESOURCE = "/dmn/drg-force-user.dmn";

    private static final String START_EL_TYPE = "start_execution_listener_job";
    private static final String END_EL_TYPE = "end_execution_listener_job";

    @Rule
    public final RecordingExporterTestWatcher recordingExporterTestWatcher =
        new RecordingExporterTestWatcher();

    @Parameter public TaskTestScenario scenario;

    @Parameters(name = "{index}: Test with ''{0}''")
    public static Collection<Object[]> parameters() {
      return Arrays.asList(
          new Object[][] {
            {
              TaskTestScenario.of(
                  "undefined task", BpmnElementType.TASK, AbstractFlowNodeBuilder::task)
            },
            {
              TaskTestScenario.of(
                  "manual task", BpmnElementType.MANUAL_TASK, AbstractFlowNodeBuilder::manualTask)
            },
            {
              TaskTestScenario.of(
                  "service task",
                  BpmnElementType.SERVICE_TASK,
                  b -> b.serviceTask().zeebeJobType("service_task_job"),
                  createCompleteJobWorkerTaskProcessor("service_task_job"))
            },
            {
              TaskTestScenario.of(
                  "job-worker script task",
                  BpmnElementType.SCRIPT_TASK,
                  b -> b.scriptTask().zeebeJobType("script_task_job"),
                  createCompleteJobWorkerTaskProcessor("script_task_job"))
            },
            {
              TaskTestScenario.of(
                  "script task with zeebe expression",
                  BpmnElementType.SCRIPT_TASK,
                  b -> b.scriptTask().zeebeExpression("225 + 500").zeebeResultVariable("sum"))
            },
            {
              TaskTestScenario.of(
                  "job-worker business rule task",
                  BpmnElementType.BUSINESS_RULE_TASK,
                  b -> b.businessRuleTask().zeebeJobType("business_rule_task_job"),
                  createCompleteJobWorkerTaskProcessor("business_rule_task_job"))
            },
            {
              TaskTestScenario.of(
                  "business rule task with a called decision",
                  BpmnElementType.BUSINESS_RULE_TASK,
                  b ->
                      b.businessRuleTask()
                          .zeebeCalledDecisionId("jedi_or_sith")
                          .zeebeResultVariable("result"))
            },
            {
              TaskTestScenario.of(
                  "job-worker send task",
                  BpmnElementType.SEND_TASK,
                  b -> b.sendTask().zeebeJobType("send_task_job"),
                  createCompleteJobWorkerTaskProcessor("send_task_job"))
            },
            {
              TaskTestScenario.of(
                  "job-worker user task",
                  BpmnElementType.USER_TASK,
                  AbstractFlowNodeBuilder::userTask,
                  createCompleteJobWorkerTaskProcessor("io.camunda.zeebe:userTask"))
            },
            {
              TaskTestScenario.of(
                  "zeebe user task",
                  BpmnElementType.USER_TASK,
                  b -> b.userTask().zeebeUserTask().zeebeAssignee("foo"),
                  pik -> ENGINE.userTask().ofInstance(pik).complete())
            },
            {
              TaskTestScenario.of(
                  "receive task",
                  BpmnElementType.RECEIVE_TASK,
                  b ->
                      b.receiveTask()
                          .message(mb -> mb.name("msg").zeebeCorrelationKey("=\"id-123\"")),
                  pik -> ENGINE.message().withName("msg").withCorrelationKey("id-123").publish())
            }
          });
    }

    @Test
    public void shouldCompleteTaskWithMultipleExecutionListeners() {
      // given
      deployProcess(
          createProcessWithTask(
              b ->
                  b.zeebeStartExecutionListener(START_EL_TYPE + "_1")
                      .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                      .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                      .zeebeEndExecutionListener(END_EL_TYPE + "_2")));
      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

      // when: complete the start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // process main task activity
      scenario.taskProcessor.accept(processInstanceKey);

      // complete the end execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      // then: EL jobs completed in expected order with expected listener event type
      assertThat(
              RecordingExporter.jobRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .withJobKind(JobKind.EXECUTION_LISTENER)
                  .withIntent(JobIntent.COMPLETED)
                  .withElementId(scenario.elementType.name())
                  .limit(4))
          .extracting(Record::getValue)
          .extracting(v -> tuple(v.getType(), v.getJobKind(), v.getJobListenerEventType()))
          .containsExactly(
              tuple(START_EL_TYPE + "_1", JobKind.EXECUTION_LISTENER, JobListenerEventType.START),
              tuple(START_EL_TYPE + "_2", JobKind.EXECUTION_LISTENER, JobListenerEventType.START),
              tuple(END_EL_TYPE + "_1", JobKind.EXECUTION_LISTENER, JobListenerEventType.END),
              tuple(END_EL_TYPE + "_2", JobKind.EXECUTION_LISTENER, JobListenerEventType.END));

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldRetryStartExecutionListenerAfterFailure() {
      // given
      deployProcess(createProcessWithTask(b -> b.zeebeStartExecutionListener(START_EL_TYPE)));
      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

      // when: fail start EL with retries
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).withRetries(1).fail();
      // complete failed start EL job
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();

      // process main task activity
      scenario.taskProcessor.accept(processInstanceKey);

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
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldRetryEndExecutionListenerAfterFailure() {
      // given
      deployProcess(createProcessWithTask(b -> b.zeebeEndExecutionListener(END_EL_TYPE)));
      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

      // process main task activity
      scenario.taskProcessor.accept(processInstanceKey);

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
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCreateIncidentForStartElWhenNoRetriesLeftAndProceedWithRemainingListeners() {
      // given
      deployProcess(
          createProcessWithTask(
              b ->
                  b.zeebeStartExecutionListener(START_EL_TYPE + "_1")
                      .zeebeStartExecutionListener(START_EL_TYPE + "_2")));
      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

      // when: fail start EL job with no retries
      ENGINE
          .job()
          .ofInstance(processInstanceKey)
          .withType(START_EL_TYPE + "_1")
          .withRetries(0)
          .fail();

      // then: incident created
      final Record<IncidentRecordValue> incident =
          RecordingExporter.incidentRecords(IncidentIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .getFirst();
      Assertions.assertThat(incident.getValue())
          .hasProcessInstanceKey(processInstanceKey)
          .hasErrorType(ErrorType.EXECUTION_LISTENER_NO_RETRIES)
          .hasErrorMessage("No more retries left.");

      // resolve incident & complete start EL jobs
      ENGINE
          .job()
          .ofInstance(processInstanceKey)
          .withType(START_EL_TYPE + "_1")
          .withRetries(1)
          .updateRetries();
      ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // process main task activity
      scenario.taskProcessor.accept(processInstanceKey);

      // assert the EL job was completed after incident resolution
      assertThat(
              records()
                  .betweenProcessInstance(processInstanceKey)
                  .withValueTypes(ValueType.JOB, ValueType.INCIDENT)
                  .onlyEvents())
          .extracting(Record::getIntent)
          .containsSequence(
              JobIntent.CREATED,
              JobIntent.FAILED,
              IncidentIntent.CREATED,
              JobIntent.RETRIES_UPDATED,
              IncidentIntent.RESOLVED,
              JobIntent.COMPLETED,
              JobIntent.CREATED,
              JobIntent.COMPLETED);

      assertExecutionListenerJobsCompleted(
          processInstanceKey, START_EL_TYPE + "_1", START_EL_TYPE + "_2");

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCreateIncidentForEndElWhenNoRetriesLeftAndProceedWithRemainingListeners() {
      // given
      deployProcess(
          createProcessWithTask(
              b ->
                  b.zeebeEndExecutionListener(END_EL_TYPE + "_1")
                      .zeebeEndExecutionListener(END_EL_TYPE + "_2")));
      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

      // process main task activity
      scenario.taskProcessor.accept(processInstanceKey);

      // when: fail end EL job with no retries
      ENGINE
          .job()
          .ofInstance(processInstanceKey)
          .withType(END_EL_TYPE + "_1")
          .withRetries(0)
          .fail();

      // then: incident created
      final Record<IncidentRecordValue> incident =
          RecordingExporter.incidentRecords(IncidentIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .getFirst();
      Assertions.assertThat(incident.getValue())
          .hasProcessInstanceKey(processInstanceKey)
          .hasErrorType(ErrorType.EXECUTION_LISTENER_NO_RETRIES)
          .hasErrorMessage("No more retries left.");

      // resolve incident & complete end EL jobs
      ENGINE
          .job()
          .ofInstance(processInstanceKey)
          .withType(END_EL_TYPE + "_1")
          .withRetries(1)
          .updateRetries();
      ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      // assert the EL job was completed after incident resolution
      assertThat(
              records()
                  .betweenProcessInstance(processInstanceKey)
                  .withValueTypes(ValueType.JOB, ValueType.INCIDENT)
                  .onlyEvents())
          .extracting(Record::getIntent)
          .containsSequence(
              JobIntent.CREATED,
              JobIntent.FAILED,
              IncidentIntent.CREATED,
              JobIntent.RETRIES_UPDATED,
              IncidentIntent.RESOLVED,
              JobIntent.COMPLETED,
              JobIntent.CREATED,
              JobIntent.COMPLETED);

      assertExecutionListenerJobsCompleted(
          processInstanceKey, END_EL_TYPE + "_1", END_EL_TYPE + "_2");

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void
        shouldCreateIncidentDuringEvaluatingTaskInputMappingsAndProceedWithStartListeners() {
      // Skip test for `BpmnElementType.TASK` and `BpmnElementType.MANUAL_TASK` because
      // these element types do not support input mappings.
      assumeThat(
          scenario.elementType, is(not(oneOf(BpmnElementType.TASK, BpmnElementType.MANUAL_TASK))));

      // given
      deployProcess(
          createProcessWithTask(
              b ->
                  b.zeebeInputExpression("assert(some_var, some_var != null)", "o_var_1")
                      .zeebeStartExecutionListener(START_EL_TYPE)));
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

      // fix issue by providing missing `some_var` variable
      ENGINE
          .variables()
          .ofScope(processInstanceKey)
          .withDocument(Map.of("some_var", "foo_bar"))
          .update();
      // resolve incident
      ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

      // complete start EL job
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();

      // process main task activity
      scenario.taskProcessor.accept(processInstanceKey);

      // assert the EL job was created after incident resolution
      assertThat(
              records()
                  .betweenProcessInstance(processInstanceKey)
                  .withValueTypes(ValueType.JOB, ValueType.INCIDENT)
                  .onlyEvents())
          .extracting(Record::getValueType, Record::getIntent)
          .containsSequence(
              tuple(ValueType.INCIDENT, IncidentIntent.CREATED),
              tuple(ValueType.INCIDENT, IncidentIntent.RESOLVED),
              tuple(ValueType.JOB, JobIntent.CREATED),
              tuple(ValueType.JOB, JobIntent.COMPLETED));

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCreateIncidentDuringEvaluatingTaskOutputMappingsAndProceedWithEndListeners() {
      // Skip test for `BpmnElementType.TASK` and `BpmnElementType.MANUAL_TASK` because
      // these element types do not support output mappings.
      assumeThat(
          scenario.elementType, is(not(oneOf(BpmnElementType.TASK, BpmnElementType.MANUAL_TASK))));

      // given
      deployProcess(
          createProcessWithTask(
              b ->
                  b.zeebeOutputExpression("assert(some_var, some_var != null)", "o_var_1")
                      .zeebeEndExecutionListener(END_EL_TYPE)));
      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

      // when: process main task activity
      scenario.taskProcessor.accept(processInstanceKey);

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

      // complete start EL job
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE).complete();

      // assert the EL job was created after incident resolution
      assertThat(
              records()
                  .betweenProcessInstance(processInstanceKey)
                  .withValueTypes(ValueType.JOB, ValueType.INCIDENT)
                  .onlyEvents())
          .extracting(Record::getValueType, Record::getIntent)
          .containsSequence(
              tuple(ValueType.INCIDENT, IncidentIntent.CREATED),
              tuple(ValueType.INCIDENT, IncidentIntent.RESOLVED),
              tuple(ValueType.JOB, JobIntent.CREATED),
              tuple(ValueType.JOB, JobIntent.COMPLETED));

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldRecurFailedStartExecutionListenerJobAfterBackoff() {
      // given
      deployProcess(createProcessWithTask(b -> b.zeebeStartExecutionListener(START_EL_TYPE)));
      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

      // when: fail start EL
      final Duration backOff = Duration.ofMinutes(30);
      final Record<JobRecordValue> failedStartElJobRecord =
          ENGINE
              .job()
              .ofInstance(processInstanceKey)
              .withType(START_EL_TYPE)
              .withBackOff(backOff)
              .withRetries(2)
              .fail();

      Assertions.assertThat(failedStartElJobRecord)
          .hasRecordType(RecordType.EVENT)
          .hasIntent(FAILED);

      // increase time to backoff
      ENGINE.increaseTime(backOff);

      // then: assert the EL job was recurred after backoff
      assertThat(
              jobRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .withJobKind(JobKind.EXECUTION_LISTENER)
                  .onlyEvents()
                  .limit(3))
          .extracting(Record::getIntent, Record::getKey)
          .containsSequence(
              tuple(JobIntent.CREATED, failedStartElJobRecord.getKey()),
              tuple(JobIntent.FAILED, failedStartElJobRecord.getKey()),
              tuple(JobIntent.RECURRED_AFTER_BACKOFF, failedStartElJobRecord.getKey()));

      // complete re-created job
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();

      // process main task activity
      scenario.taskProcessor.accept(processInstanceKey);

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldEvaluateExpressionsForExecutionListeners() {
      // given
      deployProcess(
          createProcessWithTask(
              b ->
                  b.zeebeExecutionListener(
                          el ->
                              el.start()
                                  .typeExpression("listenerNameVar")
                                  .retriesExpression("elRetries"))
                      .zeebeExecutionListener(
                          el -> el.start().type(START_EL_TYPE + "_2").retries("5"))
                      .zeebeExecutionListener(
                          el -> el.end().type(END_EL_TYPE).retriesExpression("elRetries + 5"))));

      // when
      final long processInstanceKey =
          ENGINE
              .processInstance()
              .ofBpmnProcessId(PROCESS_ID)
              .withVariables(
                  Map.of("elRetries", 6, "listenerNameVar", START_EL_TYPE + "evaluated_1"))
              .create();

      // complete start ELs
      ENGINE
          .job()
          .ofInstance(processInstanceKey)
          .withType(START_EL_TYPE + "evaluated_1")
          .complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();
      // process main task activity
      scenario.taskProcessor.accept(processInstanceKey);
      // complete end EL
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
          .containsSequence(
              tuple(START_EL_TYPE + "evaluated_1", 6),
              tuple(START_EL_TYPE + "_2", 5),
              tuple(END_EL_TYPE, 11));

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldAllowEndListenerToAccessStartListenerVariable() {
      // given
      deployProcess(
          createProcessWithTask(
              b ->
                  b.zeebeStartExecutionListener(START_EL_TYPE)
                      .zeebeEndExecutionListener(END_EL_TYPE)));
      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

      // when: complete start EL with `foo` variable
      ENGINE
          .job()
          .ofInstance(processInstanceKey)
          .withType(START_EL_TYPE)
          .withVariable("foo", 1)
          .complete();

      // process main task activity
      scenario.taskProcessor.accept(processInstanceKey);

      // then: `foo` variable accessible in end EL
      final Optional<JobRecordValue> jobActivated =
          ENGINE.jobs().withType(END_EL_TYPE).activate().getValue().getJobs().stream()
              .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
              .findFirst();

      assertThat(jobActivated)
          .hasValueSatisfying(job -> assertThat(job.getVariables()).contains(entry("foo", 1)));

      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE).complete();

      // assert the variable was created after start EL completion
      assertThat(
              records()
                  .betweenProcessInstance(processInstanceKey)
                  .withValueTypes(ValueType.JOB, ValueType.VARIABLE)
                  .onlyEvents())
          .extracting(Record::getValueType, Record::getIntent)
          .containsSequence(
              tuple(ValueType.JOB, JobIntent.CREATED),
              tuple(ValueType.JOB, JobIntent.COMPLETED),
              tuple(ValueType.VARIABLE, VariableIntent.CREATED));
    }

    @Test
    public void shouldRestrictStartExecutionListenerVariablesToOwningElementScope() {
      // given: Deploy process with a task having start ELs and another task following it
      deployProcess(
          createProcessWithTask(
              b ->
                  b.zeebeStartExecutionListener(START_EL_TYPE)
                      .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                      .serviceTask(
                          "subsequent_service_task",
                          tb -> tb.zeebeJobType("subsequent_service_task"))));
      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

      // when: Complete the first start execution listener job with a variable 'el_var'
      ENGINE
          .job()
          .ofInstance(processInstanceKey)
          .withType(START_EL_TYPE)
          .withVariable("el_var", "bar")
          .complete();

      // then: assert the variable 'el_var' is accessible within the second start EL job
      final Optional<JobRecordValue> secondElJob =
          ENGINE.jobs().withType(START_EL_TYPE + "_2").activate().getValue().getJobs().stream()
              .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
              .findFirst();
      assertThat(secondElJob)
          .hasValueSatisfying(job -> assertThat(job.getVariables()).containsKey("el_var"));
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // when: process the main task activity
      scenario.taskProcessor.accept(processInstanceKey);

      // then: assert the variable 'el_var' is not accessible in the subsequent service task element
      final var subsequentServiceTaskJob =
          ENGINE.jobs().withType("subsequent_service_task").activate().getValue().getJobs().stream()
              .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
              .findFirst();

      assertThat(subsequentServiceTaskJob)
          .hasValueSatisfying(job -> assertThat(job.getVariables()).doesNotContainKey("el_var"));
      ENGINE.job().ofInstance(processInstanceKey).withType("subsequent_service_task").complete();
    }

    @Test
    public void shouldAllowSubsequentElementToAccessVariableProducedByTaskEndListenerJob() {
      // given: deploy process with a task having end EL and another task following it
      deployProcess(
          createProcessWithTask(
              b ->
                  b.zeebeEndExecutionListener(END_EL_TYPE)
                      .serviceTask(
                          "subsequent_task", tb -> tb.zeebeJobType("subsequent_service_task"))));
      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

      scenario.taskProcessor.accept(processInstanceKey);

      // when: complete the end EL job with a variable 'end_el_var'
      ENGINE
          .job()
          .ofInstance(processInstanceKey)
          .withType(END_EL_TYPE)
          .withVariable("end_el_var", "baz")
          .complete();

      // then: assert the variable 'end_el_var' is accessible by the subsequent service task element
      final var subsequentServiceTaskJob =
          ENGINE.jobs().withType("subsequent_service_task").activate().getValue().getJobs().stream()
              .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
              .findFirst();

      assertThat(subsequentServiceTaskJob)
          .hasValueSatisfying(
              job -> assertThat(job.getVariables()).contains(entry("end_el_var", "baz")));
      ENGINE.job().ofInstance(processInstanceKey).withType("subsequent_service_task").complete();
    }

    @Test
    public void
        shouldRecreateStartExecutionListenerJobsAndProceedAfterExtractValueErrorIncidentResolution() {
      // given
      deployProcess(
          createProcessWithTask(
              b ->
                  b.zeebeExecutionListener(el -> el.start().type(START_EL_TYPE + "_1"))
                      .zeebeExecutionListener(
                          el -> el.start().typeExpression("start_el_2_name_var"))
                      .zeebeExecutionListener(el -> el.start().type(START_EL_TYPE + "_3"))
                      .zeebeExecutionListener(el -> el.end().type(END_EL_TYPE))));

      // when
      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

      // compete first EL job
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
          .withDocument(Map.of("start_el_2_name_var", START_EL_TYPE + "_eval_2"))
          .update();
      // and: resolve incident
      ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();
      // then: complete 1st re-created start EL job
      completeRecreatedJobWithType(ENGINE, processInstanceKey, START_EL_TYPE + "_1");
      // complete 2nd & 3d start EL jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_eval_2").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_3").complete();

      // process main task activity
      scenario.taskProcessor.accept(processInstanceKey);

      // complete end EL
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE).complete();

      // then: assert that the first start EL job was re-created
      assertThat(
              jobRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .withJobKind(JobKind.EXECUTION_LISTENER)
                  .limit(8)
                  .onlyEvents())
          .extracting(r -> r.getValue().getType(), Record::getIntent)
          .containsSequence(
              tuple(START_EL_TYPE + "_1", JobIntent.CREATED),
              tuple(START_EL_TYPE + "_1", JobIntent.COMPLETED),
              // 1st start EL job recreated
              tuple(START_EL_TYPE + "_1", JobIntent.CREATED),
              tuple(START_EL_TYPE + "_1", JobIntent.COMPLETED),
              // remaining start EL jobs processing
              tuple(START_EL_TYPE + "_eval_2", JobIntent.CREATED),
              tuple(START_EL_TYPE + "_eval_2", JobIntent.COMPLETED),
              tuple(START_EL_TYPE + "_3", JobIntent.CREATED),
              tuple(START_EL_TYPE + "_3", JobIntent.COMPLETED));

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldRecreateEndExecutionListenerJobsAndProceedAfterIncidentResolution() {
      // given
      deployProcess(
          createProcessWithTask(
              b ->
                  b.zeebeExecutionListener(el -> el.start().type(START_EL_TYPE))
                      .zeebeExecutionListener(el -> el.end().type(END_EL_TYPE + "_1"))
                      .zeebeExecutionListener(el -> el.end().type(END_EL_TYPE + "_2"))
                      .zeebeExecutionListener(el -> el.end().typeExpression("end_el_3_name_var"))));

      // when
      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
      // compete first EL job
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();
      // process main task activity
      scenario.taskProcessor.accept(processInstanceKey);
      // complete 1st and 2nd end EL jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      // then: incident for the second EL should be raised due to missing `end_el_3_name_var` var
      final Record<IncidentRecordValue> incident =
          RecordingExporter.incidentRecords(IncidentIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .getFirst();

      Assertions.assertThat(incident.getValue())
          .hasProcessInstanceKey(processInstanceKey)
          .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
          .hasErrorMessage(
              """
                Expected result of the expression 'end_el_3_name_var' to be 'STRING', but was 'NULL'. \
                The evaluation reported the following warnings:
                [NO_VARIABLE_FOUND] No variable found with name 'end_el_3_name_var'""");

      // fix issue with missing `end_el_3_name_var` variable, required by 2nd EL[start]
      ENGINE
          .variables()
          .ofScope(processInstanceKey)
          .withDocument(Map.of("end_el_3_name_var", END_EL_TYPE + "_eval_3"))
          .update();
      // resolve incident
      ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

      // then: complete 1st and 2nd re-created end EL jobs
      completeRecreatedJobWithType(ENGINE, processInstanceKey, END_EL_TYPE + "_1");
      completeRecreatedJobWithType(ENGINE, processInstanceKey, END_EL_TYPE + "_2");
      // complete last end EL job
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_eval_3").complete();

      // then: assert that the 1st & 2nd end EL job were re-created
      assertThat(
              jobRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .withJobKind(JobKind.EXECUTION_LISTENER)
                  .limit(12)
                  .onlyEvents())
          .extracting(r -> r.getValue().getType(), Record::getIntent)
          .containsSequence(
              // start EL job processing
              tuple(START_EL_TYPE, JobIntent.CREATED),
              tuple(START_EL_TYPE, JobIntent.COMPLETED),
              // end EL jobs processing
              tuple(END_EL_TYPE + "_1", JobIntent.CREATED),
              tuple(END_EL_TYPE + "_1", JobIntent.COMPLETED),
              tuple(END_EL_TYPE + "_2", JobIntent.CREATED),
              tuple(END_EL_TYPE + "_2", JobIntent.COMPLETED),
              // re-created 1st & 2nd end EL jobs
              tuple(END_EL_TYPE + "_1", JobIntent.CREATED),
              tuple(END_EL_TYPE + "_1", JobIntent.COMPLETED),
              tuple(END_EL_TYPE + "_2", JobIntent.CREATED),
              tuple(END_EL_TYPE + "_2", JobIntent.COMPLETED),
              // last end EL job processing
              tuple(END_EL_TYPE + "_eval_3", JobIntent.CREATED),
              tuple(END_EL_TYPE + "_eval_3", JobIntent.COMPLETED));

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(scenario.elementType, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCancelActiveStartElJobAfterProcessInstanceCancellation() {
      // given
      deployProcess(
          createProcessWithTask(
              b -> b.zeebeExecutionListener(el -> el.start().type(START_EL_TYPE))));
      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
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

    @Test
    public void shouldResolveIncidentForElJobAfterProcessInstanceCancellation() {
      // given
      deployProcess(
          createProcessWithTask(
              b -> b.zeebeExecutionListener(el -> el.start().type(START_EL_TYPE))));
      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).withRetries(0).fail();
      final var incident =
          incidentRecords(IncidentIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .withErrorType(ErrorType.EXECUTION_LISTENER_NO_RETRIES)
              .getFirst();

      // when
      ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

      // then: incident should be resolved
      assertThat(
              incidentRecords(IncidentIntent.RESOLVED)
                  .withProcessInstanceKey(processInstanceKey)
                  .withErrorType(ErrorType.EXECUTION_LISTENER_NO_RETRIES)
                  .getFirst())
          .extracting(Record::getKey)
          .isEqualTo(incident.getKey());
    }

    private void assertExecutionListenerJobsCompleted(
        final long processInstanceKey, final String... elJobTypes) {
      assertExecutionListenerJobsCompletedForElement(
          processInstanceKey, scenario.elementType.name(), elJobTypes);
    }

    private static void deployProcess(final BpmnModelInstance modelInstance) {
      ENGINE
          .deployment()
          .withXmlClasspathResource(DMN_RESOURCE)
          .withXmlResource(modelInstance)
          .deploy();
    }

    private BpmnModelInstance createProcessWithTask(
        final Consumer<AbstractTaskBuilder<?, ?>> consumer) {
      final var taskBuilder =
          scenario
              .taskConfigurer
              .apply(Bpmn.createExecutableProcess(PROCESS_ID).startEvent())
              .id(scenario.elementType.name());

      consumer.accept(taskBuilder);

      return taskBuilder.endEvent().done();
    }

    private static Consumer<Long> createCompleteJobWorkerTaskProcessor(final String taskType) {
      return pik -> ENGINE.job().ofInstance(pik).withType(taskType).complete();
    }

    private record TaskTestScenario(
        String name,
        BpmnElementType elementType,
        Function<StartEventBuilder, AbstractTaskBuilder<?, ?>> taskConfigurer,
        Consumer<Long> taskProcessor) {

      @Override
      public String toString() {
        return name;
      }

      private static TaskTestScenario of(
          final String name,
          final BpmnElementType elementType,
          final Function<StartEventBuilder, AbstractTaskBuilder<?, ?>> taskConfigurer,
          final Consumer<Long> taskProcessor) {
        return new TaskTestScenario(name, elementType, taskConfigurer, taskProcessor);
      }

      private static TaskTestScenario of(
          final String name,
          final BpmnElementType elementType,
          final Function<StartEventBuilder, AbstractTaskBuilder<?, ?>> taskConfigurer) {
        return new TaskTestScenario(name, elementType, taskConfigurer, pik -> {});
      }
    }
  }

  public static class ExtraTests {
    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

    @Rule
    public final RecordingExporterTestWatcher recordingExporterTestWatcher =
        new RecordingExporterTestWatcher();

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
      completeRecreatedJobWithType(ENGINE, processInstanceKey, START_EL_TYPE);

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
              tuple(
                  BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(
                  BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
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
              tuple(
                  BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(
                  BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldRecreateELJobAfterResolvingServiceTaskJobExpressionIncident() {
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
  }
}
