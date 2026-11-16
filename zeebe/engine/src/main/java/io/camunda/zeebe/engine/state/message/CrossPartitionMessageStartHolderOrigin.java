/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * Value of the cross-partition message-start holder-origin column family, written on {@code P_B}
 * (the buffering partition) and keyed by the holder instance's process-instance key. It records
 * everything {@code P_B} needs to push a {@code RELEASE} to {@code P_K} the moment the holder
 * completes or terminates, without re-deriving anything from the completing element's context:
 *
 * <ul>
 *   <li>the {@code bpmnProcessId} and {@code correlationKey} — together they identify the
 *       correlation-key lock on {@code P_K} that must be released. The stored {@code bpmnProcessId}
 *       is authoritative: a migrated holder's new version may declare a different process id (or no
 *       message start event at all), so the lock must be keyed by the id captured at creation, not
 *       by the completing instance's current definition;
 *   <li>the {@code messageKey} — its partition bits address {@code P_K} (the partition that
 *       buffered the message and holds the lock). The push path synthesizes the {@code RELEASE}'s
 *       partition-addressing envelope from these bits;
 *   <li>the {@code tenantId} — carried through to the {@code RELEASE} so the lock lookup on {@code
 *       P_K} stays tenant-scoped;
 *   <li>the {@code storageOrdinal} of the holder instance, stamped on the holder of the {@code
 *       PUSHED} event and the pushed {@code RELEASE} so a consumer routing by ordinal can file them
 *       with that instance. Entries written before the ordinal was persisted read back as 0, the
 *       main index.
 * </ul>
 *
 * <p>The buffers returned by the getters are backed by the shared column-family read buffer; copy
 * them before appending events or performing further state reads.
 */
@MsgpackPojo
@NullMarked
public final class CrossPartitionMessageStartHolderOrigin
    extends MsgpackBufferValue<CrossPartitionMessageStartHolderOrigin> implements DbValue {

  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  DirectBuffer correlationKey = Decoding.EMPTY;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long messageKey = -1L;
  int storageOrdinal = StorageOrdinalRelated.MAIN_INDEX;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  public CrossPartitionMessageStartHolderOrigin setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKey;
  }

  public CrossPartitionMessageStartHolderOrigin setCorrelationKey(
      final DirectBuffer correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  public DirectBuffer getTenantIdBuffer() {
    return tenantId;
  }

  public CrossPartitionMessageStartHolderOrigin setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  public CrossPartitionMessageStartHolderOrigin setTenantId(final DirectBuffer tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public long getMessageKey() {
    return messageKey;
  }

  public CrossPartitionMessageStartHolderOrigin setMessageKey(final long messageKey) {
    this.messageKey = messageKey;
    return this;
  }

  /**
   * @return the storage ordinal of the holder instance; 0 for entries written before the ordinal
   *     was persisted (released V1 STARTED applier), which is the main index
   */
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public CrossPartitionMessageStartHolderOrigin setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<CrossPartitionMessageStartHolderOrigin> codec() {
    return CrossPartitionMessageStartHolderOriginCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<CrossPartitionMessageStartHolderOrigin> ops() {
    return CrossPartitionMessageStartHolderOriginOps.INSTANCE;
  }
}
