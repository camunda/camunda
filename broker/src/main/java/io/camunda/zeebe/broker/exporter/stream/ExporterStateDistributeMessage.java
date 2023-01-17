/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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

public class ExporterStateDistributeMessage
    extends SbeBufferWriterReader<ExporterStateEncoder, ExporterStateDecoder> {

  private final Map<String, Long> exporterPositions = new HashMap<>();
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
    exporterPositions.clear();
  }

  @Override
  public int getLength() {

    final var length = new MutableInteger();
    exporterPositions.forEach(
        (id, pos) ->
            length.addAndGet(
                StateEncoder.positionEncodingLength()
                    + StateEncoder.exporterIdHeaderLength()
                    + StateEncoder.metadataHeaderLength()
                    + id.length()));

    return super.getLength() + StateEncoder.sbeHeaderSize() + length.get();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    super.write(buffer, offset);

    final var stateEncoder = encoder.stateCount(exporterPositions.size());

    exporterPositions.forEach(
        (id, pos) -> {
          final var idBuffer = BufferUtil.wrapString(id);
          stateEncoder.next().position(pos).putExporterId(idBuffer, 0, idBuffer.capacity());
        });
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    super.wrap(buffer, offset, length);

    final var stateDecoder = decoder.state();

    while (stateDecoder.hasNext()) {
      final var next = stateDecoder.next();
      final var position = next.position();

      final var exporterIdLength = next.exporterIdLength();
      final var bytes = new byte[exporterIdLength];
      next.getExporterId(bytes, 0, exporterIdLength);
      exporterPositions.put(new String(bytes), position);
    }
  }

  public void putExporter(final String exporterId, final long lastExportedPosition) {
    exporterPositions.put(exporterId, lastExportedPosition);
  }

  public Map<String, Long> getExporterPositions() {
    return exporterPositions;
  }
}
