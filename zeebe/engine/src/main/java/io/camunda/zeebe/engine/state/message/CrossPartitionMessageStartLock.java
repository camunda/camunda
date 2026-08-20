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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
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
 *       once the lock is released, since the buffer scan is tenant-scoped.
 * </ul>
 */
public final class CrossPartitionMessageStartLock extends UnpackedObject implements DbValue {

  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public CrossPartitionMessageStartLock() {
    super(2);
    declareProperty(processInstanceKeyProp).declareProperty(tenantIdProp);
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
}
