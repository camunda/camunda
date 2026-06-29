/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.fact;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Serializes a {@link ProcessInstanceExecutionTimeFact} to the bytes carried as a fact-stream
 * payload, and back. A flat, self-contained binary encoding (fixed-width fields plus UTF strings)
 * keeps the fact independent of the consumer; it can be swapped for a richer/self-describing format
 * later without touching the pipeline stages.
 */
public final class ProcessInstanceExecutionTimeFactCodec {

  public byte[] serialize(final ProcessInstanceExecutionTimeFact fact) {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (final DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeLong(fact.processInstanceKey());
      out.writeLong(fact.processDefinitionKey());
      out.writeUTF(fact.bpmnProcessId());
      out.writeInt(fact.version());
      out.writeUTF(fact.tenantId());
      out.writeLong(fact.startTime());
      out.writeLong(fact.endTime());
      out.writeLong(fact.durationMs());
      out.writeBoolean(fact.completedNormally());
      out.writeInt(fact.sourcePartitionId());
      out.writeLong(fact.sourcePosition());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to serialize execution-time fact", e);
    }
    return bytes.toByteArray();
  }

  public ProcessInstanceExecutionTimeFact deserialize(final byte[] payload) {
    try (final DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
      return new ProcessInstanceExecutionTimeFact(
          in.readLong(),
          in.readLong(),
          in.readUTF(),
          in.readInt(),
          in.readUTF(),
          in.readLong(),
          in.readLong(),
          in.readLong(),
          in.readBoolean(),
          in.readInt(),
          in.readLong());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to deserialize execution-time fact", e);
    }
  }
}
