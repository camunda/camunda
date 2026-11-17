/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.distribution;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.ProjectionOf;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class PersistedCommandDistribution
    extends MsgpackBufferValue<PersistedCommandDistribution>
    implements DbValue, ProjectionOf<CommandDistributionRecord, PersistedCommandDistribution> {

  /** The empty msgpack map, the stored command value while there is none. */
  private static final DirectBuffer EMPTY_MAP = new UnsafeBuffer(new byte[] {(byte) 0x80});

  private static final DirectBuffer EMPTY_AUTH_INFO = encode(AuthInfo.EMPTY);

  DirectBuffer queueId = Decoding.EMPTY;
  @Nullable ValueType valueType;
  int intent = Intent.NULL_VAL;

  @MsgpackProperty(raw = true)
  DirectBuffer commandValue = EMPTY_MAP;

  @MsgpackProperty(raw = true)
  DirectBuffer authInfo = EMPTY_AUTH_INFO;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public PersistedCommandDistribution wrap(
      final CommandDistributionRecord commandDistributionRecord) {
    if (commandDistributionRecord.getQueueId() != null) {
      setQueueId(commandDistributionRecord.getQueueId());
    } else {
      queueId = Decoding.EMPTY;
    }
    valueType = commandDistributionRecord.getValueType();
    intent = commandDistributionRecord.getIntent().value();
    commandValue = encode(commandDistributionRecord.getCommandValue());
    authInfo = encode(commandDistributionRecord.getAuthInfo());

    return this;
  }

  public Optional<String> getQueueId() {
    final var value = BufferUtil.bufferAsString(queueId);
    return value.isEmpty() ? Optional.empty() : Optional.of(value);
  }

  public PersistedCommandDistribution setQueueId(final String queueId) {
    this.queueId = BufferUtil.wrapString(queueId);
    return this;
  }

  public ValueType getValueType() {
    return Decoding.required(valueType, "valueType");
  }

  public Intent getIntent() {
    final int intentValue = intent;
    if (intentValue < 0 || intentValue > Short.MAX_VALUE) {
      throw new IllegalStateException(
          String.format(
              "Expected to read the intent, but it's persisted value '%d' is not a short integer",
              intentValue));
    }
    return Intent.fromProtocolValue(getValueType(), (short) intentValue);
  }

  public MsgpackRecordValue getCommandValue() {
    if (EMPTY_MAP.equals(commandValue)) {
      return new UnifiedRecordValue(0);
    }

    final var type = getValueType();
    final var concreteCommandValue = MsgpackRecordValue.fromValueType(type);
    if (concreteCommandValue == null) {
      throw new IllegalStateException(
          "Expected to read the command value, but its value type `"
              + type.name()
              + "` has no known record value class");
    }

    // the stored bytes are a view on the buffer they were read from, so the record gets a copy
    concreteCommandValue.wrap(BufferUtil.cloneBuffer(commandValue));
    return concreteCommandValue;
  }

  public AuthInfo getAuthInfo() {
    return AuthInfo.decode(authInfo, 0, authInfo.capacity());
  }

  private static DirectBuffer encode(final BufferWriter value) {
    final var bytes = new UnsafeBuffer(new byte[value.getLength()]);
    value.write(bytes, 0);
    return bytes;
  }

  @Override
  protected MsgpackCodec<PersistedCommandDistribution> codec() {
    return PersistedCommandDistributionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedCommandDistribution> ops() {
    return PersistedCommandDistributionOps.INSTANCE;
  }
}
