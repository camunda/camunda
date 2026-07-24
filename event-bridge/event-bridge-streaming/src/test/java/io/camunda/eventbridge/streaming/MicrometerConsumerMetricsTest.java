/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.protobuf.Parser;
import io.camunda.eventbridge.api.proto.ConsumerHeartbeatResponse;
import io.camunda.eventbridge.api.proto.JoinResponse;
import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.OffsetResetPolicy;
import io.camunda.eventbridge.client.Partitioner;
import io.camunda.eventbridge.client.internal.ClientConfig;
import io.camunda.eventbridge.client.internal.consumer.ConsumerImpl;
import io.camunda.eventbridge.client.internal.transport.HttpTransport;
import io.camunda.eventbridge.client.internal.transport.HttpTransport.BinaryResponse;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * End-to-end through the PUBLIC {@link Consumer} API: a real {@link ConsumerImpl} (which owns a
 * real {@code GroupCoordinator}) wired to a real {@link MicrometerConsumerMetrics} over a {@link
 * SimpleMeterRegistry} via {@link Consumer#metrics}. The four meters read back exactly as the
 * client-side callbacks fire, and {@code eb.consumer.assignment.epoch} is the observable,
 * end-to-end proof of the static-membership takeover spec's invariant 1 (strict epoch increase):
 * after a takeover (modeled here as a heartbeat's full reconciliation carrying a higher epoch —
 * exactly how this client observes a coordinator-side takeover of its own static instance id), the
 * gauge reads the INCREASED epoch.
 */
final class MicrometerConsumerMetricsTest {

  private static final long HEARTBEAT_INTERVAL_MS = 60_000L;
  private static final String GROUP = "g1";

  private HttpTransport transport;
  private ScheduledThreadPoolExecutor executor;
  private SimpleMeterRegistry registry;

  @BeforeEach
  void setUp() {
    transport = Mockito.mock(HttpTransport.class);
    executor = new ScheduledThreadPoolExecutor(1);
    when(transport.parse(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              final Parser<?> parser = invocation.getArgument(1);
              return parser.parseFrom((byte[]) invocation.getArgument(0));
            });
    registry = new SimpleMeterRegistry();
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void shouldTrackTheIncreasedEpochAfterATakeover() {
    // given — a consumer joined at epoch 1, metrics configured before the first heartbeat
    final Consumer consumer = newConsumer();
    when(transport.postProtobufRaw(contains("/members"), any(), eq("join")))
        .thenReturn(
            CompletableFuture.completedFuture(new BinaryResponse(201, joinResponse("m1", 1))));
    consumer.metrics(new MicrometerConsumerMetrics(registry, GROUP));
    consumer.joinGroup().join();
    assertThat(epochGauge().value()).isEqualTo(1.0);

    // when — a heartbeat's full reconciliation reports a higher epoch: from this client's
    // perspective this is exactly how it observes a coordinator-side static-membership takeover of
    // its own static instance id
    when(transport.postProtobufRaw(contains("/heartbeat"), any(), eq("heartbeat")))
        .thenReturn(
            CompletableFuture.completedFuture(new BinaryResponse(200, heartbeatResponse(2))));
    consumer.sendHeartbeat().join();

    // then — the gauge reads the increased epoch, the observable end-to-end proof of invariant 1
    assertThat(epochGauge().value()).isEqualTo(2.0);
  }

  @Test
  void shouldNotIncrementRebalancesOnAStaticRestartsJoinAlone() {
    // given — metrics configured before the first join
    final Consumer consumer = newConsumer();
    consumer.metrics(new MicrometerConsumerMetrics(registry, GROUP));
    when(transport.postProtobufRaw(contains("/members"), any(), eq("join")))
        .thenReturn(
            CompletableFuture.completedFuture(new BinaryResponse(201, joinResponse("m1", 5))));

    // when — the join itself completes (a restart — fresh or a coordinator-side takeover of an
    // existing static instance id — looks identical here: REBALANCE_IN_PROGRESS, no assignment)
    consumer.joinGroup().join();

    // then — no rebalance is observed yet: a JOIN_GROUP reply never carries an assignment (learned
    // only via the next heartbeat, see JoinGroupProcessor's javadoc), so a restart/takeover's join
    // must not itself count as a rebalance
    assertThat(counter("eb.consumer.rebalances")).isZero();
  }

  private Consumer newConsumer() {
    return new ConsumerImpl(
        (topic, partition, offset, maxBytes, minBytes, maxWaitMs) ->
            CompletableFuture.completedFuture(null),
        transport,
        executor,
        clientConfig(),
        GROUP,
        List.of("t1"),
        "instance-a");
  }

  private static ClientConfig clientConfig() {
    return new ClientConfig(
        "http://localhost",
        OffsetResetPolicy.EARLIEST,
        1,
        5_000L,
        1 << 20,
        0,
        1,
        64L << 20,
        32L << 20,
        HEARTBEAT_INTERVAL_MS,
        Partitioner.defaultHash());
  }

  private Gauge epochGauge() {
    return registry.get("eb.consumer.assignment.epoch").tag("group", GROUP).gauge();
  }

  private double counter(final String name) {
    return registry.get(name).tag("group", GROUP).counter().count();
  }

  private static byte[] joinResponse(final String memberId, final long epoch) {
    return JoinResponse.newBuilder()
        .setMemberId(memberId)
        .setMemberEpoch(epoch)
        .build()
        .toByteArray();
  }

  private static byte[] heartbeatResponse(final long epoch) {
    return ConsumerHeartbeatResponse.newBuilder().setMemberEpoch(epoch).build().toByteArray();
  }
}
