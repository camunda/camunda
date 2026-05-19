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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

/**
 * Local arm of the design's start-event uniqueness rule: when {@code businessIdUniquenessEnabled}
 * is on and a published message carries a {@code businessId}, a new instance is only created if no
 * active process instance on this partition already holds that {@code businessId} for the same
 * process definition.
 *
 * <p>This pins:
 *
 * <ul>
 *   <li>Live correlation path ({@link MessageCorrelateBehavior#correlateToMessageStartEvents}):
 *       second publish with same businessId is suppressed; later publishes with a different
 *       businessId still start a new PI; messages without a businessId are never blocked.
 *   <li>Buffered correlation path ({@link
 *       io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBufferedMessageStartEventBehavior}): a
 *       buffered second message with the same correlation key is correlated only after the holding
 *       PI completes, demonstrating that the uniqueness filter consults observable PI state and
 *       unblocks once the holder is gone.
 *   <li>The businessId from the message is stamped on the new PI's creation record so future
 *       uniqueness checks (and exporters) see it.
 * </ul>
 *
 * <p>A companion test ({@link MessageStartEventBusinessIdUniquenessDisabledTest}) pins the
 * regression behaviour with the flag off so the gate cannot silently change defaults.
 *
 * <p>Cross-partition uniqueness (where the businessId hashes to a different partition than the
 * message correlation key) is intentionally out of scope for this increment and is covered in a
 * later increment via the cross-partition ask to {@code P_B}.
 */
public final class MessageStartEventBusinessIdUniquenessTest {

  private static final String PROCESS_ID = "wf";
  private static final String MESSAGE_NAME = "start-msg";

  // A process whose only start event is a message start event with a service-task body so we have
  // a stable observable signal (a JOB CREATED record per started PI) without auto-completion.
  private static final BpmnModelInstance MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .message(MESSAGE_NAME)
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));

  @Test
  public void shouldBlockSecondStartWhenBusinessIdIsAlreadyHeldByActivePI() {
    // given a started PI holds businessId "biz-42" for this process definition
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("") // empty key so the existing correlation-key lock is not the gate
        .withBusinessId("biz-42")
        .withVariables(Map.of("seq", 1))
        .publish();
    final var firstJob = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    // when a second message with the same businessId is published with TTL=0 so we get a
    // deterministic terminal (EXPIRED) to bound the assertion stream on
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("")
        .withBusinessId("biz-42")
        .withVariables(Map.of("seq", 2))
        .withTimeToLive(0L)
        .publish();

    // then no second PI is created up to the EXPIRED terminal of the suppressed message
    final long secondPiCount =
        RecordingExporter.records()
            .limit(r -> r.getIntent() == MessageIntent.EXPIRED)
            .processInstanceRecords()
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .count();
    assertThat(secondPiCount)
        .as("only the original PI should exist; the duplicate-businessId publish is suppressed")
        .isEqualTo(1L);
    assertThat(firstJob.getValue().getProcessInstanceKey()).isPositive();
  }

  @Test
  public void shouldAllowSecondStartWhenBusinessIdDiffers() {
    // given
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("")
        .withBusinessId("biz-A")
        .withVariables(Map.of("seq", 1))
        .publish();
    RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    // when a second message with a different businessId is published
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("")
        .withBusinessId("biz-B")
        .withVariables(Map.of("seq", 2))
        .publish();

    // then both PIs are started — the uniqueness rule is per businessId, not per definition
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withElementType(BpmnElementType.PROCESS)
                .limit(2))
        .extracting(r -> r.getValue().getBusinessId())
        .containsExactly("biz-A", "biz-B");
  }

  @Test
  public void shouldAllowMessageWithoutBusinessIdEvenWhenBusinessIdIsHeld() {
    // given a PI already holds "biz-42"
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("")
        .withBusinessId("biz-42")
        .withVariables(Map.of("seq", 1))
        .publish();
    RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    // when a message without a businessId is published
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("")
        .withVariables(Map.of("seq", 2))
        .publish();

    // then it still starts a new PI — the rule is asymmetric: only messages carrying a
    // businessId are subject to uniqueness, mirroring the catch-event filter from increment 1b.
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withElementType(BpmnElementType.PROCESS)
                .limit(2))
        .extracting(r -> r.getValue().getBusinessId())
        .containsExactly("biz-42", "");
  }

  @Test
  public void shouldStampBusinessIdFromMessageOnTheNewProcessInstanceRecord() {
    // given
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();

    // when
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("")
        .withBusinessId("biz-stamped")
        .publish();

    // then the activating PROCESS record carries the businessId so exporters and the local
    // uniqueness check see it
    final var processActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    assertThat(processActivating.getValue().getBusinessId()).isEqualTo("biz-stamped");

    // and the PROCESS_INSTANCE_CREATION:CREATED follow-up event carries it as well, so consumers
    // of ProcessInstanceCreationRecordValue.getBusinessId() observe the same value as for a PI
    // created via the PROCESS_INSTANCE_CREATION command path
    final var creationCreated =
        RecordingExporter.processInstanceCreationRecords()
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .withInstanceKey(processActivating.getValue().getProcessInstanceKey())
            .getFirst();
    assertThat(creationCreated.getValue().getBusinessId()).isEqualTo("biz-stamped");
  }

  @Test
  public void shouldCorrelateBufferedMessageAfterHoldingInstanceCompletes() {
    // given a first PI holds the correlation-key lock and businessId "biz-42"
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("k-1")
        .withBusinessId("biz-42")
        .withVariables(Map.of("seq", 1))
        .publish();
    final var firstJob = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    // and a second message is buffered behind the correlation-key lock
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("k-1")
        .withBusinessId("biz-42")
        .withTimeToLive(Duration.ofMinutes(5))
        .withVariables(Map.of("seq", 2))
        .publish();

    // when the holding PI completes — at this point no active PI holds "biz-42" anymore so the
    // buffered scan's uniqueness filter does NOT block correlation
    engine.job().withKey(firstJob.getKey()).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(firstJob.getValue().getProcessInstanceKey())
        .filterRootScope()
        .await();

    // then the buffered second message correlates and starts a new PI also carrying "biz-42"
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withElementType(BpmnElementType.PROCESS)
                .limit(2))
        .extracting(r -> r.getValue().getBusinessId())
        .containsExactly("biz-42", "biz-42");
  }
}
