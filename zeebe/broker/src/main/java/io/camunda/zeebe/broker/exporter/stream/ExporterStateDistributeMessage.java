/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.broker.protocol.ExporterStateDecoder;
import io.camunda.zeebe.broker.protocol.ExporterStateEncoder;
import io.camunda.zeebe.broker.protocol.ExporterStateEncoder.StateEncoder;
import io.camunda.zeebe.protocol.impl.encoding.SbeBufferWriterReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

public class ExporterStateDistributeMessage
    extends SbeBufferWriterReader<ExporterStateEncoder, ExporterStateDecoder> {

  private final Map<String, ExporterStateEntry> exporterState = new HashMap<>();
  private final ExporterStateEncoder encoder = new ExporterStateEncoder();
  private final ExporterStateDecoder decoder = new ExporterStateDecoder();

  @Override
  protected ExporterStateEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected ExporterStateDecoder getBodyDecoder() {
    return decoder;
  }

  @Override
  public void reset() {
    super.reset();
    exporterState.clear();
  }

  @Override
  public int getLength() {
    final var length = new MutableInteger();
    exporterState.forEach(
        (id, state) ->
            length.addAndGet(
                StateEncoder.positionEncodingLength()
                    + StateEncoder.exporterIdHeaderLength()
                    + StateEncoder.metadataHeaderLength()
                    + id.length()
                    + state.metadata.capacity()));

    return super.getLength() + StateEncoder.sbeHeaderSize() + length.get();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    super.write(buffer, offset);

    final var stateEncoder = encoder.stateCount(exporterState.size());
    exporterState.forEach(
        (id, state) -> {
          final var idBuffer = BufferUtil.wrapString(id);
          final var metadata = state.metadata;

          stateEncoder
              .next()
              .position(state.position)
              .putExporterId(idBuffer, 0, idBuffer.capacity())
              .putMetadata(metadata, 0, metadata.capacity());
        });
    return headerEncoder.encodedLength() + encoder.encodedLength();
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    super.wrap(buffer, offset, length);

    final var stateDecoder = decoder.state();

    while (stateDecoder.hasNext()) {
      final var next = stateDecoder.next();
      final var position = next.position();

      final var exporterIdLength = next.exporterIdLength();
      final var exporterIdBytes = new byte[exporterIdLength];
      next.getExporterId(exporterIdBytes, 0, exporterIdLength);

      final var metadataLength = next.metadataLength();
      final var metadataBytes = new byte[metadataLength];
      next.getMetadata(metadataBytes, 0, metadataLength);
      final var metadataBuffer = new UnsafeBuffer(metadataBytes);

      exporterState.put(
          new String(exporterIdBytes), new ExporterStateEntry(position, metadataBuffer));
    }
  }

  public void putExporter(
      final String exporterId,
      final long lastExportedPosition,
      final DirectBuffer exporterMetadata) {
    exporterState.put(exporterId, new ExporterStateEntry(lastExportedPosition, exporterMetadata));
  }

  public Map<String, ExporterStateEntry> getExporterState() {
    return exporterState;
  }

  record ExporterStateEntry(long position, DirectBuffer metadata) {

    @Override
    public String toString() {
      return "{"
          + "position="
          + position
          + ", metadata="
          + BufferUtil.bufferAsString(metadata)
          + '}';
    }
  }
}
