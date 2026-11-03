/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.stream;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedProbe;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.stream.probe.ActivatedProbeImpl;
import io.camunda.zeebe.protocol.impl.stream.probe.ProbeActivationProperties;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamBlockedException;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens the probe stream of a worker, as {@link StreamJobsHandler} does for one job type. A worker
 * holds one stream for every probe it answers; the stream declares their IDs in its metadata and
 * the engine matches each probe against that list.
 */
public final class StreamProbesHandler extends Actor {

  private static final Logger LOGGER = LoggerFactory.getLogger(StreamProbesHandler.class);

  private final ClientStreamer<ProbeActivationProperties> probeStreamer;

  public StreamProbesHandler(final ClientStreamer<ProbeActivationProperties> probeStreamer) {
    this.probeStreamer = probeStreamer;
  }

  public void handle(
      final List<String> probeIds,
      final ProbeActivationProperties properties,
      final ServerCallStreamObserver<ActivatedProbe> responseObserver,
      final String physicalTenantId) {
    final String invalid = validate(probeIds);
    if (invalid != null) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription(invalid).asRuntimeException());
      return;
    }

    final var streamType = wrapString(ProbeActivationProperties.STREAM_TYPE);
    final var consumer = new ProbeStreamConsumer(responseObserver, actor, physicalTenantId);
    final var remover = new AsyncProbeStreamRemover(probeStreamer, actor);
    responseObserver.setOnCloseHandler(remover);
    responseObserver.setOnCancelHandler(remover);
    actor.run(
        () ->
            actor.runOnCompletion(
                probeStreamer.add(streamType, properties, consumer, physicalTenantId),
                (streamId, error) -> {
                  if (error != null) {
                    LOGGER.warn("Failed to register new probe stream", error);
                    responseObserver.onError(
                        Status.UNAVAILABLE
                            .withDescription("Failed to register new probe stream")
                            .withCause(error)
                            .asRuntimeException());
                    return;
                  }
                  remover.streamId(streamId);
                }));
  }

  private static @Nullable String validate(final List<String> probeIds) {
    if (probeIds.isEmpty()) {
      return "Expected to stream probes for at least one probe ID, but none was given";
    }
    final Set<String> seen = new HashSet<>();
    for (final String probeId : probeIds) {
      if (probeId.isBlank()) {
        return "Expected to stream probes for non-blank probe IDs, but one was blank";
      }
      if (!seen.add(probeId)) {
        return "Expected each probe ID once, but '%s' was given twice".formatted(probeId);
      }
    }
    return null;
  }

  private static final class ProbeStreamConsumer implements ClientStreamConsumer {

    private final ServerCallStreamObserver<ActivatedProbe> responseObserver;
    private final ConcurrencyControl executor;
    private final String physicalTenantId;

    private ProbeStreamConsumer(
        final ServerCallStreamObserver<ActivatedProbe> responseObserver,
        final ConcurrencyControl executor,
        final String physicalTenantId) {
      this.responseObserver = responseObserver;
      this.executor = executor;
      this.physicalTenantId = physicalTenantId;
    }

    @Override
    public ActorFuture<Void> push(final DirectBuffer payload) {
      final var result = new CompletableActorFuture<Void>();
      try {
        executor.run(() -> handlePushedProbe(payload, result));
      } catch (final Exception e) {
        responseObserver.onError(e);
        result.completeExceptionally(e);
      }
      return result;
    }

    private void handlePushedProbe(
        final DirectBuffer payload, final CompletableActorFuture<Void> result) {
      if (!responseObserver.isReady()) {
        // a failed push makes the broker yield the probe, which re-dispatches it once
        result.completeExceptionally(
            new ClientStreamBlockedException(
                "Expected to push payload (size = '%d') to stream, but stream is blocked"
                    .formatted(payload.capacity())));
        return;
      }

      final ActivatedProbe activatedProbe;
      try {
        final var deserialized = new ActivatedProbeImpl();
        deserialized.wrap(payload);
        final var record = deserialized.probeRecord();
        activatedProbe =
            ActivatedProbe.newBuilder()
                .setKey(deserialized.probeKey())
                .setProbeId(record.getProbeId())
                .setTenantId(record.getTenantId())
                .setInput(MsgPackConverter.convertToJson(record.getInputBuffer()))
                .setDeadline(record.getDeadline())
                .setWorker(record.getWorker())
                .setPhysicalTenantId(physicalTenantId)
                .build();
      } catch (final Exception e) {
        result.completeExceptionally(e);
        return;
      }

      try {
        responseObserver.onNext(activatedProbe);
        result.complete(null);
      } catch (final Exception e) {
        responseObserver.onError(e);
        result.completeExceptionally(e);
      }
    }
  }

  /** As {@code StreamJobsHandler.AsyncJobStreamRemover}: copes with close before registration. */
  private static final class AsyncProbeStreamRemover implements Runnable {

    private final ClientStreamer<ProbeActivationProperties> probeStreamer;
    private final ConcurrencyControl executor;
    private boolean isRemoved;
    private ClientStreamId streamId;

    private AsyncProbeStreamRemover(
        final ClientStreamer<ProbeActivationProperties> probeStreamer,
        final ConcurrencyControl executor) {
      this.probeStreamer = probeStreamer;
      this.executor = executor;
    }

    @Override
    public void run() {
      executor.run(
          () -> {
            isRemoved = true;
            if (streamId != null) {
              probeStreamer.remove(streamId);
            }
          });
    }

    private void streamId(final ClientStreamId streamId) {
      if (isRemoved) {
        probeStreamer.remove(streamId);
        return;
      }
      this.streamId = streamId;
    }
  }
}
