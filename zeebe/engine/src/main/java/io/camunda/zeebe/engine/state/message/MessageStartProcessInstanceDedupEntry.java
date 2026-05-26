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

/**
 * Value of the cross-partition message-start dedup column family on {@code P_B}. Stores the
 * process-instance key the original {@code REQUEST} produced together with the row's deletion
 * deadline (epoch millis). The deadline is set once at insert time as {@code now + tombstoneWindow}
 * and is never updated; the read path treats {@code deletionDeadline <= now} as a miss and the
 * sweeper deletes such rows.
 */
public final class MessageStartProcessInstanceDedupEntry extends UnpackedObject implements DbValue {

  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final LongProperty deletionDeadlineProp = new LongProperty("deletionDeadline", -1L);

  public MessageStartProcessInstanceDedupEntry() {
    super(2);
    declareProperty(processInstanceKeyProp).declareProperty(deletionDeadlineProp);
  }

  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public MessageStartProcessInstanceDedupEntry setProcessInstanceKey(
      final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
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
