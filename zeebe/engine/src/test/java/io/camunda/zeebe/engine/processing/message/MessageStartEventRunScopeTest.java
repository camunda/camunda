/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.TagUtil;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that a run-scoped message publish which starts an instance via a message start event
 * stamps the new root instance with the reserved run-scope tag (ADR 0014, D4 for messages), so the
 * started instance belongs to the publishing run.
 */
public final class MessageStartEventRunScopeTest {

  private static final String MESSAGE_NAME = "start-message";
  private static final String PROCESS_ID = "message-start-process";
  private static final String RUN_A = "runA";

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static final BpmnModelInstance MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .message(m -> m.name(MESSAGE_NAME))
          .endEvent()
          .done();

  @Test
  public void shouldStampRunScopeTagOnMessageStartInstance() {
    // given a message start event process
    ENGINE.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();

    // when a scoped message starts an instance
    ENGINE.message().withName(MESSAGE_NAME).withCorrelationKey("").withRunScope(RUN_A).publish();

    // then the newly started instance is stamped with the reserved run-scope tag
    final var activating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    assertThat(activating.getValue().getTags()).contains(TagUtil.runScopeTag(RUN_A));
  }

  @Test
  public void shouldNotStampTagForUnscopedMessageStart() {
    // given a message start event process
    ENGINE.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();

    // when an unscoped message starts an instance
    ENGINE.message().withName(MESSAGE_NAME).withCorrelationKey("").publish();

    // then the started instance carries no run-scope tag, exactly as today
    final var activating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    assertThat(activating.getValue().getTags())
        .noneMatch(tag -> tag.startsWith(TagUtil.RUN_SCOPE_TAG_PREFIX));
  }
}
