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
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collection;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CreateProcessInstanceUnsupportedElementTypeTest {

  private static final String PROCESS_ID = "process-id";
  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final BpmnElementType elementType;
  private final String elementId;

  public CreateProcessInstanceUnsupportedElementTypeTest(
      final BpmnElementType elementType, final String elementId) {
    this.elementType = elementType;
    this.elementId = elementId;
  }

  @Test
  public void shouldRejectCommandIfElementTargetsUnsupportedElementType() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent("rootStartEvent")
                .sequenceFlowId("sequenceFlow")
                .subProcess(
                    "subProcess",
                    sp ->
                        sp.embeddedSubProcess()
                            .startEvent("subStartEvent")
                            .manualTask("task")
                            .endEvent())
                .boundaryEvent("boundaryEvent", b -> b.error("ERROR").endEvent())
                .endEvent()
                .done())
        .deploy();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withStartInstruction(elementId)
        .expectRejection()
        .create();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceCreationIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    Assertions.assertThat(rejectionRecord.getRejectionReason())
        .contains(
            "Expected to create instance of process with start instructions but the element with id '%s' targets unsupported element type '%s'. Supported element types are:"
                .formatted(elementId, elementType));
  }

  @Parameters(name = "{index}: {0} - {1}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(
        new Object[][] {
          {BpmnElementType.START_EVENT, "rootStartEvent"},
          {BpmnElementType.START_EVENT, "subStartEvent"},
          {BpmnElementType.SEQUENCE_FLOW, "sequenceFlow"},
          {BpmnElementType.BOUNDARY_EVENT, "boundaryEvent"},
        });
  }
}
