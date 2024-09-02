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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.processing.deployment.model.validation.ExpectedValidationResult;
import io.camunda.zeebe.engine.processing.deployment.model.validation.ProcessValidationUtil;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractEndEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.EndEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.EventSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
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
public class ExecutionListenerEndEventElementTest {

  @RunWith(Parameterized.class)
  public static class ParametrizedTest {

    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

    @Rule
    public final RecordingExporterTestWatcher recordingExporterTestWatcher =
        new RecordingExporterTestWatcher();

    @Parameter public EndEventTestScenario scenario;

    @Parameters(name = "{index}: {0}")
    public static Collection<Object[]> endEventParameters() {
      return Arrays.asList(
          new Object[][] {
            {EndEventTestScenario.of("none", e -> e)},
            {
              EndEventTestScenario.of(
                  "message",
                  e -> e.message("my_message").zeebeJobType("end_event_message_job"),
                  pik -> ENGINE.job().ofInstance(pik).withType("end_event_message_job").complete())
            },
            {EndEventTestScenario.of("signal", e -> e.signal("my_signal"))},
            {EndEventTestScenario.of("terminate", AbstractEndEventBuilder::terminate)},
            {EndEventTestScenario.of("escalation", e -> e.escalation("my_escalation"))},
            {
              EndEventTestScenario.of(
                  "compensation",
                  e -> e.compensateEventDefinition().compensateEventDefinitionDone())
            }
          });
    }

    @Test
    public void shouldCompleteEndEventWithMultipleExecutionListeners() {
      // given
      final String endEventElementId = String.format("end_%s_event", scenario.name);
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .manualTask()
                  .endEvent(endEventElementId, e -> scenario.endEventBuilderFunction.apply(e))
                  .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                  .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_2")
                  .done());

      // when: complete the start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // process end event element
      scenario.eventProcessor.accept(processInstanceKey);

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
              tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCancelActiveStartElJobAfterProcessInstanceCancellation() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .manualTask()
                  .endEvent("end_event", e -> scenario.endEventBuilderFunction.apply(e))
                  .zeebeStartExecutionListener(START_EL_TYPE)
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

    private record EndEventTestScenario(
        String name,
        UnaryOperator<EndEventBuilder> endEventBuilderFunction,
        Consumer<Long> eventProcessor) {

      @Override
      public String toString() {
        return name;
      }

      private static EndEventTestScenario of(
          final String name,
          final UnaryOperator<EndEventBuilder> endEventBuilderFunction,
          final Consumer<Long> eventProcessor) {
        return new EndEventTestScenario(name, endEventBuilderFunction, eventProcessor);
      }

      private static EndEventTestScenario of(
          final String name, final UnaryOperator<EndEventBuilder> endEventBuilderFunction) {
        return new EndEventTestScenario(name, endEventBuilderFunction, ok -> {});
      }
    }
  }

  public static class ExtraTests {
    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

    @Rule
    public final RecordingExporterTestWatcher recordingExporterTestWatcher =
        new RecordingExporterTestWatcher();

    @Test
    public void shouldCompleteNonInterruptingEscalationEndEventFromSubprocessWithMultipleELs() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess("process")
                  .startEvent()
                  .subProcess(
                      "subprocess",
                      s ->
                          s.embeddedSubProcess()
                              .startEvent()
                              .endEvent("esc_end", e -> e.escalation("escalation"))
                              .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                              .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                              .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                              .zeebeEndExecutionListener(END_EL_TYPE + "_2"))
                  // non interrupting escalation catch event
                  .boundaryEvent("esc_catch", b -> b.escalation("escalation").cancelActivity(false))
                  .endEvent()
                  .done());

      // when: complete the all execution listener jobs
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
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldNotExecuteEndELsForInterruptingEscalationEndEventFromSubprocess() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess("process")
                  .startEvent()
                  .subProcess(
                      "subprocess",
                      s ->
                          s.embeddedSubProcess()
                              .startEvent()
                              .endEvent("esc_end", e -> e.escalation("escalation"))
                              .zeebeStartExecutionListener(START_EL_TYPE)
                              .zeebeEndExecutionListener(END_EL_TYPE))
                  // interrupting escalation catch event
                  .boundaryEvent("esc_catch", b -> b.escalation("escalation"))
                  .endEvent()
                  .done());

      // when: complete start execution listener job
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
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATED),
              tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
              tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCompleteErrorEndEventWithMultipleStartExecutionListeners() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .subProcess(
                      "event-subprocess",
                      subProcess ->
                          subProcess
                              .embeddedSubProcess()
                              .startEvent()
                              .manualTask()
                              .endEvent("end-event-throw-error", e -> e.error("error"))
                              .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                              .zeebeStartExecutionListener(START_EL_TYPE + "_2"))
                  .boundaryEvent("boundary-catch-error", b -> b.error("error"))
                  .endEvent("end-event-parent")
                  .done());

      // when: complete the start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(
              r -> r.getValue().getElementId(),
              r -> r.getValue().getBpmnElementType(),
              Record::getIntent)
          .containsSubsequence(
              tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  "event-subprocess",
                  BpmnElementType.SUB_PROCESS,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  "end-event-throw-error",
                  BpmnElementType.END_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(
                  "end-event-throw-error",
                  BpmnElementType.END_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  "end-event-throw-error",
                  BpmnElementType.END_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  "end-event-throw-error",
                  BpmnElementType.END_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  "end-event-throw-error",
                  BpmnElementType.END_EVENT,
                  ProcessInstanceIntent.ELEMENT_TERMINATED),
              tuple(
                  "event-subprocess",
                  BpmnElementType.SUB_PROCESS,
                  ProcessInstanceIntent.ELEMENT_TERMINATED),
              tuple(
                  "boundary-catch-error",
                  BpmnElementType.BOUNDARY_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  "end-event-parent",
                  BpmnElementType.END_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldNotDeployProcessWithErrorEndEventWithEndExecutionListeners() {
      // given
      final var modelInstance =
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent()
              .subProcess(
                  "event-subprocess",
                  subProcess ->
                      subProcess
                          .embeddedSubProcess()
                          .startEvent()
                          .manualTask()
                          .endEvent("end-event-throw-error", e -> e.error("error"))
                          .zeebeStartExecutionListener(START_EL_TYPE)
                          .zeebeEndExecutionListener(END_EL_TYPE))
              .boundaryEvent("boundary-catch-error", b -> b.error("error"))
              .endEvent("end-event-parent")
              .done();

      // when - then
      ProcessValidationUtil.validateProcess(
          modelInstance,
          ExpectedValidationResult.expect(
              EndEvent.class,
              "Execution listeners of type 'end' are not supported by [error] end events"));
    }

    @Test
    public void shouldCompleteCompensationEndEventFromSubprocessWithMultipleExecutionListeners() {
      // given
      final String endEventElementId = "compensation-throw-end-event";
      final Consumer<EventSubProcessBuilder> compensationEventSubprocess =
          eventSubprocess -> eventSubprocess.startEvent().error().endEvent(endEventElementId);

      final BpmnModelInstance process =
          Bpmn.createExecutableProcess(PROCESS_ID)
              .eventSubProcess("event-subprocess", compensationEventSubprocess)
              .startEvent()
              .serviceTask(
                  "A",
                  task ->
                      task.zeebeJobType("A")
                          .boundaryEvent()
                          .compensation(
                              compensation ->
                                  compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
              .serviceTask("C", task -> task.zeebeJobType("C"))
              .done();

      final EndEvent endEvent = process.getModelElementById(endEventElementId);
      endEvent
          .builder()
          .zeebeStartExecutionListener(START_EL_TYPE + "_1")
          .zeebeStartExecutionListener(START_EL_TYPE + "_2")
          .zeebeEndExecutionListener(END_EL_TYPE + "_1")
          .zeebeEndExecutionListener(END_EL_TYPE + "_2")
          .compensateEventDefinition()
          .activityRef("A");

      ENGINE.deployment().withXmlResource(process).deploy();

      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

      ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

      // when
      ENGINE.job().ofInstance(processInstanceKey).withType("C").withErrorCode("error").throwError();

      // then
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      // assert that compensation end event has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getElementId(), Record::getIntent)
          .containsSubsequence(
              tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(endEventElementId, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(endEventElementId, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(endEventElementId, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(endEventElementId, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(endEventElementId, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(endEventElementId, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(endEventElementId, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(endEventElementId, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }
  }
}
