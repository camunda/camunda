/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.core.logappend;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import org.agrona.DirectBuffer;

/**
 * A {@link LogAppendEntry} that wraps a raw binary event payload. This allows the Event Bridge to
 * write application events into the LogStream without using Zeebe domain record types.
 *
 * <p>The {@link RecordMetadata} is initialized with neutral type markers ({@link
 * ValueType#NULL_VAL}, {@link RecordType#NULL_VAL}) since the Event Bridge does not use Zeebe's
 * typed record model.
 */
public final class EventLogAppendEntry implements LogAppendEntry {

  private static final RecordMetadata NEUTRAL_METADATA = createNeutralMetadata();

  private final RawEventRecordValue recordValue = new RawEventRecordValue();
  private long key;

  public EventLogAppendEntry() {}

  /**
   * Configures this entry to carry the given raw payload.
   *
   * @param key logical key for this log entry ({@code -1} for no key)
   * @param payload raw event bytes
   * @param offset offset into {@code payload}
   * @param length number of bytes to read
   * @return {@code this} for chaining
   */
  public EventLogAppendEntry wrap(
      final long key, final DirectBuffer payload, final int offset, final int length) {
    this.key = key;
    recordValue.wrapPayload(payload, offset, length);
    return this;
  }

  /**
   * Configures this entry to carry the given raw payload byte array.
   *
   * @param key logical key
   * @param payload raw event bytes
   * @return {@code this} for chaining
   */
  public EventLogAppendEntry wrap(final long key, final byte[] payload) {
    this.key = key;
    recordValue.wrapPayload(payload);
    return this;
  }

  @Override
  public long key() {
    return key;
  }

  @Override
  public int sourceIndex() {
    return -1;
  }

  @Override
  public RecordMetadata recordMetadata() {
    return NEUTRAL_METADATA;
  }

  @Override
  public UnifiedRecordValue recordValue() {
    return recordValue;
  }

  private static RecordMetadata createNeutralMetadata() {
    final var meta = new RecordMetadata();
    meta.recordType(RecordType.NULL_VAL);
    meta.valueType(ValueType.NULL_VAL);
    return meta;
  }
}
