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

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.IntermediateCatchEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
public class ExecutionListenerIntermediateCatchEventElementTest {

  @RunWith(Parameterized.class)
  public static class ParametrizedTest {

    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

    @Rule
    public final RecordingExporterTestWatcher recordingExporterTestWatcher =
        new RecordingExporterTestWatcher();

    @Parameter public IntermediateCatchEventTestScenario scenario;

    @Parameters(name = "{index}: {0}")
    public static Collection<Object[]> intermediateCatchEventParameters() {
      return Arrays.asList(
          new Object[][] {
            {
              IntermediateCatchEventTestScenario.of(
                  "message",
                  Map.of("key", "key-1"),
                  e -> e.message(m -> m.name("my_message").zeebeCorrelationKeyExpression("key")),
                  ignore -> {
                    ENGINE.message().withName("my_message").withCorrelationKey("key-1").publish();
                    RecordingExporter.messageRecords()
                        .withName("my_message")
                        .withIntent(MessageIntent.PUBLISHED)
                        .await();
                  })
            },
            {
              IntermediateCatchEventTestScenario.of(
                  "timer",
                  Collections.emptyMap(),
                  e -> e.timerWithDate("=now() + duration(\"PT15S\")"),
                  pik -> {
                    ENGINE.increaseTime(Duration.ofSeconds(15));
                    RecordingExporter.timerRecords()
                        .withProcessInstanceKey(pik)
                        .withIntent(TimerIntent.TRIGGERED)
                        .await();
                  })
            },
            {
              IntermediateCatchEventTestScenario.of(
                  "signal",
                  Collections.emptyMap(),
                  e -> e.signal("my_signal"),
                  ignore -> ENGINE.signal().withSignalName("my_signal").broadcast())
            }
          });
    }

    @Test
    public void shouldCompleteIntermediateCatchEventWithMultipleExecutionListeners() {
      // given
      final var modelInstance =
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent()
              .intermediateCatchEvent(scenario.name, c -> scenario.builderFunction.apply(c))
              .zeebeStartExecutionListener(START_EL_TYPE + "_1")
              .zeebeStartExecutionListener(START_EL_TYPE + "_2")
              .zeebeEndExecutionListener(END_EL_TYPE + "_1")
              .zeebeEndExecutionListener(END_EL_TYPE + "_2")
              .manualTask()
              .endEvent()
              .done();

      final long processInstanceKey =
          createProcessInstance(ENGINE, modelInstance, scenario.processVariables);

      // when: complete the start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // trigger event
      scenario.intermediateCatchEventTrigger.accept(processInstanceKey);

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
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldAccessInputMappingVariablesInStartExecutionListener() {
      // given
      final var modelInstance =
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent()
              .intermediateCatchEvent(
                  scenario.name,
                  c -> scenario.builderFunction.apply(c).zeebeInput("=5+4", "inputVar"))
              .zeebeStartExecutionListener(START_EL_TYPE)
              .endEvent()
              .done();

      // when: deploy process
      final long processInstanceKey =
          createProcessInstance(ENGINE, modelInstance, scenario.processVariables);

      // then: `inputVar` variable accessible in start EL
      final Optional<JobRecordValue> startElJobActivated =
          ENGINE.jobs().withType(START_EL_TYPE).activate().getValue().getJobs().stream()
              .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
              .findFirst();

      assertThat(startElJobActivated)
          .hasValueSatisfying(job -> assertThat(job.getVariables()).contains(entry("inputVar", 9)));
    }

    @Test
    public void shouldAllowEndListenerToAccessStartListenerVariable() {
      // given
      final var modelInstance =
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent()
              .intermediateCatchEvent(scenario.name, c -> scenario.builderFunction.apply(c))
              .zeebeStartExecutionListener(START_EL_TYPE)
              .zeebeEndExecutionListener(END_EL_TYPE)
              .manualTask()
              .endEvent()
              .done();

      final long processInstanceKey =
          createProcessInstance(ENGINE, modelInstance, scenario.processVariables);

      // when: complete start EL with `bar` variable
      ENGINE
          .job()
          .ofInstance(processInstanceKey)
          .withType(START_EL_TYPE)
          .withVariable("bar", 448)
          .complete();

      // trigger event
      scenario.intermediateCatchEventTrigger.accept(processInstanceKey);

      // then: `bar` variable accessible in end EL
      final Optional<JobRecordValue> jobActivated =
          ENGINE.jobs().withType(END_EL_TYPE).activate().getValue().getJobs().stream()
              .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
              .findFirst();

      assertThat(jobActivated)
          .hasValueSatisfying(job -> assertThat(job.getVariables()).contains(entry("bar", 448)));
    }

    @Test
    public void shouldAllowSubsequentElementToAccessVariableProducedByCatchEventEndListenerJob() {
      // given: deploy process with catch event having end EL and service task following it
      final var modelInstance =
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent()
              .intermediateCatchEvent(scenario.name, c -> scenario.builderFunction.apply(c))
              .zeebeEndExecutionListener(END_EL_TYPE)
              .serviceTask("subsequent_task", tb -> tb.zeebeJobType("subsequent_service_task"))
              .endEvent()
              .done();

      final long processInstanceKey =
          createProcessInstance(ENGINE, modelInstance, scenario.processVariables);

      // trigger event
      scenario.intermediateCatchEventTrigger.accept(processInstanceKey);

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
      final var modelInstance =
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent()
              .intermediateCatchEvent(scenario.name, c -> scenario.builderFunction.apply(c))
              .zeebeStartExecutionListener(START_EL_TYPE)
              .manualTask()
              .endEvent()
              .done();

      final long processInstanceKey =
          createProcessInstance(ENGINE, modelInstance, scenario.processVariables);
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

    private record IntermediateCatchEventTestScenario(
        String name,
        Map<String, Object> processVariables,
        UnaryOperator<IntermediateCatchEventBuilder> builderFunction,
        Consumer<Long> intermediateCatchEventTrigger) {

      @Override
      public String toString() {
        return name;
      }

      private static IntermediateCatchEventTestScenario of(
          final String name,
          final Map<String, Object> processVariables,
          final UnaryOperator<IntermediateCatchEventBuilder> builderFunction,
          final Consumer<Long> eventTrigger) {
        return new IntermediateCatchEventTestScenario(
            name, processVariables, builderFunction, eventTrigger);
      }
    }
  }

  public static class ExtraTests {
    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

    @Rule
    public final RecordingExporterTestWatcher recordingExporterTestWatcher =
        new RecordingExporterTestWatcher();

    @Test
    public void shouldCompleteLinkEventWithMultipleExecutionListeners() {
      // given
      final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(PROCESS_ID);
      processBuilder
          .startEvent()
          .intermediateThrowEvent(
              "throw",
              b ->
                  b.link("linkA")
                      .zeebeStartExecutionListener(START_EL_TYPE + "_throw_1")
                      .zeebeStartExecutionListener(START_EL_TYPE + "_throw_2")
                      .zeebeEndExecutionListener(END_EL_TYPE + "_throw_1"));
      final BpmnModelInstance modelInstance =
          processBuilder
              .linkCatchEvent("catch")
              .link("linkA")
              .zeebeStartExecutionListener(START_EL_TYPE + "_catch_1")
              .zeebeEndExecutionListener(END_EL_TYPE + "_catch_1")
              .zeebeEndExecutionListener(END_EL_TYPE + "_catch_2")
              .manualTask()
              .endEvent()
              .done();

      final long processInstanceKey = createProcessInstance(ENGINE, modelInstance);

      // when: complete the execution listener jobs for link throw events
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_throw_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_throw_2").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_throw_1").complete();

      // complete the execution listener jobs for link catch events
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_catch_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_catch_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_catch_2").complete();

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
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }
  }
}
