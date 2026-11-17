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
import org.jspecify.annotations.NullMarked;

/**
 * Value of the cross-partition message-start dedup column family on {@code P_B}. Stores the
 * process-instance key the original {@code REQUEST} produced together with the row's deletion
 * deadline (epoch millis). The deadline is taken directly from the request's {@code
 * messageDeadline} (= {@code publishTime + ttl} on {@code P_K}) and is never updated; the read path
 * treats {@code deletionDeadline <= now} as a miss and the sweeper deletes such rows.
 */
@MsgpackPojo
@NullMarked
public final class MessageStartProcessInstanceDedupEntry
    extends MsgpackBufferValue<MessageStartProcessInstanceDedupEntry> implements DbValue {

  long processInstanceKey = -1L;
  long deletionDeadline = -1L;
  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public MessageStartProcessInstanceDedupEntry setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public MessageStartProcessInstanceDedupEntry setProcessInstanceKey(
      final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public long getDeletionDeadline() {
    return deletionDeadline;
  }

  public MessageStartProcessInstanceDedupEntry setDeletionDeadline(final long deletionDeadline) {
    this.deletionDeadline = deletionDeadline;
    return this;
  }

  @Override
  protected MsgpackCodec<MessageStartProcessInstanceDedupEntry> codec() {
    return MessageStartProcessInstanceDedupEntryCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<MessageStartProcessInstanceDedupEntry> ops() {
    return MessageStartProcessInstanceDedupEntryOps.INSTANCE;
  }
}
