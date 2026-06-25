/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.membership;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.FENCED_MEMBER_EPOCH;
import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.NONE;

import io.camunda.eventbridge.clustermetadata.record.BrokerRecord;
import io.camunda.eventbridge.clustermetadata.session.BrokerLivenessMirror;
import io.camunda.eventbridge.clustermetadata.state.broker.BrokerMetadata.BrokerStatus;
import io.camunda.eventbridge.clustermetadata.state.broker.BrokerQueryService;
import io.camunda.eventbridge.clustermetadata.stream.MetadataStream;
import io.camunda.eventbridge.protocol.request.coordination.BrokerHeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.BrokerHeartbeatResponse;
import io.camunda.eventbridge.protocol.request.coordination.RegisterBrokerRequest;
import io.camunda.eventbridge.protocol.transport.CoordinationResponseEncoder;
import io.camunda.zeebe.scheduler.Actor;
import java.time.InstantSource;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves broker registration + liveness on its own actor — the broker counterpart of the
 * consumer-groups {@code HeartbeatHandler}. A {@code REGISTER_BROKER} is written to the metadata
 * stream (the {@code RegisterBrokerProcessor} assigns the epoch) and the committed reply is
 * forwarded; a {@code BROKER_HEARTBEAT} is served leader-locally: it validates the broker's epoch
 * against the replicated registry (read through its <em>own</em> {@link BrokerQueryService} on a
 * private context) and refreshes the broker's entry in the {@link BrokerLivenessMirror} that the
 * off-actor {@code BrokerEvictionTask} sweeps. A stale epoch (or an unknown/fenced broker) is told
 * to re-register.
 *
 * <p>On leader activation {@link #onActorStarted} seeds the liveness from replicated state so
 * already-registered brokers keep their registration across a failover without an eviction storm.
 */
public final class BrokerHeartbeatHandler extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(BrokerHeartbeatHandler.class);

  private final int partitionId;
  private final InstantSource clock;
  private final MetadataStream metadataStream;
  private final BrokerQueryService brokerQuery;
  private final BrokerLivenessMirror liveness;

  public BrokerHeartbeatHandler(
      final int partitionId, final InstantSource clock, final MetadataStream metadataStream) {
    this.partitionId = partitionId;
    this.clock = clock;
    this.metadataStream = metadataStream;
    brokerQuery = metadataStream.newBrokerQueryService();
    liveness = metadataStream.brokerLiveness();
  }

  @Override
  public String getName() {
    return "BrokerHeartbeatHandler-" + partitionId;
  }

  @Override
  protected void onActorStarted() {
    seed();
  }

  @Override
  protected void onActorClosing() {
    // Leadership is being given up — abandon the ephemeral liveness so the eviction task (also
    // stopping) cannot act on stale sessions; a new leader reseeds from replicated state.
    liveness.clear();
  }

  /**
   * Writes the {@code REGISTER_BROKER} command and forwards the committed reply (with the epoch).
   */
  public CompletableFuture<byte[]> handleRegister(final RegisterBrokerRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          liveness.touch(request.getBrokerId(), clock.instant());
          metadataStream
              .registerBroker(
                  new BrokerRecord()
                      .setBrokerId(request.getBrokerId())
                      .setIncarnation(request.getIncarnation()))
              .whenComplete(forward(result));
        });
    return result;
  }

  /** Serves one broker heartbeat leader-locally and returns the serialized reply. */
  public CompletableFuture<byte[]> handleHeartbeat(final BrokerHeartbeatRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          try {
            result.complete(CoordinationResponseEncoder.serialize(heartbeat(request)));
          } catch (final RuntimeException e) {
            result.completeExceptionally(e);
          }
        });
    return result;
  }

  private BrokerHeartbeatResponse heartbeat(final BrokerHeartbeatRequest request) {
    final var broker = brokerQuery.broker(request.getBrokerId());
    if (broker == null
        || broker.status() != BrokerStatus.ACTIVE
        || broker.brokerEpoch() != request.getBrokerEpoch()) {
      // Unknown, fenced, or stale epoch — the broker must (re-)register.
      return new BrokerHeartbeatResponse().setErrorCode(FENCED_MEMBER_EPOCH);
    }
    liveness.touch(request.getBrokerId(), clock.instant());
    return new BrokerHeartbeatResponse().setErrorCode(NONE);
  }

  /**
   * Rebuilds the liveness from the replicated registry replayed before this actor started, giving
   * each already-registered broker a fresh deadline so a failover does not immediately fence them.
   */
  private void seed() {
    final var now = clock.instant();
    final var brokers = brokerQuery.brokersSnapshot();
    brokers.forEach(
        (id, meta) -> {
          if (meta.status() == BrokerStatus.ACTIVE) {
            liveness.touch(id, now);
          }
        });
    if (!brokers.isEmpty()) {
      LOG.info(
          "Metadata partition {} — restored {} registered broker(s) from replicated state",
          partitionId,
          brokers.size());
    }
  }

  private static BiConsumer<byte[], Throwable> forward(final CompletableFuture<byte[]> result) {
    return (response, error) -> {
      if (error != null) {
        result.completeExceptionally(error);
      } else {
        result.complete(response);
      }
    };
  }
}
