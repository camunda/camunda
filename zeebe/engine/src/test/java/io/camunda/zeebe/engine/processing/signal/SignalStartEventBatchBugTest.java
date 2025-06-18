/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.signal;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Rule;
import org.junit.Test;

public class SignalStartEventBatchBugTest {
  private static final String SIGNAL_NAME_1 = "a";
  private static final String MULTI_INSTANCE_PROCESS_ID = "multi-instance";
  private static final String SIGNAL_RECEIVER_PROCESS_ID = "process";

  /**
   * The amount of entries in the input collection and the maxCommandsInBatch size both play into
   * whether this fails or not. E.g.: input collection size / batch size
   *
   * <p>succeeds: 4/20, 10/15, 20/3
   *
   * <p>fails: 4/21, 10/16, 20/4
   */
  private static final String INPUT_COLLECTION = "[1, 2, 3, 4]"; // works with 3 entries

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition().maxCommandsInBatch(21); // works with 20

  @Test
  public void shouldAlwaysTriggerSignalStartEvent() {
    // given
    final var process = Bpmn.createExecutableProcess(SIGNAL_RECEIVER_PROCESS_ID);
    process.startEvent("none-start").endEvent();
    process.startEvent("signal-start").signal(SIGNAL_NAME_1).endEvent();
    final var process2 =
        Bpmn.createExecutableProcess(MULTI_INSTANCE_PROCESS_ID)
            .startEvent()
            .subProcess(
                "multi",
                subProcessBuilder -> {
                  subProcessBuilder.multiInstance(
                      consumer -> {
                        consumer
                            .zeebeInputCollectionExpression(INPUT_COLLECTION)
                            .zeebeInputElement("index");
                      });
                })
            .embeddedSubProcess()
            .startEvent()
            .endEvent()
            .signal(SIGNAL_NAME_1)
            .done();

    engine.deployment().withXmlResource(process.done()).deploy();
    engine.deployment().withXmlResource(process2).deploy();

    // when
    engine.processInstance().ofBpmnProcessId(MULTI_INSTANCE_PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .limit(MULTI_INSTANCE_PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withBpmnProcessId(SIGNAL_RECEIVER_PROCESS_ID)
                .withElementType(BpmnElementType.START_EVENT))
        .extracting(r -> r.getValue().getBpmnEventType())
        .containsOnly(BpmnEventType.SIGNAL);
  }
}
