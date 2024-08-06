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
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.START_EL_TYPE;
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.createProcessInstance;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
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
import io.camunda.zeebe.model.bpmn.builder.IntermediateThrowEventBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Enclosed.class)
public class ExecutionListenerIntermediateThrowEventElementTest {

  @RunWith(Parameterized.class)
  public static class ParametrizedTest {
    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

    @Rule
    public final RecordingExporterTestWatcher recordingExporterTestWatcher =
        new RecordingExporterTestWatcher();

    @Parameter public IntermediateThrowEventTestScenario scenario;

    @Parameters(name = "{index}: {0}")
    public static Collection<Object[]> intermediateThrowEventParameters() {
      return Arrays.asList(
          new Object[][] {
            {IntermediateThrowEventTestScenario.of("none", e -> e)},
            {
              IntermediateThrowEventTestScenario.of(
                  "message_publish",
                  e -> e.message(m -> m.name("my_message").zeebeCorrelationKeyExpression("key")))
            },
            {
              IntermediateThrowEventTestScenario.of(
                  "message_job",
                  e -> e.message("my_message").zeebeJobType("event_message_job"),
                  pik -> ENGINE.job().ofInstance(pik).withType("event_message_job").complete())
            },
            {IntermediateThrowEventTestScenario.of("signal", e -> e.signal("signal"))},
            {
              IntermediateThrowEventTestScenario.of(
                  "compensation",
                  e -> e.compensateEventDefinition().compensateEventDefinitionDone())
            },
            {
              IntermediateThrowEventTestScenario.of(
                  "escalation", e -> e.escalation("my_escalation"))
            }
          });
    }

    @Test
    public void shouldCompleteIntermediateThrowEventWithMultipleExecutionListeners() {
      // given
      final String eventElementId = String.format("%s-intermediate-throw-event", scenario.name);
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .intermediateThrowEvent(eventElementId, e -> scenario.builderFunction.apply(e))
                  .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                  .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_2")
                  .manualTask()
                  .endEvent()
                  .done());

      // when: complete the start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // process intermediate throw event element
      scenario.eventElementProcessor.accept(processInstanceKey);

      // complete the end execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void startExecutionListenersShouldAccessInputVariablesOfIntermediateThrowEvent() {
      // Invoke test only for `signal` and `message job` Intermediate Throw events
      // as only they are supporting input mappings
      assumeThat(scenario.name, is(oneOf("signal", "message_job")));

      // given
      final var modelInstance =
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent()
              .intermediateThrowEvent(
                  scenario.name,
                  c ->
                      scenario
                          .builderFunction
                          .apply(c)
                          .zeebeInput("=contains(\"ABC\",\"B\")", "boolInputVar"))
              .zeebeStartExecutionListener(START_EL_TYPE)
              .endEvent()
              .done();

      // when: deploy process
      final long processInstanceKey = createProcessInstance(ENGINE, modelInstance);

      // then: `boolInputVar` variable accessible in start EL
      final Optional<JobRecordValue> startElJobActivated =
          ENGINE.jobs().withType(START_EL_TYPE).activate().getValue().getJobs().stream()
              .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
              .findFirst();

      assertThat(startElJobActivated)
          .hasValueSatisfying(
              job -> assertThat(job.getVariables()).contains(entry("boolInputVar", true)));
    }

    @Test
    public void endExecutionListenersShouldAccessOutputVariablesOfIntermediateThrowEvent() {
      // Skip test for `compensation` intermediate throw event as it do not support output mappings
      assumeThat(scenario.name, is(not("compensation")));

      // given
      final String eventElementId = String.format("%s-intermediate-throw-event", scenario.name);
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .intermediateThrowEvent(
                      eventElementId,
                      e ->
                          scenario
                              .builderFunction
                              .apply(e)
                              .zeebeOutputExpression("counter + 1", "updatedCounter"))
                  .zeebeEndExecutionListener(END_EL_TYPE)
                  .manualTask()
                  .endEvent()
                  .done(),
              Map.of("counter", 1));

      // when: process intermediate throw event element
      scenario.eventElementProcessor.accept(processInstanceKey);

      // then
      final Optional<JobRecordValue> jobActivated =
          ENGINE.jobs().withType(END_EL_TYPE).activate().getValue().getJobs().stream()
              .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
              .findFirst();

      assertThat(jobActivated)
          .hasValueSatisfying(
              job ->
                  assertThat(job.getVariables())
                      .contains(entry("counter", 1), entry("updatedCounter", 2)));
    }

    @Test
    public void shouldAllowSubsequentElementToAccessVariableProducedByThrowEventEndListenerJob() {
      // given: deploy process with throw event having end EL and service task following it
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .intermediateThrowEvent(scenario.name, e -> scenario.builderFunction.apply(e))
                  .zeebeEndExecutionListener(END_EL_TYPE)
                  .serviceTask("subsequent_task", tb -> tb.zeebeJobType("subsequent_service_task"))
                  .endEvent()
                  .done());

      // process intermediate throw event element
      scenario.eventElementProcessor.accept(processInstanceKey);

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
    public void shouldCancelActiveStartElJobAfterProcessInstanceCancellation() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .intermediateThrowEvent(scenario.name, e -> scenario.builderFunction.apply(e))
                  .zeebeStartExecutionListener(START_EL_TYPE)
                  .manualTask()
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

    private record IntermediateThrowEventTestScenario(
        String name,
        UnaryOperator<IntermediateThrowEventBuilder> builderFunction,
        Consumer<Long> eventElementProcessor) {

      @Override
      public String toString() {
        return name;
      }

      private static IntermediateThrowEventTestScenario of(
          final String name,
          final UnaryOperator<IntermediateThrowEventBuilder> builderFunction,
          final Consumer<Long> eventElementProcessor) {
        return new IntermediateThrowEventTestScenario(name, builderFunction, eventElementProcessor);
      }

      private static IntermediateThrowEventTestScenario of(
          final String name, final UnaryOperator<IntermediateThrowEventBuilder> builderFunction) {
        return new IntermediateThrowEventTestScenario(name, builderFunction, ok -> {});
      }
    }
  }

  public static class ExtraTests {
    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

    @Rule
    public final RecordingExporterTestWatcher recordingExporterTestWatcher =
        new RecordingExporterTestWatcher();

    @Test
    public void shouldCompleteNonInterruptingEscalationThrowEventFromSubprocessWithMultipleELs() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .subProcess(
                      "subprocess",
                      s ->
                          s.embeddedSubProcess()
                              .startEvent()
                              .intermediateThrowEvent(
                                  "esc-throw-event",
                                  e ->
                                      e.escalation("my_escalation")
                                          .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                                          .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                                          .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                                          .zeebeEndExecutionListener(END_EL_TYPE + "_2"))
                              .endEvent("sub_e"))
                  // non interrupting escalation catch event
                  .boundaryEvent(
                      "esc-catch-event", b -> b.escalation("my_escalation").cancelActivity(false))
                  .manualTask()
                  .endEvent("escalation_e")
                  .moveToActivity("subprocess")
                  .endEvent("main_e")
                  .done());

      // when: complete all execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldNotExecuteEndELsForInterruptingEscalationThrowEventFromSubprocess() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .subProcess(
                      "subprocess",
                      s ->
                          s.embeddedSubProcess()
                              .startEvent()
                              .intermediateThrowEvent(
                                  "esc-throw-event",
                                  e ->
                                      e.escalation("my_escalation")
                                          .zeebeStartExecutionListener(START_EL_TYPE)
                                          .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                                          .zeebeEndExecutionListener(END_EL_TYPE + "_2"))
                              .endEvent("sub_e"))
                  // interrupting escalation catch event
                  .boundaryEvent("esc-catch-event", b -> b.escalation("my_escalation"))
                  .manualTask()
                  .endEvent("escalation_e")
                  .moveToActivity("subprocess")
                  .endEvent("main_e")
                  .done());

      // when: complete start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();

      // assert that only 1 start execution listener job was created
      assertThat(
              RecordingExporter.records()
                  .betweenProcessInstance(processInstanceKey)
                  .withIntent(JobIntent.CREATED)
                  .withValueType(ValueType.JOB))
          .extracting(Record::getValue)
          .map(JobRecordValue.class::cast)
          .map(JobRecordValue::getType)
          .containsExactly(START_EL_TYPE);

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_TERMINATED),
              tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
              tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCompleteIntermediateCompensationEventWithMultipleExecutionListeners() {
      // given
      final String eventElementId = "compensation-throw-event";

      final BpmnModelInstance process =
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent()
              .serviceTask(
                  "A",
                  task ->
                      task.zeebeJobType("A")
                          .boundaryEvent()
                          .compensation(
                              compensation ->
                                  compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
              .intermediateThrowEvent(
                  eventElementId, e -> e.compensateEventDefinition().activityRef("A"))
              .zeebeStartExecutionListener(START_EL_TYPE + "_1")
              .zeebeStartExecutionListener(START_EL_TYPE + "_2")
              .zeebeEndExecutionListener(END_EL_TYPE + "_1")
              .zeebeEndExecutionListener(END_EL_TYPE + "_2")
              .serviceTask("B", t -> t.zeebeJobType("B"))
              .endEvent()
              .done();

      ENGINE.deployment().withXmlResource(process).deploy();

      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

      // when: task A completed
      ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

      // then EL's for intermediate throw compensation event triggered
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

      // assert that process with intermediate throw event completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(
              r -> r.getValue().getElementId(),
              r -> r.getValue().getBpmnElementType(),
              Record::getIntent)
          .containsSubsequence(
              tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple("A", BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  eventElementId,
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(
                  eventElementId,
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  eventElementId,
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  eventElementId,
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  "Undo-A", BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  eventElementId,
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(
                  eventElementId,
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  eventElementId,
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  eventElementId,
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple("B", BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }
  }
}
