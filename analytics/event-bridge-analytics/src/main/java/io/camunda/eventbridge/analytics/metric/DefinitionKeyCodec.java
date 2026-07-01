/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import io.camunda.analytics.streaming.aggregate.Codec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Byte codec for {@link DefinitionKey}, so the durable rollup can persist it as a cell-key
 * component.
 */
public final class DefinitionKeyCodec implements Codec<DefinitionKey> {

  @Override
  public byte[] encode(final DefinitionKey key) {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (final DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeUTF(key.bpmnProcessId());
      out.writeLong(key.processDefinitionKey());
      out.writeInt(key.version());
      out.writeUTF(key.tenantId());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to encode DefinitionKey", e);
    }
    return bytes.toByteArray();
  }

  @Override
  public DefinitionKey decode(final byte[] bytes) {
    try (final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      final String bpmnProcessId = in.readUTF();
      final long processDefinitionKey = in.readLong();
      final int version = in.readInt();
      final String tenantId = in.readUTF();
      return new DefinitionKey(bpmnProcessId, processDefinitionKey, version, tenantId);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to decode DefinitionKey", e);
    }
  }
}
