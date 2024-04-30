/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractActivityBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CompensationEventCompatibilityTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String CHILD_PROCESS_ID = "child-process";
  private static final String DECISION_ID = "jedi_or_sith";
  private static final String JOB_TYPE = "job-type";

  private static final List<Scenario> SCENARIOS =
      List.of(
          Scenario.of("undefined task", BpmnElementType.TASK, AbstractFlowNodeBuilder::task),
          Scenario.of(
              "manual task", BpmnElementType.MANUAL_TASK, AbstractFlowNodeBuilder::manualTask),
          Scenario.of(
              "service task",
              BpmnElementType.SERVICE_TASK,
              b -> b.serviceTask().zeebeJobType(JOB_TYPE),
              CompensationEventCompatibilityTest::completeJob),
          Scenario.of(
              "user task (job worker)",
              BpmnElementType.USER_TASK,
              AbstractFlowNodeBuilder::userTask,
              processInstanceKey ->
                  ENGINE
                      .job()
                      .ofInstance(processInstanceKey)
                      .withType(Protocol.USER_TASK_JOB_TYPE)
                      .complete()),
          Scenario.of(
              "user task (native)",
              BpmnElementType.USER_TASK,
              b -> b.userTask().zeebeUserTask(),
              processInstanceKey -> ENGINE.userTask().ofInstance(processInstanceKey).complete()),
          Scenario.of(
              "receive task",
              BpmnElementType.RECEIVE_TASK,
              b ->
                  b.receiveTask()
                      .message(m -> m.name("message").zeebeCorrelationKeyExpression("1")),
              processInstanceKey ->
                  ENGINE.message().withName("message").withCorrelationKey("1").publish()),
          Scenario.of(
              "send task",
              BpmnElementType.SEND_TASK,
              b -> b.sendTask().zeebeJobType(JOB_TYPE),
              CompensationEventCompatibilityTest::completeJob),
          Scenario.of(
              "business rule task (job worker)",
              BpmnElementType.BUSINESS_RULE_TASK,
              b -> b.businessRuleTask().zeebeJobType(JOB_TYPE),
              CompensationEventCompatibilityTest::completeJob),
          Scenario.of(
              "business rule task (DMN)",
              BpmnElementType.BUSINESS_RULE_TASK,
              b ->
                  b.businessRuleTask()
                      .zeebeCalledDecisionId(DECISION_ID)
                      .zeebeResultVariable("result")),
          Scenario.of(
              "script task (job worker)",
              BpmnElementType.SCRIPT_TASK,
              b -> b.scriptTask().zeebeJobType(JOB_TYPE),
              CompensationEventCompatibilityTest::completeJob),
          Scenario.of(
              "script task (FEEL)",
              BpmnElementType.SCRIPT_TASK,
              b -> b.scriptTask().zeebeExpression("true").zeebeResultVariable("result")),
          Scenario.of(
              "subprocess",
              BpmnElementType.SUB_PROCESS,
              b ->
                  b.subProcess(
                      "subprocess",
                      subprocess -> subprocess.embeddedSubProcess().startEvent().endEvent())),
          Scenario.of(
              "call activity",
              BpmnElementType.CALL_ACTIVITY,
              b -> b.callActivity().zeebeProcessId(CHILD_PROCESS_ID)),
          Scenario.of(
              "parallel multi-instance",
              BpmnElementType.MULTI_INSTANCE_BODY,
              b ->
                  b.manualTask()
                      .multiInstance(m -> m.zeebeInputCollectionExpression("[1,2]").parallel())),
          Scenario.of(
              "sequential multi-instance",
              BpmnElementType.MULTI_INSTANCE_BODY,
              b ->
                  b.manualTask()
                      .multiInstance(m -> m.zeebeInputCollectionExpression("[1,2]").sequential())));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final Scenario scenario;

  public CompensationEventCompatibilityTest(final Scenario scenario) {
    this.scenario = scenario;
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> scenarios() {
    return SCENARIOS.stream().map(s -> new Object[] {s}).collect(Collectors.toList());
  }

  @BeforeClass
  public static void deployResources() {
    // child process for call activity
    final BpmnModelInstance childProcess =
        Bpmn.createExecutableProcess(CHILD_PROCESS_ID).startEvent().endEvent().done();

    ENGINE
        .deployment()
        .withXmlResource(childProcess)
        .withXmlClasspathResource("/dmn/decision-table.dmn")
        .deploy();
  }

  @Test
  public void shouldCompensateActivity() {
    // given
    final var processBuilder = Bpmn.createExecutableProcess(PROCESS_ID).startEvent();
    // add compensation activity
    final var compensationActivity = scenario.builder.apply(processBuilder);
    // add compensation handler
    compensationActivity
        .boundaryEvent()
        .compensation(compensation -> compensation.task("compensation-handler"));
    // add compensation throw event
    final BpmnModelInstance process =
        compensationActivity.endEvent().compensateEventDefinition().done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    scenario.completionAction.accept(processInstanceKey);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getBpmnEventType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                scenario.bpmnElementType,
                scenario.bpmnEventType,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnElementType.TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldInvokeCompensationHandler() {
    // given
    final var processBuilder = Bpmn.createExecutableProcess(PROCESS_ID).startEvent();
    // add compensation activity
    final var compensationActivity = processBuilder.task("compensation-activity");
    // add compensation handler
    compensationActivity.boundaryEvent().compensation(scenario.builder::apply);
    // add compensation throw event
    final BpmnModelInstance process =
        compensationActivity.endEvent().compensateEventDefinition().done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    scenario.completionAction.accept(processInstanceKey);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getBpmnEventType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                scenario.bpmnElementType,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                scenario.bpmnElementType,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  private static void completeJob(final long processInstanceKey) {
    ENGINE.job().ofInstance(processInstanceKey).withType(JOB_TYPE).complete();
  }

  private record Scenario(
      String name,
      BpmnElementType bpmnElementType,
      BpmnEventType bpmnEventType,
      Function<AbstractFlowNodeBuilder<?, ?>, AbstractActivityBuilder<?, ?>> builder,
      LongConsumer completionAction) {

    @Override
    public String toString() {
      return name;
    }

    private static Scenario of(
        final String name,
        final BpmnElementType bpmnElementType,
        final Function<AbstractFlowNodeBuilder<?, ?>, AbstractActivityBuilder<?, ?>> builder) {
      return of(name, bpmnElementType, builder, key -> {});
    }

    private static Scenario of(
        final String name,
        final BpmnElementType bpmnElementType,
        final Function<AbstractFlowNodeBuilder<?, ?>, AbstractActivityBuilder<?, ?>> builder,
        final LongConsumer completionAction) {

      final var bpmnEventType =
          bpmnElementType == BpmnElementType.RECEIVE_TASK
              ? BpmnEventType.MESSAGE
              : BpmnEventType.UNSPECIFIED;
      return new Scenario(name, bpmnElementType, bpmnEventType, builder, completionAction);
    }
  }
}
