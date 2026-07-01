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
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartCorrelationKeyLockReleaseRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartCorrelationKeyLockReleaseIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartCorrelationKeyLockReleaseRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the {@code P_B}-side holder-liveness query processor of the pull-based correlation-key
 * lock release in isolation: a directly-written {@link
 * MessageStartCorrelationKeyLockReleaseIntent#QUERY} command is consumed, acknowledged with {@link
 * MessageStartCorrelationKeyLockReleaseIntent#QUERIED}, and answered with a {@link
 * MessageStartCorrelationKeyLockReleaseIntent#RELEASE} reply only when the holder instance is no
 * longer active on this partition. The poller that dispatches the query from {@code P_K} lands in a
 * later commit; until then this is the only exercise of the processor.
 *
 * <p>Tests run on a single-partition engine and encode the requesting partition in {@code
 * requestKey} as partition {@code 1}, so the reply command is written locally and observable via
 * {@link RecordingExporter}. No reply-side processor is registered yet (lands in a later commit),
 * so the {@code RELEASE} reply stays a recorded command — exactly what we want to assert on here.
 */
public final class MessageStartCorrelationKeyLockReleaseQueryProcessorTest {

  private static final String PROCESS_ID = "wf";
  private static final String CORRELATION_KEY = "ck";
  // requestKey is a data field (reply routing only), never used as an event key, so a synthetic
  // partition-1 key is fine.
  private static final long REQUEST_KEY = Protocol.encodePartitionId(1, 42);
  private static final long ABSENT_HOLDER_KEY = Protocol.encodePartitionId(1, 999_999);

  private static final BpmnModelInstance LONG_RUNNING_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine = EngineRule.singlePartition().withInitialClusterVersionAtMax();

  @Test
  public void shouldReplyReleaseWhenHolderInstanceIsNotActive() {
    // given a deployed process (advances the key generator) but no holder instance
    final long commandKey =
        engine.deployment().withXmlResource(LONG_RUNNING_PROCESS).deploy().getKey();

    // when a QUERY for a non-existent holder is delivered to this partition (acting as P_B)
    engine.writeRecords(
        RecordToWrite.command()
            .key(commandKey)
            .messageStartCorrelationKeyLockRelease(
                MessageStartCorrelationKeyLockReleaseIntent.QUERY, query(ABSENT_HOLDER_KEY)));

    // then the query is acknowledged with QUERIED and a RELEASE reply is dispatched back to P_K
    final var queried =
        RecordingExporter.messageStartCorrelationKeyLockReleaseRecords(
                MessageStartCorrelationKeyLockReleaseIntent.QUERIED)
            .getFirst();
    assertThat(queried.getRecordType()).isEqualTo(RecordType.EVENT);
    assertQueryPreserved(queried.getValue(), ABSENT_HOLDER_KEY);

    final var release =
        RecordingExporter.messageStartCorrelationKeyLockReleaseRecords(
                MessageStartCorrelationKeyLockReleaseIntent.RELEASE)
            .getFirst();
    assertThat(release.getRecordType()).isEqualTo(RecordType.COMMAND);
    assertQueryPreserved(release.getValue(), ABSENT_HOLDER_KEY);
  }

  @Test
  public void shouldStaySilentWhenHolderInstanceIsStillActive() {
    // given an active holder instance waiting on a job
    final long commandKey =
        engine.deployment().withXmlResource(LONG_RUNNING_PROCESS).deploy().getKey();
    final long holderKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.jobRecords(JobIntent.CREATED).withProcessInstanceKey(holderKey).getFirst();

    // when a QUERY arrives for the still-active holder
    engine.writeRecords(
        RecordToWrite.command()
            .key(commandKey)
            .messageStartCorrelationKeyLockRelease(
                MessageStartCorrelationKeyLockReleaseIntent.QUERY, query(holderKey)));

    // then the query is acknowledged but no RELEASE reply is produced
    final var queried =
        RecordingExporter.messageStartCorrelationKeyLockReleaseRecords(
                MessageStartCorrelationKeyLockReleaseIntent.QUERIED)
            .getFirst();
    assertQueryPreserved(queried.getValue(), holderKey);

    // bound the scan on the QUERIED ack as a deterministic terminal so the assertion does not have
    // to wait for a RELEASE that should never happen. QUERIED and any RELEASE are written in the
    // same processing cycle, so a RELEASE would already be present once QUERIED is observed.
    final long releaseReplies =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getIntent() == MessageStartCorrelationKeyLockReleaseIntent.QUERIED
                        && r.getRecordType() == RecordType.EVENT)
            .filter(r -> r.getIntent() == MessageStartCorrelationKeyLockReleaseIntent.RELEASE)
            .count();
    assertThat(releaseReplies).as("a still-active holder produces no RELEASE reply").isZero();
  }

  private MessageStartCorrelationKeyLockReleaseRecord query(final long holderProcessInstanceKey) {
    final var record = new MessageStartCorrelationKeyLockReleaseRecord().setRequestKey(REQUEST_KEY);
    record
        .addHolder()
        .setProcessInstanceKey(holderProcessInstanceKey)
        .setBpmnProcessId(PROCESS_ID)
        .setCorrelationKey(CORRELATION_KEY)
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    return record;
  }

  private void assertQueryPreserved(
      final MessageStartCorrelationKeyLockReleaseRecordValue value,
      final long holderProcessInstanceKey) {
    assertThat(value.getRequestKey()).isEqualTo(REQUEST_KEY);
    assertThat(value.getHolders()).hasSize(1);
    final var holder = value.getHolders().getFirst();
    assertThat(holder.getProcessInstanceKey()).isEqualTo(holderProcessInstanceKey);
    assertThat(holder.getBpmnProcessId()).isEqualTo(PROCESS_ID);
    assertThat(holder.getCorrelationKey()).isEqualTo(CORRELATION_KEY);
    assertThat(holder.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }
}
