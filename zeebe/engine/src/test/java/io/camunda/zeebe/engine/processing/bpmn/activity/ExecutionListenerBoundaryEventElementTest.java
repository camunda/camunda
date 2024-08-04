/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.engine.processing.bpmn.activity.ExecutionListenerTest.END_EL_TYPE;
import static io.camunda.zeebe.engine.processing.bpmn.activity.ExecutionListenerTest.PROCESS_ID;
import static io.camunda.zeebe.engine.processing.bpmn.activity.ExecutionListenerTest.SERVICE_TASK_TYPE;
import static io.camunda.zeebe.engine.processing.bpmn.activity.ExecutionListenerTest.START_EL_TYPE;
import static io.camunda.zeebe.engine.processing.bpmn.activity.ExecutionListenerTest.createProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.processing.deployment.model.validation.ExpectedValidationResult;
import io.camunda.zeebe.engine.processing.deployment.model.validation.ProcessValidationUtil;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AbstractActivityBuilder;
import io.camunda.zeebe.model.bpmn.builder.BoundaryEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
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
public class ExecutionListenerBoundaryEventElementTest {

  @RunWith(Parameterized.class)
  public static class ParametrizedTest {

    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

    private static final String BOUNDARY_OWNER_ID = "boundary_event_owner";

    @Rule
    public final RecordingExporterTestWatcher recordingExporterTestWatcher =
        new RecordingExporterTestWatcher();

    @Parameter public BoundaryEventTestScenario scenario;

    @Parameters(name = "{index}: {0}")
    public static Collection<Object[]> boundaryEventParameters() {
      final Function<StartEventBuilder, AbstractActivityBuilder<?, ?>> processWithServiceTask =
          startEvent ->
              startEvent.serviceTask(BOUNDARY_OWNER_ID, t -> t.zeebeJobType(SERVICE_TASK_TYPE));

      return Arrays.asList(
          new Object[][] {
            {
              BoundaryEventTestScenario.of(
                  "error",
                  processWithServiceTask,
                  e -> e.error("err"),
                  pik ->
                      ENGINE
                          .job()
                          .ofInstance(pik)
                          .withType(SERVICE_TASK_TYPE)
                          .withErrorCode("err")
                          .throwError())
            },
            {
              BoundaryEventTestScenario.of(
                  "message",
                  processWithServiceTask,
                  e -> e.message(m -> m.name("my_message").zeebeCorrelationKey("=\"key-1\"")),
                  ignore ->
                      ENGINE.message().withName("my_message").withCorrelationKey("key-1").publish())
            },
            {
              BoundaryEventTestScenario.of(
                  "signal",
                  processWithServiceTask,
                  e -> e.signal("my_signal"),
                  ignore -> ENGINE.signal().withSignalName("my_signal").broadcast())
            },
            {
              BoundaryEventTestScenario.of(
                  "timer",
                  processWithServiceTask,
                  e -> e.timerWithDate("=now() + duration(\"PT15S\")"),
                  ignore -> ENGINE.increaseTime(Duration.ofSeconds(15)))
            },
            {
              BoundaryEventTestScenario.of(
                  "escalation",
                  se ->
                      se.subProcess(
                          BOUNDARY_OWNER_ID,
                          s ->
                              s.embeddedSubProcess()
                                  .startEvent()
                                  .endEvent("sub_end_event", e -> e.escalation("my_escalation"))),
                  e -> e.escalation("my_escalation"),
                  pik -> {})
            }
          });
    }

    @Test
    public void shouldCompleteBoundaryEventWithMultipleEndExecutionListeners() {
      // given
      final var boundaryEventElemId = "boundary_%s_event".formatted(scenario.name);
      final var modelInstance =
          scenario
              .processBuilder
              .apply(Bpmn.createExecutableProcess(PROCESS_ID).startEvent())
              .boundaryEvent(
                  boundaryEventElemId, b -> scenario.boundaryEventBuilderFunction.apply(b))
              .zeebeEndExecutionListener(END_EL_TYPE + "_1")
              .zeebeEndExecutionListener(END_EL_TYPE + "_2")
              .endEvent("boundary_end")
              .moveToActivity(BOUNDARY_OWNER_ID)
              .endEvent("main_end")
              .done();

      final long processInstanceKey = createProcessInstance(ENGINE, modelInstance);

      // when: trigger boundary event
      scenario.triggerEvent.accept(processInstanceKey);

      // complete the end execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      // assert the event has completed as expected
      final BpmnElementType element = BpmnElementType.BOUNDARY_EVENT;
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
              tuple(boundaryEventElemId, element, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(boundaryEventElemId, element, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(
                  boundaryEventElemId, element, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  boundaryEventElemId, element, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(boundaryEventElemId, element, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  "boundary_end",
                  BpmnElementType.END_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldNotDeployProcessWithBoundaryEventWithStartExecutionListeners() {
      // given
      final var boundaryEventElemId = "boundary_%s_event".formatted(scenario.name);
      final var modelInstance =
          scenario
              .processBuilder
              .apply(Bpmn.createExecutableProcess(PROCESS_ID).startEvent())
              .boundaryEvent(
                  boundaryEventElemId, b -> scenario.boundaryEventBuilderFunction.apply(b))
              .zeebeStartExecutionListener(START_EL_TYPE)
              .endEvent("boundary_end")
              .moveToActivity(BOUNDARY_OWNER_ID)
              .endEvent("main_end")
              .done();

      // when - then
      ProcessValidationUtil.validateProcess(
          modelInstance,
          ExpectedValidationResult.expect(
              BoundaryEvent.class,
              "Execution listeners of type 'start' are not supported by boundary events"));
    }

    private record BoundaryEventTestScenario(
        String name,
        Function<StartEventBuilder, AbstractActivityBuilder<?, ?>> processBuilder,
        UnaryOperator<BoundaryEventBuilder> boundaryEventBuilderFunction,
        Consumer<Long> triggerEvent) {

      @Override
      public String toString() {
        return name;
      }

      private static BoundaryEventTestScenario of(
          final String name,
          final Function<StartEventBuilder, AbstractActivityBuilder<?, ?>> processBuilder,
          final UnaryOperator<BoundaryEventBuilder> boundaryEventBuilderFunction,
          final Consumer<Long> triggerEvent) {
        return new BoundaryEventTestScenario(
            name, processBuilder, boundaryEventBuilderFunction, triggerEvent);
      }
    }
  }

  public static class ExtraTests {
    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

    @Rule
    public final RecordingExporterTestWatcher recordingExporterTestWatcher =
        new RecordingExporterTestWatcher();

    @Test
    public void shouldNotDeployProcessWithCompensationBoundaryEventWithExecutionListeners() {
      // given
      final var modelInstance =
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent()
              .serviceTask("service_task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
              .boundaryEvent(
                  "compensation_boundary",
                  b ->
                      b.compensation(
                          c ->
                              c.serviceTask("undo_service_task")
                                  .zeebeJobType("undo_service_task")
                                  .done()))
              .zeebeStartExecutionListener(END_EL_TYPE + "_1")
              .zeebeEndExecutionListener(END_EL_TYPE + "_2")
              .moveToActivity("service_task")
              .intermediateThrowEvent(
                  "boundary_throw",
                  ic -> ic.compensateEventDefinition().activityRef("service_task"))
              .manualTask("manual_task")
              .endEvent("main_end")
              .done();

      // when - then
      ProcessValidationUtil.validateProcess(
          modelInstance,
          ExpectedValidationResult.expect(
              BoundaryEvent.class,
              "Execution listeners of type 'start' and 'end' are not supported by [compensation] boundary events"));
    }
  }
}
