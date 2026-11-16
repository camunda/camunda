/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageBatchRecordValue;
import java.util.ArrayList;
import java.util.List;
import org.agrona.collections.LongArrayList;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class MessageBatchRecord extends MsgpackBufferValue<MessageBatchRecord>
    implements MsgpackRecordValue, MessageBatchRecordValue {

  // Retained for backwards compatibility: 8.10+ no longer writes message keys onto the batch
  // command (the expiry processor queries the message state directly), but the property is kept so
  // records written by earlier versions still deserialize and the public getMessageKeys() contract
  // holds.
  final LongArrayList messageKeys = new LongArrayList();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public List<Long> getMessageKeys() {
    return new ArrayList<>(messageKeys);
  }

  @Override
  protected MsgpackCodec<MessageBatchRecord> codec() {
    return MessageBatchRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<MessageBatchRecord> ops() {
    return MessageBatchRecordOps.INSTANCE;
  }
}
