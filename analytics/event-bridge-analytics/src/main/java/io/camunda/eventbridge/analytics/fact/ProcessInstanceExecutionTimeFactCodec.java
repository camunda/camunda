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
import java.util.LinkedHashMap;
import java.util.Map;

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
      out.writeInt(fact.variables().size());
      for (final Map.Entry<String, String> variable : fact.variables().entrySet()) {
        out.writeUTF(variable.getKey());
        out.writeUTF(variable.getValue());
      }
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to serialize execution-time fact", e);
    }
    return bytes.toByteArray();
  }

  public ProcessInstanceExecutionTimeFact deserialize(final byte[] payload) {
    try (final DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
      final long processInstanceKey = in.readLong();
      final long processDefinitionKey = in.readLong();
      final String bpmnProcessId = in.readUTF();
      final int version = in.readInt();
      final String tenantId = in.readUTF();
      final long startTime = in.readLong();
      final long endTime = in.readLong();
      final long durationMs = in.readLong();
      final boolean completedNormally = in.readBoolean();
      final int sourcePartitionId = in.readInt();
      final long sourcePosition = in.readLong();
      final int variableCount = in.readInt();
      final Map<String, String> variables = new LinkedHashMap<>();
      for (int i = 0; i < variableCount; i++) {
        variables.put(in.readUTF(), in.readUTF());
      }
      return new ProcessInstanceExecutionTimeFact(
          processInstanceKey,
          processDefinitionKey,
          bpmnProcessId,
          version,
          tenantId,
          startTime,
          endTime,
          durationMs,
          completedNormally,
          sourcePartitionId,
          sourcePosition,
          Map.copyOf(variables));
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to deserialize execution-time fact", e);
    }
  }
}
