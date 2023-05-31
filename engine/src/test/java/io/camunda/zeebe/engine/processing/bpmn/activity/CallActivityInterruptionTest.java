/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.CallActivityBuilder;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CallActivityInterruptionTest {
  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          // Disable batch processing. Interrupting behaviour is only reproducible if
          // termination of call activity does not finish within one batch.
          .maxCommandsInBatch(1);

  private static final String PROCESS_ID_PARENT = "wf-parent";
  private static final String PROCESS_ID_CHILD = "wf-child";

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();
  private String jobType;

  @Before
  public void init() {
    jobType = helper.getJobType();

    final var parentProcess = parentProcess(CallActivityBuilder::done);

    final var childProcess =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD)
            .startEvent()
            .serviceTask("child-task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();

    ENGINE
        .deployment()
        .withXmlResource("wf-parent.bpmn", parentProcess)
        .withXmlResource("wf-child.bpmn", childProcess)
        .deploy();
  }

  @Test
  public void shouldTriggerBoundaryEventIfChildIsCompleting() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "parent-wf.bpmn",
            parentProcess(
                callActivity ->
                    callActivity
                        .boundaryEvent()
                        .cancelActivity(true)
                        .timerWithDuration("PT1M")
                        .endEvent()))
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    final var timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var childTaskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    // when trigger the boundary event and complete the child instance concurrently
    ENGINE.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, childTaskActivated.getValue())
            .key(childTaskActivated.getKey()),
        RecordToWrite.command()
            .timer(TimerIntent.TRIGGER, timerCreated.getValue())
            .key(timerCreated.getKey()));

    // then
    assertThat(
            RecordingExporter.records()
                .onlyEvents()
                .betweenProcessInstance(processInstanceKey)
                .processInstanceRecords())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  private static BpmnModelInstance parentProcess(final Consumer<CallActivityBuilder> consumer) {
    final var builder =
        Bpmn.createExecutableProcess(PROCESS_ID_PARENT)
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId(PROCESS_ID_CHILD));

    consumer.accept(builder);

    return builder.endEvent().done();
  }
}
