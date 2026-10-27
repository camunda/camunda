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
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.TagUtil;
import java.time.Duration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Single-partition run-scope message tests that exercise the buffered-message path: {@code
 * MessageCorrelator#runScopeMatches}, which runs when a message is published before the catch event
 * subscribes (publish-before-subscribe), as opposed to the correlate-time filter in {@code
 * MessageCorrelateBehavior}.
 */
public final class MessageCorrelationRunScopeTest {

  private static final String MESSAGE_NAME = "message";
  private static final String CORRELATION_KEY = "key";
  private static final String PROCESS_ID = "catch-process";
  private static final String RUN_A = "runA";
  private static final String RUN_B = "runB";

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static final BpmnModelInstance CATCH_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("receive")
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression("key"))
          .endEvent()
          .done();

  @Test
  public void shouldCorrelateBufferedMessageOfMatchingRunOnly() {
    // given a run-A and a run-B message are both buffered for the same name + correlation key, with
    // the run-B message buffered first
    ENGINE.deployment().withXmlResource(CATCH_PROCESS).deploy();

    ENGINE
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withRunScope(RUN_B)
        .withTimeToLive(Duration.ofMinutes(10).toMillis())
        .publish();
    final var runAMessage =
        ENGINE
            .message()
            .withName(MESSAGE_NAME)
            .withCorrelationKey(CORRELATION_KEY)
            .withRunScope(RUN_A)
            .withTimeToLive(Duration.ofMinutes(10).toMillis())
            .publish();

    // when a run-A instance subscribes (publish-before-subscribe: the buffered path)
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withTags(TagUtil.runScopeTag(RUN_A))
            .withVariable("key", CORRELATION_KEY)
            .create();

    // then it correlates to the run-A message, not the earlier-buffered run-B message
    final var correlated =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(correlated.getValue().getMessageKey()).isEqualTo(runAMessage.getKey());
  }
}
