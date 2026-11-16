/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.DbValue;
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
 * Value of the cross-partition message-start lock column family on {@code P_K}. Marks a local
 * process-correlation-key lock entry whose holder instance was created on another partition ({@code
 * P_B}) via the cross-partition message-start handshake.
 *
 * <p>It records exactly what the pull-based release loop needs to act on that holder:
 *
 * <ul>
 *   <li>the holder's {@code processInstanceKey} — the release poll asks {@code P_B} whether
 *       <em>this specific instance</em> is still active, and the instance key already encodes the
 *       partition it lives on, so the target partition is derived from the key rather than stored;
 *   <li>the {@code tenantId} — needed to pick up the next buffered message for the correlation key
 *       once the lock is released, since the buffer scan is tenant-scoped;
 *   <li>the {@code storageOrdinal} of the holder instance, stamped on the holder of every release
 *       query so a consumer routing by ordinal can file the query with that instance. Entries
 *       written before the ordinal was persisted read back as 0, the main index.
 * </ul>
 */
@MsgpackPojo
@NullMarked
public final class CrossPartitionMessageStartLock
    extends MsgpackBufferValue<CrossPartitionMessageStartLock> implements DbValue {

  long processInstanceKey = -1L;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  int storageOrdinal = StorageOrdinalRelated.MAIN_INDEX;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public CrossPartitionMessageStartLock setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public DirectBuffer getTenantIdBuffer() {
    return tenantId;
  }

  public CrossPartitionMessageStartLock setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  public CrossPartitionMessageStartLock setTenantId(final DirectBuffer tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  /**
   * @return the storage ordinal of the holder instance; 0 for entries written before the ordinal
   *     was persisted (released V1 STARTED applier), which is the main index
   */
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public CrossPartitionMessageStartLock setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<CrossPartitionMessageStartLock> codec() {
    return CrossPartitionMessageStartLockCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<CrossPartitionMessageStartLock> ops() {
    return CrossPartitionMessageStartLockOps.INSTANCE;
  }
}
