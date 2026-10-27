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
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartCorrelationKeyLockReleaseRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class MessageStartCorrelationKeyLockReleasePushedV1ApplierTest {
  private static final long HOLDER_PROCESS_INSTANCE_KEY = 4503599627370497L;
  private static final String BPMN_PROCESS_ID = "process";
  private static final String CORRELATION_KEY = "ck-1";
  private static final String TENANT = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  private static final long MESSAGE_KEY = 2251799813685249L;

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableMessageState messageState;
  private MessageStartCorrelationKeyLockReleasePushedV1Applier applier;

  @BeforeEach
  public void setUp() {
    messageState = processingState.getMessageState();
    applier = new MessageStartCorrelationKeyLockReleasePushedV1Applier(messageState);
  }

  @Test
  public void shouldRemoveHolderOriginOnPushed() {
    // given a holder-origin entry P_B kept for a cross-partition holder
    seedOrigin();

    // when the holder's completion is pushed
    applier.applyState(HOLDER_PROCESS_INSTANCE_KEY, pushedRecord());

    // then the holder-origin entry is dropped rather than leaked
    assertThat(messageState.getCrossPartitionStartHolderOrigin(HOLDER_PROCESS_INSTANCE_KEY))
        .isNull();
  }

  @Test
  public void shouldNotFailWhenHolderOriginAbsent() {
    // given no holder-origin entry (e.g. a PUSHED replayed after the entry was already removed)
    // when the holder's completion is pushed
    // then the removal is guarded and does not throw
    assertThatCode(() -> applier.applyState(HOLDER_PROCESS_INSTANCE_KEY, pushedRecord()))
        .doesNotThrowAnyException();
  }

  private void seedOrigin() {
    messageState.putCrossPartitionStartHolderOrigin(
        HOLDER_PROCESS_INSTANCE_KEY,
        wrapString(BPMN_PROCESS_ID),
        wrapString(CORRELATION_KEY),
        TENANT,
        MESSAGE_KEY);
  }

  private MessageStartCorrelationKeyLockReleaseRecord pushedRecord() {
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
