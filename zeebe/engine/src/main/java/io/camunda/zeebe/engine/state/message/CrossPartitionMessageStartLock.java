/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.agrona.DirectBuffer;

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
public final class CrossPartitionMessageStartLock extends UnpackedObject implements DbValue {

  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final IntegerProperty storageOrdinalProp =
      new IntegerProperty("storageOrdinal", StorageOrdinalRelated.MAIN_INDEX);

  public CrossPartitionMessageStartLock() {
    super(3);
    declareProperty(processInstanceKeyProp)
        .declareProperty(tenantIdProp)
        .declareProperty(storageOrdinalProp);
  }

  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public CrossPartitionMessageStartLock setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  public DirectBuffer getTenantIdBuffer() {
    return tenantIdProp.getValue();
  }

  public CrossPartitionMessageStartLock setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public CrossPartitionMessageStartLock setTenantId(final DirectBuffer tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  /**
   * @return the storage ordinal of the holder instance; 0 for entries written before the ordinal
   *     was persisted (released V1 STARTED applier), which is the main index
   */
  public int getStorageOrdinal() {
    return storageOrdinalProp.getValue();
  }

  public CrossPartitionMessageStartLock setStorageOrdinal(final int storageOrdinal) {
    storageOrdinalProp.setValue(storageOrdinal);
    return this;
  }
}
