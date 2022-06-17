/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

public class CreateProcessInstanceRejectionTest {

  private static final String PROCESS_ID = "process-id";
  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectCommandIfElementIdIsUnknown() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .manualTask("task")
                .endEvent()
                .done())
        .deploy();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withStartInstruction("task")
        .withStartInstruction("unknown-element")
        .withVariable("x", 1)
        .expectRejection()
        .create();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceCreationIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to create instance of process with start instructions but no element found with id 'unknown-element'.");
  }

  @Test
  public void shouldRejectCommandIfElementIsInsideMultiInstance() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "subprocess",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .manualTask("task-in-multi-instance")
                            .done())
                .multiInstance(m -> m.zeebeInputCollectionExpression("[1,2,3]"))
                .manualTask("task")
                .endEvent()
                .done())
        .deploy();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withStartInstruction("task")
        .withStartInstruction("task-in-multi-instance")
        .withVariable("x", 1)
        .expectRejection()
        .create();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceCreationIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to create instance of process with start instructions but the element with id 'task-in-multi-instance' is inside a multi-instance subprocess. The creation of elements inside a multi-instance subprocess is not supported.");
  }
}
