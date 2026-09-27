/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.atomix.raft.protocol.ExporterPosition;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.exporter.stream.ExporterStateDistributeMessage.ExporterStateEntry;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Exporter positions handed over to the desired leader of a coordinated leadership transfer,
 * carried on TimeoutNow. One instance lives for the lifetime of a partition.
 *
 * <p>On the leader, the active {@link ExporterDirector} mirrors every write to its {@link
 * ExportersState} here, so Raft can read it on its own thread without going through the exporter
 * actor. On the desired leader, the positions received with TimeoutNow are kept until the director
 * opened for the next term takes them.
 */
@NullMarked
public final class ExporterStateHandover {

  private static final Logger LOG = Loggers.EXPORTER_LOGGER;
  private static final byte[] NO_METADATA = new byte[0];

  private final Map<String, ExporterPosition> outgoing = new ConcurrentHashMap<>();
  private final AtomicReference<@Nullable Incoming> incoming = new AtomicReference<>();

  /**
   * Mirrors {@link ExportersState#setExporterState}: a null {@code metadata} keeps the previous
   * metadata.
   */
  void onStateSet(
      final String exporterId, final long position, final @Nullable DirectBuffer metadata) {
    outgoing.compute(
        exporterId,
        (id, previous) -> {
          final byte[] bytes;
          if (metadata != null) {
            bytes = BufferUtil.bufferAsArray(metadata);
          } else {
            bytes = previous != null ? previous.metadata() : NO_METADATA;
          }
          return new ExporterPosition(id, position, bytes);
        });
  }

  /** Mirrors {@link ExportersState#setPosition}: the metadata is left unchanged. */
  void onPositionSet(final String exporterId, final long position) {
    onStateSet(exporterId, position, null);
  }

  void onRemoved(final String exporterId) {
    outgoing.remove(exporterId);
  }

  /** Replaces the outgoing positions with everything {@code state} holds. */
  void seed(final ExportersState state) {
    outgoing.clear();
    state.visitExporterState(
        (exporterId, entry) ->
            outgoing.put(
                exporterId,
                new ExporterPosition(
                    exporterId,
                    entry.getPosition(),
                    BufferUtil.bufferAsArray(entry.getMetadata()))));
  }

  void clearOutgoing() {
    outgoing.clear();
  }

  /** The positions to hand over to the next leader. Safe to call from any thread. */
  public List<ExporterPosition> outgoing() {
    return List.copyOf(outgoing.values());
  }

  /** Keeps the positions received with a TimeoutNow from the leader of {@code term}. */
  public void receive(final long term, final List<ExporterPosition> exporterPositions) {
    incoming.set(new Incoming(term, exporterPositions));
  }

  /**
   * Takes the kept positions, returning them as the periodic exporter state distribution would
   * deliver them, but only if they were sent by the leader of the term right before {@code
   * leaderTerm}. Whatever was kept is discarded either way.
   */
  Map<String, ExporterStateEntry> takeIncoming(final long leaderTerm) {
    final var taken = incoming.getAndSet(null);
    if (taken == null) {
      return Map.of();
    }
    if (taken.term() + 1 != leaderTerm) {
      LOG.debug(
          "Discarding exporter positions handed over in term {}, as this leader is in term {}",
          taken.term(),
          leaderTerm);
      return Map.of();
    }
    return taken.exporterPositions().stream()
        .collect(
            Collectors.toMap(
                ExporterPosition::exporterId,
                position ->
                    new ExporterStateEntry(
                        position.position(), new UnsafeBuffer(position.metadata())),
                (first, second) -> second));
  }

  private record Incoming(long term, List<ExporterPosition> exporterPositions) {}
}
