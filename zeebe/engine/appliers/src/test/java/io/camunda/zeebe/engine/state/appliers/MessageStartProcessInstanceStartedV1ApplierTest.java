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

import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class MessageStartProcessInstanceStartedV1ApplierTest {

  private static final int P_B = 1;
  private static final int P_K = 2;
  private static final long RAW_HOLDER_KEY = 42L;
  private static final long MESSAGE_KEY = Protocol.encodePartitionId(P_K, 7L);
  private static final String BPMN_PROCESS_ID = "process";
  private static final String CORRELATION_KEY = "ck-1";
  private static final String BUSINESS_ID = "biz-1";
  private static final String TENANT = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  private static final long PROCESS_DEFINITION_KEY = 123L;

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableMessageState messageState;

  @BeforeEach
  public void setUp() {
    messageState = processingState.getMessageState();
  }

  @Test
  public void shouldWriteHolderOriginOnHolderPartition() {
    // given the applier runs on P_B — the partition the holder instance was created on
    final var applier = applierForPartition(P_B);
    final long holderKey = Protocol.encodePartitionId(P_B, RAW_HOLDER_KEY);

    // when the cross-partition STARTED is applied
    applier.applyState(1L, startedRecord(holderKey, CORRELATION_KEY, BUSINESS_ID));

    // then the holder-origin entry is written with the lock coordinates and addressing messageKey
    final var origin = messageState.getCrossPartitionStartHolderOrigin(holderKey);
    assertThat(origin).isNotNull();
    assertThat(origin.getBpmnProcessIdBuffer()).isEqualTo(wrapString(BPMN_PROCESS_ID));
    assertThat(origin.getCorrelationKeyBuffer()).isEqualTo(wrapString(CORRELATION_KEY));
    assertThat(origin.getTenantIdBuffer()).isEqualTo(wrapString(TENANT));
    assertThat(origin.getMessageKey()).isEqualTo(MESSAGE_KEY);
  }

  @Test
  public void shouldNotWriteHolderOriginOnLockPartition() {
    // given the applier runs on P_K — the lock partition, where the same STARTED reply is applied
    // but the holder instance lives on another partition (its key encodes P_B)
    final var applier = applierForPartition(P_K);
    final long holderKey = Protocol.encodePartitionId(P_B, RAW_HOLDER_KEY);

    // when the cross-partition STARTED is applied
    applier.applyState(1L, startedRecord(holderKey, CORRELATION_KEY, BUSINESS_ID));

    // then no holder-origin entry is written on P_K
    assertThat(messageState.getCrossPartitionStartHolderOrigin(holderKey)).isNull();
  }

  @Test
  public void shouldNotWriteHolderOriginWhenCorrelationKeyEmpty() {
    // given the applier runs on P_B but the started holder carries no correlation key, so there is
    // no correlation-key lock on any P_K to release
    final var applier = applierForPartition(P_B);
    final long holderKey = Protocol.encodePartitionId(P_B, RAW_HOLDER_KEY);

    // when the STARTED is applied
    applier.applyState(1L, startedRecord(holderKey, "", BUSINESS_ID));

    // then no holder-origin entry is written
    assertThat(messageState.getCrossPartitionStartHolderOrigin(holderKey)).isNull();
  }

  private MessageStartProcessInstanceStartedV1Applier applierForPartition(final int partitionId) {
    return new MessageStartProcessInstanceStartedV1Applier(
        processingState.getMessageStartProcessInstanceDedupState(),
        processingState.getMessageStartProcessInstanceAskState(),
        messageState,
        partitionId);
  }

  private MessageStartProcessInstanceRequestRecord startedRecord(
      final long processInstanceKey, final String correlationKey, final String businessId) {
    return new MessageStartProcessInstanceRequestRecord()
        .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
        .setMessageKey(MESSAGE_KEY)
        .setMessageName("start-msg")
        .setCorrelationKey(correlationKey)
        .setBusinessId(businessId)
        .setBpmnProcessId(BPMN_PROCESS_ID)
        .setTenantId(TENANT)
        .setProcessInstanceKey(processInstanceKey);
  }
}
