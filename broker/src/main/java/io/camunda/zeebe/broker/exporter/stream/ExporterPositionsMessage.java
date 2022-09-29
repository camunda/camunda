/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.broker.protocol.ExporterPositionsDecoder;
import io.camunda.zeebe.broker.protocol.ExporterPositionsEncoder;
import io.camunda.zeebe.broker.protocol.ExporterPositionsEncoder.BucketsEncoder;
import io.camunda.zeebe.broker.protocol.ExporterPositionsEncoder.PositionsEncoder;
import io.camunda.zeebe.protocol.impl.encoding.SbeBufferWriterReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableInteger;

public class ExporterPositionsMessage
    extends SbeBufferWriterReader<ExporterPositionsEncoder, ExporterPositionsDecoder> {

  private final Map<String, Long> exporterPositions = new HashMap<>();
  private final Map<String, Map<String, Long>> exporterSequences = new HashMap<>();
  private final ExporterPositionsEncoder encoder = new ExporterPositionsEncoder();
  private final ExporterPositionsDecoder decoder = new ExporterPositionsDecoder();

  @Override
  protected ExporterPositionsEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected ExporterPositionsDecoder getBodyDecoder() {
    return decoder;
  }

  @Override
  public void reset() {
    super.reset();
    exporterPositions.clear();
    exporterSequences.clear();
  }

  @Override
  public int getLength() {

    final var length = new MutableInteger();
    exporterPositions.forEach(
        (id, pos) ->
            length.addAndGet(
                PositionsEncoder.positionEncodingLength()
                    + PositionsEncoder.exporterIdHeaderLength()
                    + id.length()));

    exporterSequences.forEach(
        (id, seqs) -> {
          seqs.forEach(
              (v, s) -> {
                length.addAndGet(
                    BucketsEncoder.sequenceEncodingLength()
                        + BucketsEncoder.valueTypeHeaderLength()
                        + v.length()
                        + BucketsEncoder.exporterIdHeaderLength()
                        + id.length());
              });
        });

    return super.getLength()
        + PositionsEncoder.sbeHeaderSize()
        + BucketsEncoder.sbeHeaderSize()
        + length.get();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    super.write(buffer, offset);

    final var positionsEncoder = encoder.positionsCount(exporterPositions.size());

    exporterPositions.forEach(
        (id, pos) -> {
          final var idBuffer = BufferUtil.wrapString(id);
          positionsEncoder.next().position(pos).putExporterId(idBuffer, 0, idBuffer.capacity());
        });

    final var size = new MutableInteger();
    exporterSequences.forEach(
        (k, seqs) -> {
          size.addAndGet(seqs.size());
        });

    final var bucketsEncoder = encoder.bucketsCount(size.get());
    exporterSequences.forEach(
        (e, seqs) -> {
          final var exporterIdBuffer = BufferUtil.wrapString(e);
          seqs.forEach(
              (v, s) -> {
                final var valueTypeBuffer = BufferUtil.wrapString(v);
                bucketsEncoder
                    .next()
                    .sequence(s)
                    .putValueType(valueTypeBuffer, 0, valueTypeBuffer.capacity())
                    .putExporterId(exporterIdBuffer, 0, exporterIdBuffer.capacity());
              });
        });
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    super.wrap(buffer, offset, length);

    final var positionsDecoder = decoder.positions();

    while (positionsDecoder.hasNext()) {
      final var next = positionsDecoder.next();
      final var position = next.position();

      final var exporterIdLength = next.exporterIdLength();
      final var bytes = new byte[exporterIdLength];
      next.getExporterId(bytes, 0, exporterIdLength);
      exporterPositions.put(new String(bytes), position);
    }

    final var bucketsDecoder = decoder.buckets();

    while (bucketsDecoder.hasNext()) {
      final var next = bucketsDecoder.next();
      final var sequence = next.sequence();

      final var valueTypeLength = next.valueTypeLength();
      final var valueTypeBytes = new byte[valueTypeLength];
      next.getValueType(valueTypeBytes, 0, valueTypeLength);
      final var valueType = new String(valueTypeBytes);

      final var exporterIdLength = next.exporterIdLength();
      final var exporterIdBytes = new byte[exporterIdLength];
      next.getExporterId(exporterIdBytes, 0, exporterIdLength);
      final var exporterId = new String(exporterIdBytes);

      final var seqs =
          exporterSequences.computeIfAbsent(exporterId, (k) -> new HashMap<String, Long>());
      seqs.put(valueType, sequence);
    }
  }

  public void putExporter(final String exporterId, final long lastExportedPosition) {
    exporterPositions.put(exporterId, lastExportedPosition);
  }

  public void putSequence(final String exporterId, final String valueType, final long sequence) {
    final var sequenceMap =
        exporterSequences.computeIfAbsent(exporterId, (k) -> new HashMap<String, Long>());
    sequenceMap.put(valueType, sequence);
  }

  public Map<String, Long> getExporterPositions() {
    return exporterPositions;
  }

  public Map<String, Map<String, Long>> getExporterSequences() {
    return exporterSequences;
  }
}
