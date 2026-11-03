/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.engine.processing.streamprocessor.ProbeStreamer;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.impl.stream.probe.ActivatedProbe;
import io.camunda.zeebe.protocol.impl.stream.probe.ProbeActivationProperties;
import io.camunda.zeebe.protocol.impl.stream.probe.ProbeActivationPropertiesImpl;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;

/**
 * A {@link ProbeStreamer} for engine tests, as {@link RecordingJobStreamer} for jobs: streams are
 * added by the test and record what is pushed to them. Of several matching streams, the first one
 * added is picked, so tests are deterministic.
 */
public final class RecordingProbeStreamer implements ProbeStreamer {

  private final Map<String, List<RecordingProbeStream>> streams = new ConcurrentHashMap<>();

  @Override
  public Optional<ProbeStream> streamFor(
      final DirectBuffer streamType, final Predicate<ProbeActivationProperties> filter) {
    return streams.getOrDefault(BufferUtil.bufferAsString(streamType), List.of()).stream()
        .filter(stream -> filter.test(stream.properties()))
        .findFirst()
        .map(ProbeStream.class::cast);
  }

  /** Adds a stream declaring one probe ID. */
  public RecordingProbeStream addStream(
      final String probeId, final String worker, final List<String> tenantIds) {
    return addStream(List.of(probeId), worker, tenantIds);
  }

  /** Adds one stream declaring every given probe ID, as a worker with several handlers does. */
  public RecordingProbeStream addStream(
      final List<String> probeIds, final String worker, final List<String> tenantIds) {
    final var properties =
        new ProbeActivationPropertiesImpl()
            .setWorker(worker)
            .setProbeIds(probeIds)
            .setTenantIds(tenantIds);
    final var stream = new RecordingProbeStream(properties);
    streams
        .computeIfAbsent(ProbeActivationProperties.STREAM_TYPE, key -> new CopyOnWriteArrayList<>())
        .add(stream);
    return stream;
  }

  public void clearStreams() {
    streams.clear();
  }

  public static final class RecordingProbeStream implements ProbeStream {

    private final ProbeActivationProperties properties;
    private final List<PushedProbe> pushedProbes = new CopyOnWriteArrayList<>();

    private RecordingProbeStream(final ProbeActivationProperties properties) {
      this.properties = properties;
    }

    @Override
    public ProbeActivationProperties properties() {
      return properties;
    }

    @Override
    public void push(final ActivatedProbe payload) {
      // copy the record: the engine may reuse the instance it pushed
      pushedProbes.add(new PushedProbe(payload.probeKey(), payload.probeRecord().copy()));
    }

    public List<PushedProbe> getPushedProbes() {
      return pushedProbes;
    }
  }

  public record PushedProbe(long probeKey, ProbeRecord record) {}
}
