/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
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

  private static final String DUAL_PROCESS_ID = "wf-dual";
  private static final String HOLDER_JOB_TYPE = "holder";
  private static final String MESSAGE_JOB_TYPE = "msg";

  // A process with both a none-start and a message-start event sharing the same definition, so a
  // PI created via CreateProcessInstance (none-start, no correlation key) and a message-start
  // publish contend for the same businessId uniqueness index. Both arms have a service task so the
  // holder can be completed deterministically and each started PI emits an observable JOB CREATED.
  private static final BpmnModelInstance MESSAGE_AND_NONE_START_PROCESS =
      Bpmn.createExecutableProcess(DUAL_PROCESS_ID)
          .startEvent("noneStart")
          .serviceTask("holderTask", t -> t.zeebeJobType(HOLDER_JOB_TYPE))
          .endEvent()
          .moveToProcess(DUAL_PROCESS_ID)
          .startEvent("msgStart")
          .message(MESSAGE_NAME)
          .serviceTask("msgTask", t -> t.zeebeJobType(MESSAGE_JOB_TYPE))
          .endEvent()
          .done();

  private static final String VERSIONED_PROCESS_ID = "wf-versioned";

  // An older version (v1) of the process that has ONLY a none-start event — no message start event.
  // An instance of it can hold a businessId via CreateProcessInstance.
  private static final BpmnModelInstance VERSIONED_PROCESS_V1 =
      Bpmn.createExecutableProcess(VERSIONED_PROCESS_ID)
          .startEvent("noneStartV1")
          .serviceTask("holderTask", t -> t.zeebeJobType(HOLDER_JOB_TYPE))
          .endEvent()
          .done();

  // A newer version (v2) of the SAME bpmnProcessId that adds a message start event. The
  // message-start
  // subscription belongs to this latest version, while the businessId-holding instance runs v1.
  private static final BpmnModelInstance VERSIONED_PROCESS_V2 =
      Bpmn.createExecutableProcess(VERSIONED_PROCESS_ID)
          .startEvent("msgStartV2")
          .message(MESSAGE_NAME)
          .serviceTask("msgTask", t -> t.zeebeJobType(MESSAGE_JOB_TYPE))
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true))
          .withInitialClusterVersionAtMax();

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
    // deterministic terminal (EXPIRED for this specific message) to bound the assertion stream on
    final var suppressedPublish =
        engine
            .message()
            .withName(MESSAGE_NAME)
            .withCorrelationKey("")
            .withBusinessId("biz-42")
            .withVariables(Map.of("seq", 2))
            .withTimeToLive(0L)
            .publish();

    // then no second PI is created up to the EXPIRED terminal of the suppressed message.
    // The limit predicate keys on the suppressed message's record key (not just intent) so that
    // an unrelated EXPIRED — e.g. introduced by future test-setup changes — cannot short-circuit
    // the bound and silently mask a regression.
    final long processInstancesStarted =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getIntent() == MessageIntent.EXPIRED
                        && r.getKey() == suppressedPublish.getKey())
            .processInstanceRecords()
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .count();
    assertThat(processInstancesStarted)
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
  public void shouldCorrelateNextBufferedMessageWhenPreviousIsSkippedByBusinessIdUniqueness() {
    // Pins the buffered scan's "skip the blocked entry, continue iterating" invariant: if the
    // visitor flipped from `return true` to `return false` after a businessId-uniqueness skip,
    // the queue for the correlation key would silently stall and the next buffered entry would
    // not be correlated even after the correlation-key holder completes.

    // given a long-lived holder PI on correlation key "k-holder" carrying "biz-held"
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("k-holder")
        .withBusinessId("biz-held")
        .withVariables(Map.of("seq", 0))
        .publish();
    final var holderJob = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    // and a second PI on correlation key "k-1" that owns the correlation-key lock for "k-1"
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("k-1")
        .withBusinessId("biz-k1")
        .withVariables(Map.of("seq", 1))
        .publish();
    final var k1Job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .filter(
                j ->
                    j.getValue().getProcessInstanceKey()
                        != holderJob.getValue().getProcessInstanceKey())
            .getFirst();

    // and two buffered messages on "k-1": the first carries "biz-held" (will be skipped by the
    // uniqueness filter as long as the holder PI is alive), the second carries "biz-free"
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("k-1")
        .withBusinessId("biz-held")
        .withTimeToLive(Duration.ofMinutes(5))
        .withVariables(Map.of("seq", 2))
        .publish();
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("k-1")
        .withBusinessId("biz-free")
        .withTimeToLive(Duration.ofMinutes(5))
        .withVariables(Map.of("seq", 3))
        .publish();

    // when the correlation-key holder for "k-1" completes (the buffered scan runs on "k-1")
    engine.job().withKey(k1Job.getKey()).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(k1Job.getValue().getProcessInstanceKey())
        .filterRootScope()
        .await();

    // then the scan skips the blocked "biz-held" entry but still correlates the "biz-free" one,
    // proving the queue is not stalled by a single skipped entry
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withElementType(BpmnElementType.PROCESS)
                .limit(3))
        .extracting(r -> r.getValue().getBusinessId())
        .containsExactly("biz-held", "biz-k1", "biz-free");
  }

  @Test
  public void shouldAllowStartWhenBusinessIdIsHeldOnlyByBannedProcessInstance() {
    // Pins the deliberate semantics that the uniqueness lookup ignores PIs which have been
    // banned: a banned PI cannot make progress and must not indefinitely block new starts that
    // carry the same businessId. The `hasActiveProcessInstanceWithBusinessId` callsites all pass
    // `bannedInstanceState::isProcessInstanceBanned` for exactly this reason; this test fails if
    // that predicate is ever dropped from the callsite.

    // given a PI holds businessId "biz-banned"
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("")
        .withBusinessId("biz-banned")
        .withVariables(Map.of("seq", 1))
        .publish();
    final var firstJob = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    // and that PI is banned (e.g. due to an unrecoverable processing error) — it stays "active"
    // structurally but its banned state must exempt it from blocking new starts
    engine.banInstanceInNewTransaction(1, firstJob.getValue().getProcessInstanceKey());

    // when a second message with the same businessId is published
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("")
        .withBusinessId("biz-banned")
        .withVariables(Map.of("seq", 2))
        .publish();

    // then a new PI is started — the banned holder does not gate
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withElementType(BpmnElementType.PROCESS)
                .limit(2))
        .extracting(r -> r.getValue().getBusinessId())
        .containsExactly("biz-banned", "biz-banned");
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

  @Test
  public void
      shouldCorrelateBusinessIdBlockedMessageWhenHolderWithDifferentCorrelationKeyCompletes() {
    // Pins the businessId index + completion hook (ADR 0002 D5): a message blocked purely on
    // businessId uniqueness — whose correlation key differs from the holder's, so the
    // correlation-key buffer scan can never reach it — is still re-driven once the holder frees the
    // businessId on completion. This is the behaviour the correlation-key buffer could not provide.

    // given a holder PI on correlation key "k-holder" carrying "biz-shared"
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("k-holder")
        .withBusinessId("biz-shared")
        .withVariables(Map.of("seq", 1))
        .publish();
    final var holderJob = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    // and a message on a DIFFERENT, otherwise-free correlation key carrying the same businessId —
    // blocked only by uniqueness and therefore buffered (the correlation-key lock for "k-other" is
    // free, so the correlation-key buffer would never re-drive it)
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("k-other")
        .withBusinessId("biz-shared")
        .withTimeToLive(Duration.ofMinutes(5))
        .withVariables(Map.of("seq", 2))
        .publish();

    // when the holder completes, freeing "biz-shared"
    engine.job().withKey(holderJob.getKey()).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(holderJob.getValue().getProcessInstanceKey())
        .filterRootScope()
        .await();

    // then the businessId-blocked message starts a new PI even though its correlation key never
    // matched the holder's — proving the businessId index, not the correlation-key buffer, owns it
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withElementType(BpmnElementType.PROCESS)
                .limit(2))
        .extracting(r -> r.getValue().getBusinessId())
        .containsExactly("biz-shared", "biz-shared");
  }

  @Test
  public void shouldCorrelateBusinessIdBlockedMessageWhenHolderTerminates() {
    // A businessId frees on termination as well as completion, and the completion hook runs on the
    // termination transition too — so a message blocked on a businessId held by a (differently
    // keyed) holder is re-driven when that holder is cancelled.

    // given a holder PI on correlation key "k-holder" carrying "biz-shared"
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("k-holder")
        .withBusinessId("biz-shared")
        .withVariables(Map.of("seq", 1))
        .publish();
    final var holderJob = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();
    final var holderProcessInstanceKey = holderJob.getValue().getProcessInstanceKey();

    // and a businessId-blocked message on a different, free correlation key
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("k-other")
        .withBusinessId("biz-shared")
        .withTimeToLive(Duration.ofMinutes(5))
        .withVariables(Map.of("seq", 2))
        .publish();

    // when the holder is terminated (cancelled) — freeing "biz-shared" without completing
    engine.processInstance().withInstanceKey(holderProcessInstanceKey).cancel();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .withProcessInstanceKey(holderProcessInstanceKey)
        .filterRootScope()
        .await();

    // then the businessId-blocked message starts a new PI
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withElementType(BpmnElementType.PROCESS)
                .limit(2))
        .extracting(r -> r.getValue().getBusinessId())
        .containsExactly("biz-shared", "biz-shared");
  }

  @Test
  public void shouldCorrelateBusinessIdBlockedMessageWhenHolderWithoutCorrelationKeyCompletes() {
    // Pins that the businessId index + completion hook re-drives a blocked message even when the
    // holder carries NO correlation key at all — a PI created via CreateProcessInstance on the
    // none-start event. Such a holder never enters the message-start branch of the correlation-key
    // buffer rescan, so only the businessId-keyed hook can free the blocked message.

    // given a holder PI created via the none-start API holding "biz-shared" (no correlation key)
    engine.deployment().withXmlResource(MESSAGE_AND_NONE_START_PROCESS).deploy();
    final long holderPiKey =
        engine
            .processInstance()
            .ofBpmnProcessId(DUAL_PROCESS_ID)
            .withBusinessId("biz-shared")
            .create();
    final var holderJob =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType(HOLDER_JOB_TYPE).getFirst();

    // and a message-start publish carrying the same businessId — blocked purely on uniqueness and
    // therefore buffered
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("k-msg")
        .withBusinessId("biz-shared")
        .withTimeToLive(Duration.ofMinutes(5))
        .publish();

    // when the holder completes, freeing "biz-shared"
    engine.job().withKey(holderJob.getKey()).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(holderPiKey)
        .filterRootScope()
        .await();

    // then the buffered message-start correlates and a second PI is created, even though the
    // holder never carried a correlation key
    final var messagePiActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .withBpmnProcessId(DUAL_PROCESS_ID)
            .limit(2)
            .skip(1)
            .getFirst();
    assertThat(messagePiActivating.getValue().getBusinessId()).isEqualTo("biz-shared");
    assertThat(messagePiActivating.getValue().getProcessInstanceKey()).isNotEqualTo(holderPiKey);

    // and the same-partition retry is fully local — no cross-partition ask is ever emitted
    final long crossPartitionAsks =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATING
                        && r.getKey() == messagePiActivating.getKey())
            .filter(r -> r.getValueType() == ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST)
            .count();
    assertThat(crossPartitionAsks)
        .as("a same-partition (P_K = P_B) blocked-start retry must not emit a cross-partition ask")
        .isZero();
  }

  @Test
  public void shouldNotRestartBusinessIdBlockedMessageWhenHolderIsBannedUntilTtl() {
    // Pins the accepted boundary (mirrors the correlation-key buffer): banning frees the businessId
    // WITHOUT a completion/termination event, so the completion hook never runs and a message
    // blocked behind a banned holder is not re-driven promptly — it waits until its TTL and then
    // expires unstarted. No duplicate, no leak.

    // given a holder PI holds "biz-banned" and a message-start with the same businessId is blocked
    engine.deployment().withXmlResource(MESSAGE_AND_NONE_START_PROCESS).deploy();
    final long holderPiKey =
        engine
            .processInstance()
            .ofBpmnProcessId(DUAL_PROCESS_ID)
            .withBusinessId("biz-banned")
            .create();
    RecordingExporter.jobRecords(JobIntent.CREATED).withType(HOLDER_JOB_TYPE).getFirst();

    final var blockedPublish =
        engine
            .message()
            .withName(MESSAGE_NAME)
            .withCorrelationKey("k-msg")
            .withBusinessId("biz-banned")
            .withTimeToLive(Duration.ofSeconds(5))
            .publish();

    // when the holder is banned (neither completes nor terminates) and the message's TTL elapses
    engine.banInstanceInNewTransaction(1, holderPiKey);
    engine.increaseTime(
        Duration.ofSeconds(5).plus(EngineConfiguration.DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL));

    // then the buffered message expires without ever starting a second PI — only the holder PI was
    // ever activated, bounded by the EXPIRED terminal of the blocked publish
    final long activations =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getIntent() == MessageIntent.EXPIRED && r.getKey() == blockedPublish.getKey())
            .processInstanceRecords()
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .count();
    assertThat(activations)
        .as(
            "a banned holder does not trigger the completion hook; the blocked start expires at TTL")
        .isEqualTo(1L);
  }

  @Test
  public void shouldCorrelateBusinessIdBlockedMessageWhenHolderOfOlderVersionWithoutMessageStart() {
    // Pins that the businessId re-drive does NOT depend on the *holder's own* version having a
    // message start event. Business ID uniqueness is scoped per (businessId, bpmnProcessId) across
    // versions, so a holder running an older version that has only a none-start event still frees
    // the businessId on completion and must unblock a message-start owned by the latest version
    // (ADR 0002 D5). Without the broadened guard, the holder's completion is skipped because its
    // own
    // process element has no message start event, and the buffered start strands until TTL.

    // given v1 (none-start only) deployed and an instance of it holding "biz-versioned"
    engine.deployment().withXmlResource(VERSIONED_PROCESS_V1).deploy();
    final long holderPiKey =
        engine
            .processInstance()
            .ofBpmnProcessId(VERSIONED_PROCESS_ID)
            .withBusinessId("biz-versioned")
            .create();
    final var holderJob =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType(HOLDER_JOB_TYPE).getFirst();

    // and v2 deployed afterwards, adding a message start event for the same bpmnProcessId
    engine.deployment().withXmlResource(VERSIONED_PROCESS_V2).deploy();
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CREATED)
        .withMessageName(MESSAGE_NAME)
        .getFirst();

    // and a message-start publish carrying the same businessId — blocked by the v1 holder
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("k-msg")
        .withBusinessId("biz-versioned")
        .withTimeToLive(Duration.ofMinutes(5))
        .publish();

    // when the v1 holder (whose own version has no message start event) completes, freeing the
    // businessId
    engine.job().withKey(holderJob.getKey()).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(holderPiKey)
        .filterRootScope()
        .await();

    // then the buffered message-start (owned by v2) is re-driven and a new PI is created
    final var messagePiActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .withBpmnProcessId(VERSIONED_PROCESS_ID)
            .filter(r -> r.getValue().getProcessInstanceKey() != holderPiKey)
            .getFirst();
    assertThat(messagePiActivating.getValue().getBusinessId()).isEqualTo("biz-versioned");
    assertThat(messagePiActivating.getValue().getVersion())
        .as("the re-driven instance is created from the latest version that owns the message start")
        .isEqualTo(2);
  }
}
