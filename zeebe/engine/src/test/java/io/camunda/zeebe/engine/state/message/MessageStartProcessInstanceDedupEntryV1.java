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

/**
 * Value of the cross-partition message-start dedup column family on {@code P_B}. Stores the
 * process-instance key the original {@code REQUEST} produced together with the row's deletion
 * deadline (epoch millis). The deadline is taken directly from the request's {@code
 * messageDeadline} (= {@code publishTime + ttl} on {@code P_K}) and is never updated; the read path
 * treats {@code deletionDeadline <= now} as a miss and the sweeper deletes such rows.
 */
/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class MessageStartProcessInstanceDedupEntryV1 extends UnpackedObject
    implements DbValue {

  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final LongProperty deletionDeadlineProp = new LongProperty("deletionDeadline", -1L);
  private final IntegerProperty storageOrdinalProp = new IntegerProperty("storageOrdinal", 0);

  public MessageStartProcessInstanceDedupEntryV1() {
    super(3);
    declareProperty(processInstanceKeyProp)
        .declareProperty(deletionDeadlineProp)
        .declareProperty(storageOrdinalProp);
  }

  public int getStorageOrdinal() {
    return storageOrdinalProp.getValue();
  }

  public MessageStartProcessInstanceDedupEntryV1 setStorageOrdinal(final int storageOrdinal) {
    storageOrdinalProp.setValue(storageOrdinal);
    return this;
  }

  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public MessageStartProcessInstanceDedupEntryV1 setProcessInstanceKey(
      final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  public long getDeletionDeadline() {
    return deletionDeadlineProp.getValue();
  }

  public MessageStartProcessInstanceDedupEntryV1 setDeletionDeadline(final long deletionDeadline) {
    deletionDeadlineProp.setValue(deletionDeadline);
    return this;
  }
}
