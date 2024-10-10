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
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.createProcessInstance;
import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.processing.deployment.model.validation.ExpectedValidationResult;
import io.camunda.zeebe.engine.processing.deployment.model.validation.ProcessValidationUtil;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AbstractBpmnModelElementBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractGatewayBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.model.bpmn.instance.Gateway;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Enclosed.class)
public class ExecutionListenerGatewayElementsTest {
  @RunWith(Parameterized.class)
  public static class ParametrizedTest {

    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

    private static final String GATEWAY_ELEMENT_ID = "gateway_element_under_test";

    @Rule
    public final RecordingExporterTestWatcher recordingExporterTestWatcher =
        new RecordingExporterTestWatcher();

    @Parameter public GatewayTestScenario scenario;

    @Parameters(name = "{index}: {0}")
    public static Collection<Object[]> parameters() {
      return Arrays.asList(
          new Object[][] {
            {
              GatewayTestScenario.of(
                  "exclusive",
                  BpmnElementType.EXCLUSIVE_GATEWAY,
                  Map.of("foo", 1),
                  start -> start.exclusiveGateway(GATEWAY_ELEMENT_ID),
                  process ->
                      process
                          .sequenceFlowId("to_end_a")
                          .conditionExpression("foo < 5")
                          .serviceTask(SERVICE_TASK_TYPE, t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                          .endEvent("end_a")
                          .moveToLastExclusiveGateway()
                          .sequenceFlowId("to_end_b")
                          .defaultFlow()
                          .endEvent("end_b"))
            },
            {
              GatewayTestScenario.of(
                  "inclusive",
                  BpmnElementType.INCLUSIVE_GATEWAY,
                  Map.of("foo", 1),
                  start -> start.inclusiveGateway(GATEWAY_ELEMENT_ID),
                  process ->
                      process
                          .sequenceFlowId("to_end_a")
                          .conditionExpression("foo < 5")
                          .serviceTask(SERVICE_TASK_TYPE, t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                          .endEvent("end_a")
                          .moveToLastInclusiveGateway()
                          .sequenceFlowId("to_end_b")
                          .defaultFlow()
                          .endEvent("end_b"))
            },
            {
              GatewayTestScenario.of(
                  "parallel",
                  BpmnElementType.PARALLEL_GATEWAY,
                  Map.of("foo", 1),
                  start -> start.parallelGateway(GATEWAY_ELEMENT_ID),
                  process ->
                      process
                          .serviceTask(SERVICE_TASK_TYPE, t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                          .endEvent("end_a")
                          .moveToLastGateway()
                          .manualTask("manual_task")
                          .endEvent("end_b"))
            },
            {
              GatewayTestScenario.of(
                  "event-based",
                  BpmnElementType.EVENT_BASED_GATEWAY,
                  Collections.emptyMap(),
                  start -> start.eventBasedGateway(GATEWAY_ELEMENT_ID),
                  process ->
                      process
                          .intermediateCatchEvent("signal", e -> e.signal("my_signal"))
                          .serviceTask(SERVICE_TASK_TYPE, t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                          .endEvent("end_signal")
                          .moveToLastGateway()
                          .intermediateCatchEvent(
                              "message",
                              e ->
                                  e.message(
                                      m -> m.name("my_message").zeebeCorrelationKey("=\"my_key\"")))
                          .endEvent("end_message"),
                  () -> ENGINE.signal().withSignalName("my_signal").broadcast())
            }
          });
    }

    @Test
    public void shouldCompleteGatewayElementWithMultipleStartExecutionListeners() {
      // given
      final var modelInstance =
          scenario
              .processBuilder
              .apply(
                  scenario
                      .gatewayBuilderFunction
                      .apply(Bpmn.createExecutableProcess(PROCESS_ID).startEvent("start"))
                      .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                      .zeebeStartExecutionListener(START_EL_TYPE + "_2"))
              .done();

      final long processInstanceKey =
          createProcessInstance(ENGINE, modelInstance, scenario.variables);

      // when: complete `start` execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // process gateway element
      scenario.gatewayProcessor.run();

      // complete service task on the chosen flow
      ENGINE.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_TYPE).complete();

      // assert the process with gateway and multiple start EL has completed as expected
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
                  GATEWAY_ELEMENT_ID,
                  scenario.elementType,
                  ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(
                  GATEWAY_ELEMENT_ID,
                  scenario.elementType,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  GATEWAY_ELEMENT_ID,
                  scenario.elementType,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  GATEWAY_ELEMENT_ID,
                  scenario.elementType,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  GATEWAY_ELEMENT_ID,
                  scenario.elementType,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  SERVICE_TASK_TYPE,
                  BpmnElementType.SERVICE_TASK,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldNotDeployProcessWithGatewayWithEndExecutionListeners() {
      // given
      final var modelInstance =
          scenario
              .processBuilder
              .apply(
                  scenario
                      .gatewayBuilderFunction
                      .apply(Bpmn.createExecutableProcess(PROCESS_ID).startEvent("start"))
                      .zeebeStartExecutionListener(START_EL_TYPE)
                      .zeebeExecutionListener(b -> b.end().type(END_EL_TYPE)))
              .done();

      // when - then
      ProcessValidationUtil.validateProcess(
          modelInstance,
          ExpectedValidationResult.expect(
              Gateway.class,
              "Execution listeners of type 'end' are not supported by gateway element"));
    }

    @Test
    public void shouldCancelActiveStartElJobAfterProcessInstanceCancellation() {
      // given
      final var modelInstance =
          scenario
              .processBuilder
              .apply(
                  scenario
                      .gatewayBuilderFunction
                      .apply(Bpmn.createExecutableProcess(PROCESS_ID).startEvent("start"))
                      .zeebeStartExecutionListener(START_EL_TYPE))
              .done();

      final long processInstanceKey =
          createProcessInstance(ENGINE, modelInstance, scenario.variables);
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
                  .findFirst())
          .hasValueSatisfying(r -> assertThat(r.getValue()).hasType(START_EL_TYPE));
    }

    @Test
    public void shouldResolveIncidentOnGatewayElementAndCreateStartExecutionListenerJob() {
      final var invalidExpression = "missing_var";
      final var modelInstance =
          scenario
              .processBuilder
              .apply(
                  scenario
                      .gatewayBuilderFunction
                      .apply(Bpmn.createExecutableProcess(PROCESS_ID).startEvent("start"))
                      .zeebeExecutionListener(l -> l.start().typeExpression(invalidExpression)))
              .done();

      final long processInstanceKey =
          createProcessInstance(ENGINE, modelInstance, scenario.variables);

      final var incident =
          RecordingExporter.incidentRecords(IncidentIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .withElementId(GATEWAY_ELEMENT_ID)
              .withErrorType(ErrorType.EXTRACT_VALUE_ERROR)
              .getFirst();

      // when
      ENGINE
          .variables()
          .ofScope(processInstanceKey)
          .withDocument(Map.of(invalidExpression, START_EL_TYPE))
          .update();
      ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

      // then
      assertThat(
              jobRecords(JobIntent.CREATED)
                  .withProcessInstanceKey(processInstanceKey)
                  .withJobKind(JobKind.EXECUTION_LISTENER)
                  .findFirst())
          .hasValueSatisfying(
              r ->
                  assertThat(r.getValue())
                      .describedAs(
                          "Expected to successfully resolve the incident and create start execution listener job")
                      .hasElementId(GATEWAY_ELEMENT_ID)
                      .hasType(START_EL_TYPE));
    }

    private record GatewayTestScenario(
        String name,
        BpmnElementType elementType,
        Map<String, Object> variables,
        Function<StartEventBuilder, AbstractGatewayBuilder<?, ?>> gatewayBuilderFunction,
        Function<AbstractFlowNodeBuilder<?, ?>, AbstractBpmnModelElementBuilder<?, ?>>
            processBuilder,
        Runnable gatewayProcessor) {

      @Override
      public String toString() {
        return name;
      }

      private static GatewayTestScenario of(
          final String name,
          final BpmnElementType elementType,
          final Map<String, Object> variables,
          final Function<StartEventBuilder, AbstractGatewayBuilder<?, ?>> gatewayBuilderFunction,
          final Function<AbstractFlowNodeBuilder<?, ?>, AbstractBpmnModelElementBuilder<?, ?>>
              processBuilder,
          final Runnable gatewayProcessor) {
        return new GatewayTestScenario(
            name, elementType, variables, gatewayBuilderFunction, processBuilder, gatewayProcessor);
      }

      private static GatewayTestScenario of(
          final String name,
          final BpmnElementType elementType,
          final Map<String, Object> variables,
          final Function<StartEventBuilder, AbstractGatewayBuilder<?, ?>> gatewayBuilderFunction,
          final Function<AbstractFlowNodeBuilder<?, ?>, AbstractBpmnModelElementBuilder<?, ?>>
              processBuilder) {
        return new GatewayTestScenario(
            name, elementType, variables, gatewayBuilderFunction, processBuilder, () -> {});
      }
    }
  }

  public static class ExtraTests {
    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

    @Rule
    public final RecordingExporterTestWatcher recordingExporterTestWatcher =
        new RecordingExporterTestWatcher();

    @Test
    public void shouldSetVariableInStartListenerForExclusiveGatewayCondition() {
      // given
      final var modelInstance =
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent("start")
              .exclusiveGateway("xor")
              .zeebeStartExecutionListener(START_EL_TYPE)
              .sequenceFlowId("to_end_a")
              .conditionExpression("foo < 5")
              .endEvent("end_a")
              .moveToLastExclusiveGateway()
              .sequenceFlowId("to_end_b")
              .defaultFlow()
              .endEvent("end_b")
              .done();

      final long processInstanceKey = createProcessInstance(ENGINE, modelInstance);

      // when: complete `start` execution listener jobs with `foo` variable
      ENGINE
          .job()
          .ofInstance(processInstanceKey)
          .withType(START_EL_TYPE)
          .withVariable("foo", 55)
          .complete();

      // assert the process instance has completed as expected,
      // and default `to_end_b` flow was taken
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
              tuple("start", BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  "xor",
                  BpmnElementType.EXCLUSIVE_GATEWAY,
                  ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(
                  "xor",
                  BpmnElementType.EXCLUSIVE_GATEWAY,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  "xor",
                  BpmnElementType.EXCLUSIVE_GATEWAY,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  "xor",
                  BpmnElementType.EXCLUSIVE_GATEWAY,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  "to_end_b",
                  BpmnElementType.SEQUENCE_FLOW,
                  ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
              tuple("end_b", BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldSetVariableInStartListenerForMessageEventAfterEventBasedGatewayElement() {
      // given
      final var modelInstance =
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent("start")
              .eventBasedGateway("event_gateway")
              .zeebeStartExecutionListener(START_EL_TYPE)
              .intermediateCatchEvent("signal", e -> e.signal("my_signal"))
              .endEvent("end_signal")
              .moveToLastGateway()
              .intermediateCatchEvent(
                  "message",
                  e ->
                      e.message(m -> m.name("my_message").zeebeCorrelationKeyExpression("key_var")))
              .endEvent("end_message")
              .done();

      final long processInstanceKey = createProcessInstance(ENGINE, modelInstance);

      // when: complete `start` execution listener jobs with `key_var` variable
      ENGINE
          .job()
          .ofInstance(processInstanceKey)
          .withType(START_EL_TYPE)
          .withVariable("key_var", "my_key")
          .complete();

      // trigger message event
      ENGINE.message().withName("my_message").withCorrelationKey("my_key").publish();

      // assert the process instance has completed as expected, and `to_end_a` flow was taken
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
              tuple("start", BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  "event_gateway",
                  BpmnElementType.EVENT_BASED_GATEWAY,
                  ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(
                  "event_gateway",
                  BpmnElementType.EVENT_BASED_GATEWAY,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  "event_gateway",
                  BpmnElementType.EVENT_BASED_GATEWAY,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  "event_gateway",
                  BpmnElementType.EVENT_BASED_GATEWAY,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  "end_message",
                  BpmnElementType.END_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED))
          .doesNotContain(
              tuple(
                  "end_signal",
                  BpmnElementType.END_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldSetVariableInStartListenerForSequenceFlowConditionAfterInclusiveGateway() {
      // given
      final var modelInstance =
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent("start")
              .inclusiveGateway("fork")
              .zeebeStartExecutionListener(START_EL_TYPE)
              .sequenceFlowId("to_end_a")
              .conditionExpression("contains(str,\"a\")")
              .endEvent("end_a")
              .moveToLastInclusiveGateway()
              .sequenceFlowId("to_end_b")
              .conditionExpression("contains(str,\"b\")")
              .endEvent("end_b")
              .moveToLastInclusiveGateway()
              .sequenceFlowId("to_end_c")
              .conditionExpression("contains(str,\"c\")")
              .endEvent("end_c")
              .done();

      final long processInstanceKey = createProcessInstance(ENGINE, modelInstance);

      // when: complete `start` execution listener jobs with `str` variable
      ENGINE
          .job()
          .ofInstance(processInstanceKey)
          .withType(START_EL_TYPE)
          .withVariable("str", "ac")
          .complete();

      // assert the process instance has completed as expected
      // and `to_end_a` & `to_end_c` flows were taken
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
              tuple("start", BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  "fork",
                  BpmnElementType.INCLUSIVE_GATEWAY,
                  ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(
                  "fork",
                  BpmnElementType.INCLUSIVE_GATEWAY,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  "fork",
                  BpmnElementType.INCLUSIVE_GATEWAY,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  "fork",
                  BpmnElementType.INCLUSIVE_GATEWAY,
                  ProcessInstanceIntent.ELEMENT_COMPLETED))
          .containsSubsequence(
              tuple(
                  "to_end_a",
                  BpmnElementType.SEQUENCE_FLOW,
                  ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
              tuple("end_a", BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED))
          .containsSubsequence(
              tuple(
                  "to_end_c",
                  BpmnElementType.SEQUENCE_FLOW,
                  ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
              tuple("end_c", BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED))
          .doesNotContain(
              tuple(
                  "to_end_b",
                  BpmnElementType.SEQUENCE_FLOW,
                  ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
              tuple("end_b", BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }
  }
}
