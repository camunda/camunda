/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import io.camunda.eventbridge.streaming.aggregate.Codec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Byte codec for {@link RegionKey}, so the durable rollup can persist it as a cell-key component.
 */
public final class RegionKeyCodec implements Codec<RegionKey> {

  @Override
  public byte[] encode(final RegionKey key) {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (final DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeUTF(key.region());
      out.writeUTF(key.bpmnProcessId());
      out.writeLong(key.processDefinitionKey());
      out.writeInt(key.version());
      out.writeUTF(key.tenantId());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to encode RegionKey", e);
    }
    return bytes.toByteArray();
  }

  @Override
  public RegionKey decode(final byte[] bytes) {
    try (final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      final String region = in.readUTF();
      final String bpmnProcessId = in.readUTF();
      final long processDefinitionKey = in.readLong();
      final int version = in.readInt();
      final String tenantId = in.readUTF();
      return new RegionKey(region, bpmnProcessId, processDefinitionKey, version, tenantId);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to decode RegionKey", e);
    }
  }
}
