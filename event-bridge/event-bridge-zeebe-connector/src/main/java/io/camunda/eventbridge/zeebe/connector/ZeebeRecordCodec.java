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
 * its {@link RecordMetadata} (SBE-encoded). Log-level fields that the source log assigns —
 * position, key, timestamp, source record position, partition — are intentionally not part of the
 * payload; they are re-supplied from the Event Bridge envelope when a record is reconstructed on
 * the consume side (see {@link #deserialize(byte[], int, long)}).
 *
 * <p>Frame layout (little-endian):
 *
 * <pre>
 *   metadataLength(4) | metadata[metadataLength] | value[...]
 * </pre>
 */
public final class ZeebeRecordCodec {

  private static final ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;
  private static final int METADATA_LENGTH_FIELD = Integer.BYTES;

  /** Serializes a record's metadata and value into a single Event Bridge payload. */
  public byte[] serialize(final Record<?> record) {
    final RecordMetadata metadata = toMetadata(record);
    final UnifiedRecordValue value = asUnifiedValue(record);

    final int metadataLength = metadata.getLength();
    final int valueLength = value.getLength();
    final byte[] payload = new byte[METADATA_LENGTH_FIELD + metadataLength + valueLength];
    final MutableDirectBuffer buffer = new UnsafeBuffer(payload);

    int offset = 0;
    buffer.putInt(offset, metadataLength, ORDER);
    offset += METADATA_LENGTH_FIELD;
    metadata.write(buffer, offset);
    offset += metadataLength;
    value.write(buffer, offset);

    return payload;
  }

  /**
   * Reconstructs a record from an Event Bridge payload, taking the log-level fields it cannot carry
   * (position, partition) from the envelope. Key, source record position and timestamp are not
   * carried and default to {@code -1}.
   */
  public Record<?> deserialize(final byte[] payload, final int partitionId, final long position) {
    final DirectBuffer buffer = new UnsafeBuffer(payload);

    int offset = 0;
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

    return new CopiedRecord<>(value, metadata, -1L, partitionId, position, -1L, -1L);
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
