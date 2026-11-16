/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistorybatch;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryBatchRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class AgentHistoryBatchRecord extends MsgpackBufferValue<AgentHistoryBatchRecord>
    implements MsgpackRecordValue, AgentHistoryBatchRecordValue {

  long agentInstanceKey = -1L;
  final List<DirectBuffer> historyItemIds = new ArrayList<>();
  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getAgentInstanceKey() {
    return agentInstanceKey;
  }

  public AgentHistoryBatchRecord setAgentInstanceKey(final long agentInstanceKey) {
    this.agentInstanceKey = agentInstanceKey;
    return this;
  }

  @Override
  public List<String> getHistoryItemIds() {
    return historyItemIds.stream().map(BufferUtil::bufferAsString).toList();
  }

  public AgentHistoryBatchRecord setHistoryItemIds(final List<String> historyItemIds) {
    this.historyItemIds.clear();
    if (historyItemIds != null) {
      historyItemIds.forEach(id -> this.historyItemIds.add(BufferUtil.wrapString(id)));
    }
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public AgentHistoryBatchRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<AgentHistoryBatchRecord> codec() {
    return AgentHistoryBatchRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AgentHistoryBatchRecord> ops() {
    return AgentHistoryBatchRecordOps.INSTANCE;
  }
}
