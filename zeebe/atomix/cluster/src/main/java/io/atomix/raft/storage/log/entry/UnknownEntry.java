/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.storage.log.entry;

import io.atomix.raft.storage.serializer.RaftEntrySerializer;
import io.camunda.zeebe.util.buffer.BufferWriter;

/**
 * Represents a Raft entry of an unknown type. This entry doesn't provide any information about the
 * entry and can't be serialized again. This type is used when a newer version introduced a new
 * entry type that isn't supported yet.
 */
public record UnknownEntry() implements RaftEntry {

  @Override
  public BufferWriter toSerializable(final long term, final RaftEntrySerializer serializer) {
    throw new UnsupportedOperationException("Can't serialize unknown entry");
  }
}
