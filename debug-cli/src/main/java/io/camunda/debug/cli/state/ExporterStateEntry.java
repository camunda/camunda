/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Byte-compatible mirror of {@code io.camunda.zeebe.broker.exporter.stream.ExporterStateEntry}, the
 * value stored in the {@code EXPORTER} column family.
 *
 * <p>It is copied verbatim here on purpose: the original lives in {@code zeebe-broker}, which
 * transitively drags in spring-boot, the gateway, the workflow engine and the cloud backup stores.
 * Pulling that whole tree into a lean debug CLI just to read/write a three-field msgpack record is
 * disproportionate. The property names, order and types below MUST stay identical to the broker's
 * class so the encoded bytes round-trip with a running broker.
 */
final class ExporterStateEntry extends UnpackedObject implements DbValue {

  private static final UnsafeBuffer EMPTY_METADATA = new UnsafeBuffer();

  private final LongProperty positionProp = new LongProperty("exporterPosition");
  private final BinaryProperty metadataProp =
      new BinaryProperty("exporterMetadata", EMPTY_METADATA);

  private final LongProperty metadataVersionProp = new LongProperty("metadataVersion", 0L);

  ExporterStateEntry() {
    super(3);
    declareProperty(positionProp)
        .declareProperty(metadataProp)
        .declareProperty(metadataVersionProp);
  }

  long getPosition() {
    return positionProp.getValue();
  }

  ExporterStateEntry setPosition(final long position) {
    positionProp.setValue(position);
    return this;
  }

  DirectBuffer getMetadata() {
    // Clone the buffer to avoid misuse. The buffer is reused by the state.
    return BufferUtil.cloneBuffer(metadataProp.getValue());
  }

  ExporterStateEntry setMetadata(final DirectBuffer metadata) {
    metadataProp.setValue(metadata);
    return this;
  }

  long getMetadataVersion() {
    return metadataVersionProp.getValue();
  }

  ExporterStateEntry setMetadataVersion(final long metadataVersion) {
    metadataVersionProp.setValue(metadataVersion);
    return this;
  }
}
