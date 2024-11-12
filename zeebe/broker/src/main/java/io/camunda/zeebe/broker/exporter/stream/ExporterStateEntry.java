/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ExporterStateEntry extends UnpackedObject implements DbValue {

  private static final UnsafeBuffer EMPTY_METADATA = new UnsafeBuffer();

  private final LongProperty positionProp = new LongProperty("exporterPosition");
  private final BinaryProperty metadataProp =
      new BinaryProperty("exporterMetadata", EMPTY_METADATA);

  private final LongProperty metadataVersionProp = new LongProperty("metadataVersion", 0L);

  public ExporterStateEntry() {
    super(3);
    declareProperty(positionProp)
        .declareProperty(metadataProp)
        .declareProperty(metadataVersionProp);
  }

  public long getPosition() {
    return positionProp.getValue();
  }

  public ExporterStateEntry setPosition(final long position) {
    positionProp.setValue(position);
    return this;
  }

  public DirectBuffer getMetadata() {
    // Clone the buffer to avoid misuse. The buffer is reused by the state.
    return BufferUtil.cloneBuffer(metadataProp.getValue());
  }

  public ExporterStateEntry setMetadata(final DirectBuffer metadata) {
    metadataProp.setValue(metadata);
    return this;
  }

  public long getMetadataVersion() {
    return metadataVersionProp.getValue();
  }

  public ExporterStateEntry setMetadataVersion(final long metadataVersion) {
    metadataVersionProp.setValue(metadataVersion);
    return this;
  }
}
