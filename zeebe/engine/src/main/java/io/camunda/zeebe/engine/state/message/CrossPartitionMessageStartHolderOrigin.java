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
 *       P_K} stays tenant-scoped.
 * </ul>
 *
 * <p>The buffers returned by the getters are backed by the shared column-family read buffer; copy
 * them before appending events or performing further state reads.
 */
public final class CrossPartitionMessageStartHolderOrigin extends UnpackedObject
    implements DbValue {

  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey", "");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty messageKeyProp = new LongProperty("messageKey", -1L);

  public CrossPartitionMessageStartHolderOrigin() {
    super(4);
    declareProperty(bpmnProcessIdProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(tenantIdProp)
        .declareProperty(messageKeyProp);
  }

  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  public CrossPartitionMessageStartHolderOrigin setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKeyProp.getValue();
  }

  public CrossPartitionMessageStartHolderOrigin setCorrelationKey(
      final DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  public DirectBuffer getTenantIdBuffer() {
    return tenantIdProp.getValue();
  }

  public CrossPartitionMessageStartHolderOrigin setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public CrossPartitionMessageStartHolderOrigin setTenantId(final DirectBuffer tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public long getMessageKey() {
    return messageKeyProp.getValue();
  }

  public CrossPartitionMessageStartHolderOrigin setMessageKey(final long messageKey) {
    messageKeyProp.setValue(messageKey);
    return this;
  }
}
