/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractActivityBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class BoundaryEventElementTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter public ElementWithBoundaryEventBuilder elementBuilder;

  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return buildersAsParameters();
  }

  private BpmnModelInstance process(final ElementWithBoundaryEventBuilder elementBuilder) {
    final var startEventBuilder = Bpmn.createExecutableProcess(PROCESS_ID).startEvent();

    final var processWithElementBuilder = elementBuilder.build(startEventBuilder);

    return processWithElementBuilder.boundaryEvent().timerWithDuration("PT1H").endEvent().done();
  }

  @Test
  public void shouldNotActivateBoundaryEventIfScopeIsTerminating() {
    // given
    ENGINE.deployment().withXmlResource(process(elementBuilder)).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(elementBuilder.elementType)
        .await();

    final var timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when trigger the boundary event and cancel the process instance concurrently
    ENGINE.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.CANCEL, new ProcessInstanceRecord())
            .key(processInstanceKey),
        RecordToWrite.command()
            .timer(TimerIntent.TRIGGER, timerCreated.getValue())
            .key(timerCreated.getKey()));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(elementBuilder.elementType, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(elementBuilder.elementType, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED))
        .doesNotContain(
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING));
  }

  private static Collection<Object[]> buildersAsParameters() {
    return builders().map(builder -> new Object[] {builder}).collect(Collectors.toList());
  }

  private static Stream<ElementWithBoundaryEventBuilder> builders() {
    return Stream.of(
        // -------------------------------
        new ElementWithBoundaryEventBuilder(
            BpmnElementType.SERVICE_TASK,
            process -> process.serviceTask("task", t -> t.zeebeJobType("task"))),
        // -------------------------------
        new ElementWithBoundaryEventBuilder(
            BpmnElementType.RECEIVE_TASK,
            process ->
                process.receiveTask(
                    "task",
                    r -> r.message(m -> m.name("wait").zeebeCorrelationKeyExpression("123")))),
        // -------------------------------
        new ElementWithBoundaryEventBuilder(
            BpmnElementType.BUSINESS_RULE_TASK,
            process -> process.businessRuleTask("task", b -> b.zeebeJobType("task"))),
        // -------------------------------
        new ElementWithBoundaryEventBuilder(
            BpmnElementType.USER_TASK, process -> process.userTask("task")),
        // -------------------------------
        new ElementWithBoundaryEventBuilder(
            BpmnElementType.SCRIPT_TASK,
            process -> process.scriptTask("task", s -> s.zeebeJobType("task"))),
        // -------------------------------
        new ElementWithBoundaryEventBuilder(
            BpmnElementType.SEND_TASK,
            process -> process.sendTask("task", s -> s.zeebeJobType("task"))),
        // -------------------------------
        new ElementWithBoundaryEventBuilder(
            BpmnElementType.SUB_PROCESS,
            process ->
                process.subProcess(
                    "subprocess",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .serviceTask("task", t -> t.zeebeJobType("task"))
                            .endEvent())),
        // -------------------------------
        new ElementWithBoundaryEventBuilder(
            BpmnElementType.MULTI_INSTANCE_BODY,
            process ->
                process
                    .serviceTask("task", t -> t.zeebeJobType("task"))
                    .multiInstance(m -> m.parallel().zeebeInputCollectionExpression("[1,2,3]"))),
        // -------------------------------
        new ElementWithBoundaryEventBuilder(
            BpmnElementType.CALL_ACTIVITY,
            process -> process.callActivity("call", c -> c.zeebeProcessId(PROCESS_ID))));
  }

  private record ElementWithBoundaryEventBuilder(
      BpmnElementType elementType,
      Function<AbstractFlowNodeBuilder<?, ?>, AbstractActivityBuilder<?, ?>> builder) {

    public AbstractActivityBuilder<?, ?> build(final AbstractFlowNodeBuilder<?, ?> process) {
      return builder.apply(process);
    }

    @Override
    public String toString() {
      return elementType.name();
    }
  }
}
