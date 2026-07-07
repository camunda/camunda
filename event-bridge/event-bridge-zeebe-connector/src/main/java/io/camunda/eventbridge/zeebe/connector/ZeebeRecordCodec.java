/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.zeebe.connector;

import io.camunda.eventbridge.client.EventBridgeException;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.VersionInfo;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.nio.ByteOrder;
import java.util.function.BiPredicate;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Binary codec for Zeebe records carried as Event Bridge payloads.
 *
 * <p>A payload is the <em>event</em> (the record value, in its native MsgPack form) together with
 * its {@link RecordMetadata} (SBE-encoded), the original event timestamp, and the record's Zeebe
 * origin coordinate. The timestamp is the engine event time and is part of the payload, since it is
 * a property of the event rather than a coordinate the carrying log assigns — analytics downstream
 * rely on it (e.g. process execution time). The origin coordinate {@code (partitionId, position)}
 * is carried too (ADR 0007): it is the record's <em>stable identity</em> — positions are strictly
 * monotone per Zeebe partition — which consumers need to deduplicate at-least-once producer
 * re-appends. The Event Bridge envelope's own coordinates (partition, offset) are consumption/
 * progress coordinates, not identity, and keep flowing separately alongside the reconstructed
 * record. The record key <em>is</em> carried as well: for many value types (e.g. a {@code
 * PROCESS_INSTANCE} element record) the key is the element-instance identity that downstream
 * analytics correlate an activation with its completion on, so dropping it would collapse every
 * element onto one state entry. The source record position is not carried and defaults to {@code
 * -1}.
 *
 * <p>Frame layout (little-endian):
 *
 * <pre>
 *   timestamp(8) | key(8) | position(8) | partitionId(4) | metadataLength(4) | metadata[metadataLength] | value[...]
 * </pre>
 *
 * <p><b>Threading.</b> A codec instance is <em>not</em> thread-safe: it reuses internal buffer
 * wrappers (and, for {@link #accepts}, a metadata flyweight) across calls to keep the per-record
 * paths allocation-free. Confine each instance to a single thread — e.g. the exporter thread on the
 * serialize side, or the stream runtime's source thread on the consume side. The <em>results</em>
 * are safe to hand off: {@link #serialize} returns a fresh array and {@link #deserialize} builds
 * its record from per-call metadata/value objects, so a decoded record may outlive the call (e.g.
 * buffered in a partition queue) without aliasing codec state.
 */
public final class ZeebeRecordCodec {

  private static final ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;
  private static final int TIMESTAMP_FIELD = Long.BYTES;
  private static final int KEY_FIELD = Long.BYTES;
  private static final int POSITION_FIELD = Long.BYTES;
  private static final int PARTITION_ID_FIELD = Integer.BYTES;
  private static final int METADATA_LENGTH_FIELD = Integer.BYTES;

  /** Reusable wrapper around the payload being written; the payload array itself is per-call. */
  private final UnsafeBuffer writeBuffer = new UnsafeBuffer(0, 0);

  /**
   * Reusable wrapper around the payload being read. Rewrapping is safe even though a deserialized
   * value/metadata may retain views of the payload <em>array</em>: the decoded objects wrap the
   * underlying array, never this wrapper object.
   */
  private final UnsafeBuffer readBuffer = new UnsafeBuffer(0, 0);

  /**
   * Metadata flyweight for {@link #accepts}/{@link #timestamp} peeks only — nothing of it escapes
   * (the filter sees enums). {@link #deserialize} deliberately builds a fresh {@link
   * RecordMetadata} instead, because the returned {@link CopiedRecord} retains it.
   */
  private final RecordMetadata peekMetadata = new RecordMetadata();

  /** Serializes a record's metadata and value into a single Event Bridge payload. */
  public byte[] serialize(final Record<?> record) {
    final RecordMetadata metadata = toMetadata(record);
    final UnifiedRecordValue value = asUnifiedValue(record);

    final int metadataLength = metadata.getLength();
    final int valueLength = value.getLength();
    final byte[] payload =
        new byte
            [TIMESTAMP_FIELD
                + KEY_FIELD
                + POSITION_FIELD
                + PARTITION_ID_FIELD
                + METADATA_LENGTH_FIELD
                + metadataLength
                + valueLength];
    final MutableDirectBuffer buffer = writeBuffer;
    buffer.wrap(payload);

    int offset = 0;
    buffer.putLong(offset, record.getTimestamp(), ORDER);
    offset += TIMESTAMP_FIELD;
    buffer.putLong(offset, record.getKey(), ORDER);
    offset += KEY_FIELD;
    buffer.putLong(offset, record.getPosition(), ORDER);
    offset += POSITION_FIELD;
    buffer.putInt(offset, record.getPartitionId(), ORDER);
    offset += PARTITION_ID_FIELD;
    buffer.putInt(offset, metadataLength, ORDER);
    offset += METADATA_LENGTH_FIELD;
    metadata.write(buffer, offset);
    offset += metadataLength;
    value.write(buffer, offset);

    return payload;
  }

  /**
   * Reconstructs a record from an Event Bridge payload. The event timestamp, record key, and the
   * record's <em>real</em> Zeebe origin coordinate {@code (partitionId, position)} are all carried
   * in the payload and preserved — the Event Bridge envelope's coordinates never leak into the
   * record; they flow separately as consumption/progress coordinates. The source record position is
   * not carried and defaults to {@code -1}.
   */
  public Record<?> deserialize(final byte[] payload) {
    final DirectBuffer buffer = readBuffer;
    readBuffer.wrap(payload);

    int offset = 0;
    final long timestamp = buffer.getLong(offset, ORDER);
    offset += TIMESTAMP_FIELD;
    final long key = buffer.getLong(offset, ORDER);
    offset += KEY_FIELD;
    final long position = buffer.getLong(offset, ORDER);
    offset += POSITION_FIELD;
    final int partitionId = buffer.getInt(offset, ORDER);
    offset += PARTITION_ID_FIELD;
    final int metadataLength = buffer.getInt(offset, ORDER);
    offset += METADATA_LENGTH_FIELD;

    final RecordMetadata metadata = new RecordMetadata();
    metadata.wrap(buffer, offset, metadataLength);
    offset += metadataLength;

    final ValueType valueType = metadata.getValueType();
    final UnifiedRecordValue value = UnifiedRecordValue.fromValueType(valueType);
    if (value == null) {
      throw new EventBridgeException("Unknown Zeebe record value type: " + valueType);
    }
    value.wrap(buffer, offset, payload.length - offset);

    return new CopiedRecord<>(value, metadata, key, partitionId, position, -1L, timestamp);
  }

  /**
   * Reads only the record's event timestamp — the payload's leading field — without any decode.
   * Cheap enough to run per filtered record, so a filtered run's coalesced advance can carry the
   * run's event time and keep stream time moving.
   */
  public long timestamp(final byte[] payload) {
    readBuffer.wrap(payload);
    return readBuffer.getLong(0, ORDER);
  }

  /**
   * Reads only the record's {@link ValueType} and {@link Intent} from the payload's metadata,
   * skipping the (MsgPack) value decode — cheap enough to filter records before a full {@link
   * #deserialize}. Returns whether {@code filter} accepts the pair; a consumer that folds only a
   * subset of record types can skip the far costlier value decode for the rest.
   */
  public boolean accepts(final byte[] payload, final BiPredicate<ValueType, Intent> filter) {
    final DirectBuffer buffer = readBuffer;
    readBuffer.wrap(payload);
    // skip the timestamp, key, and origin coordinate
    int offset = TIMESTAMP_FIELD + KEY_FIELD + POSITION_FIELD + PARTITION_ID_FIELD;
    final int metadataLength = buffer.getInt(offset, ORDER);
    offset += METADATA_LENGTH_FIELD;
    // wrap() resets the flyweight before decoding, so no state leaks between records.
    peekMetadata.wrap(buffer, offset, metadataLength);
    return filter.test(peekMetadata.getValueType(), peekMetadata.getIntent());
  }

  private static RecordMetadata toMetadata(final Record<?> record) {
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(record.getRecordType())
            .valueType(record.getValueType())
            .rejectionType(record.getRejectionType())
            .recordVersion(record.getRecordVersion())
            .operationReference(record.getOperationReference())
            .batchOperationReference(record.getBatchOperationReference());

    final Intent intent = record.getIntent();
    if (intent != null) {
      metadata.intent(intent);
    }
    final String rejectionReason = record.getRejectionReason();
    if (rejectionReason != null && !rejectionReason.isEmpty()) {
      metadata.rejectionReason(rejectionReason);
    }
    final String brokerVersion = record.getBrokerVersion();
    if (brokerVersion != null && !brokerVersion.isEmpty()) {
      metadata.brokerVersion(VersionInfo.parse(brokerVersion));
    }
    return metadata;
  }

  private static UnifiedRecordValue asUnifiedValue(final Record<?> record) {
    final Object value = record.getValue();
    if (!(value instanceof final UnifiedRecordValue unifiedValue)) {
      throw new EventBridgeException(
          "Cannot serialize record value of type "
              + (value == null ? "null" : value.getClass().getName())
              + "; expected a "
              + UnifiedRecordValue.class.getName());
    }
    return unifiedValue;
  }
}
