/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor;

import static io.zeebe.engine.processing.streamprocessor.TypedEventRegistry.EVENT_REGISTRY;

import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.impl.record.CopiedRecord;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.util.ReflectUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class CopiedRecords {

  public static CopiedRecord createCopiedRecord(final int partitionId, final LoggedEvent rawEvent) {
    // we have to access the underlying buffer and copy the metadata and value bytes
    // otherwise next event will overwrite the event before, since UnpackedObject
    // and RecordMetadata has properties (buffers, StringProperty etc.) which only wraps the given
    // buffer instead of copying it

    final DirectBuffer contentBuffer = rawEvent.getValueBuffer();

    final byte[] metadataBytes = new byte[rawEvent.getMetadataLength()];
    contentBuffer.getBytes(rawEvent.getMetadataOffset(), metadataBytes);
    final DirectBuffer metadataBuffer = new UnsafeBuffer(metadataBytes);

    final RecordMetadata metadata = new RecordMetadata();
    metadata.wrap(metadataBuffer, 0, metadataBuffer.capacity());

    final byte[] valueBytes = new byte[rawEvent.getValueLength()];
    contentBuffer.getBytes(rawEvent.getValueOffset(), valueBytes);
    final DirectBuffer valueBuffer = new UnsafeBuffer(valueBytes);

    final UnifiedRecordValue recordValue =
        ReflectUtil.newInstance(EVENT_REGISTRY.get(metadata.getValueType()));
    recordValue.wrap(valueBuffer);

    return new CopiedRecord<>(
        recordValue,
        metadata,
        rawEvent.getKey(),
        partitionId,
        rawEvent.getPosition(),
        rawEvent.getSourceEventPosition(),
        rawEvent.getTimestamp());
  }
}
