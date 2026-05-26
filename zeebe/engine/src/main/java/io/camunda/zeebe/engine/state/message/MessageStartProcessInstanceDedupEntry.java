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
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;

/**
 * Value of the forward column family of the cross-partition message-start dedup state on {@code
 * P_B}. Stores the process-instance key the original {@code REQUEST} produced together with the
 * entry's lifecycle status; the deletion deadline is meaningful only for {@link
 * MessageStartProcessInstanceDedupStatus#TOMBSTONE} entries and defaults to {@code -1} otherwise.
 */
public final class MessageStartProcessInstanceDedupEntry extends UnpackedObject implements DbValue {

  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final EnumProperty<MessageStartProcessInstanceDedupStatus> statusProp =
      new EnumProperty<>(
          "status",
          MessageStartProcessInstanceDedupStatus.class,
          MessageStartProcessInstanceDedupStatus.ACTIVE);
  private final LongProperty deletionDeadlineProp = new LongProperty("deletionDeadline", -1L);

  public MessageStartProcessInstanceDedupEntry() {
    super(3);
    declareProperty(processInstanceKeyProp)
        .declareProperty(statusProp)
        .declareProperty(deletionDeadlineProp);
  }

  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public MessageStartProcessInstanceDedupEntry setProcessInstanceKey(
      final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  public MessageStartProcessInstanceDedupStatus getStatus() {
    return statusProp.getValue();
  }

  public MessageStartProcessInstanceDedupEntry setStatus(
      final MessageStartProcessInstanceDedupStatus status) {
    statusProp.setValue(status);
    return this;
  }

  public long getDeletionDeadline() {
    return deletionDeadlineProp.getValue();
  }

  public MessageStartProcessInstanceDedupEntry setDeletionDeadline(final long deletionDeadline) {
    deletionDeadlineProp.setValue(deletionDeadline);
    return this;
  }
}
