/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartCorrelationKeyLockReleaseRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class MessageStartCorrelationKeyLockReleaseReleasedV1ApplierTest {

  private static final long HOLDER_PROCESS_INSTANCE_KEY = 4503599627370497L;
  private static final String BPMN_PROCESS_ID = "process";
  private static final String CORRELATION_KEY = "ck-1";
  private static final String TENANT = TenantOwned.DEFAULT_TENANT_IDENTIFIER;

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableMessageState messageState;
  private MessageStartCorrelationKeyLockReleaseReleasedV1Applier applier;

  @Before
  public void setUp() {
    final MutableProcessingState processingState = stateRule.getProcessingState();
    messageState = processingState.getMessageState();
    applier = new MessageStartCorrelationKeyLockReleaseReleasedV1Applier(messageState);
  }

  @Test
  public void shouldReleaseLockOnRelease() {
    // given a cross-partition lock held by a remote instance
    seedLock();

    // when the holder completion is released
    applier.applyState(1L, releaseRecord());

    // then the underlying correlation-key lock is released
    assertThat(
            messageState.getCrossPartitionStartLockHolder(
                wrapString(BPMN_PROCESS_ID), wrapString(CORRELATION_KEY)))
        .isEqualTo(-1L);
  }

  @Test
  public void shouldRemoveProcessInstanceCorrelationKeyOnRelease() {
    // given a cross-partition lock held by a remote instance, plus the process-instance ->
    // correlation-key row that P_K wrote when it created the holder via the handshake
    seedLock();
    messageState.putProcessInstanceCorrelationKey(
        HOLDER_PROCESS_INSTANCE_KEY, wrapString(CORRELATION_KEY));

    // when the holder completion is released
    applier.applyState(1L, releaseRecord());

    // then the correlation-key row is removed rather than leaked
    assertThat(messageState.getProcessInstanceCorrelationKey(HOLDER_PROCESS_INSTANCE_KEY)).isNull();
  }

  @Test
  public void shouldNotFailWhenProcessInstanceCorrelationKeyAbsent() {
    // given a cross-partition lock with no process-instance -> correlation-key row (e.g. a holder
    // with an empty correlation key, or a RELEASE replayed after the row was already removed)
    seedLock();

    // when the holder completion is released
    // then the removal is guarded and does not throw
    assertThatCode(() -> applier.applyState(1L, releaseRecord())).doesNotThrowAnyException();
  }

  private void seedLock() {
    messageState.putActiveProcessInstance(wrapString(BPMN_PROCESS_ID), wrapString(CORRELATION_KEY));
    messageState.putCrossPartitionStartLock(
        wrapString(BPMN_PROCESS_ID),
        wrapString(CORRELATION_KEY),
        HOLDER_PROCESS_INSTANCE_KEY,
        TENANT);
  }

  private MessageStartCorrelationKeyLockReleaseRecord releaseRecord() {
    final var record = new MessageStartCorrelationKeyLockReleaseRecord();
    record
        .addHolder()
        .setProcessInstanceKey(HOLDER_PROCESS_INSTANCE_KEY)
        .setBpmnProcessId(BPMN_PROCESS_ID)
        .setCorrelationKey(CORRELATION_KEY)
        .setTenantId(TENANT);
    return record;
  }
}
