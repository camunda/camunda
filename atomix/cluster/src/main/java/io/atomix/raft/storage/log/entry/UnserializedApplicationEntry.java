/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.storage.log.entry;

import io.atomix.raft.storage.serializer.RaftEntrySerializer;
import io.atomix.raft.storage.serializer.RaftEntrySerializer.SerializedBufferWriterAdapter;
import io.camunda.zeebe.util.buffer.BufferWriter;

/**
 * An {@link ApplicationEntry} with unserialized application data. Used for writing new entries to
 * the log.
 */
public record UnserializedApplicationEntry(
    long lowestPosition, long highestPosition, BufferWriter writer) implements ApplicationEntry {

  @Override
  public BufferWriter toSerializable(final long term, final RaftEntrySerializer serializer) {
    return new SerializedBufferWriterAdapter(
        () -> serializer.getApplicationEntrySerializedLength(this),
        (buffer, offset) -> serializer.writeApplicationEntry(term, this, buffer, offset));
  }

  @Override
  public BufferWriter dataWriter() {
    return writer;
  }
}
