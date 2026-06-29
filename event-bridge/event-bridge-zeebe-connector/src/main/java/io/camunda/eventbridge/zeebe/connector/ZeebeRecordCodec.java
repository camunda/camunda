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
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Binary codec for Zeebe records carried as Event Bridge payloads.
 *
 * <p>A payload is the <em>event</em> (the record value, in its native MsgPack form) together with
 * its {@link RecordMetadata} (SBE-encoded) and the original event timestamp. The timestamp is the
 * engine event time and is part of the payload, since it is a property of the event rather than a
 * coordinate the carrying log assigns — analytics downstream rely on it (e.g. process execution
 * time). Pure log coordinates that the source log assigns — position, partition — are re-supplied
 * from the Event Bridge envelope when a record is reconstructed on the consume side (see {@link
 * #deserialize(byte[], int, long)}); key and source record position are not carried and default to
 * {@code -1}.
 *
 * <p>Frame layout (little-endian):
 *
 * <pre>
 *   timestamp(8) | metadataLength(4) | metadata[metadataLength] | value[...]
 * </pre>
 */
public final class ZeebeRecordCodec {

  private static final ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;
  private static final int TIMESTAMP_FIELD = Long.BYTES;
  private static final int METADATA_LENGTH_FIELD = Integer.BYTES;

  /** Serializes a record's metadata and value into a single Event Bridge payload. */
  public byte[] serialize(final Record<?> record) {
    final RecordMetadata metadata = toMetadata(record);
    final UnifiedRecordValue value = asUnifiedValue(record);

    final int metadataLength = metadata.getLength();
    final int valueLength = value.getLength();
    final byte[] payload =
        new byte[TIMESTAMP_FIELD + METADATA_LENGTH_FIELD + metadataLength + valueLength];
    final MutableDirectBuffer buffer = new UnsafeBuffer(payload);

    int offset = 0;
    buffer.putLong(offset, record.getTimestamp(), ORDER);
    offset += TIMESTAMP_FIELD;
    buffer.putInt(offset, metadataLength, ORDER);
    offset += METADATA_LENGTH_FIELD;
    metadata.write(buffer, offset);
    offset += metadataLength;
    value.write(buffer, offset);

    return payload;
  }

  /**
   * Reconstructs a record from an Event Bridge payload. The event timestamp is carried in the
   * payload and preserved; the pure log coordinates position and partition are taken from the
   * envelope. Key and source record position are not carried and default to {@code -1}.
   */
  public Record<?> deserialize(final byte[] payload, final int partitionId, final long position) {
    final DirectBuffer buffer = new UnsafeBuffer(payload);

    int offset = 0;
    final long timestamp = buffer.getLong(offset, ORDER);
    offset += TIMESTAMP_FIELD;
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

    return new CopiedRecord<>(value, metadata, -1L, partitionId, position, -1L, timestamp);
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
