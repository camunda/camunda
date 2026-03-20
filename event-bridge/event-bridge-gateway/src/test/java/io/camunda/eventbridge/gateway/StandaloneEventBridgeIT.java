/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.core.EventData;
import io.camunda.eventbridge.core.EventDataBatch;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * End-to-end integration test for the standalone Event Bridge component.
 *
 * <p>Starts the full Event Bridge application in a single JVM — HTTP gateway (Spring MVC/Tomcat)
 * plus RAFT broker (ActorScheduler + AtomixCluster) — using {@link SpringBootTest} with a random
 * HTTP port and an isolated RAFT cluster port. All tests share the same Spring context and run in
 * {@link Order} to avoid state interference.
 *
 * <p>The test sequence covers the primary operational lifecycle:
 *
 * <ol>
 *   <li>Cluster start: wait for RAFT to elect a leader for partition 0.
 *   <li>Publish: write a batch of binary events and verify the assigned log positions.
 *   <li>Auto-register: register a consumer via repeated heartbeats (no explicit subscribe call) and
 *       verify that partition 0 is assigned via a {@code fullAssignment} response.
 *   <li>Poll: consume published events and verify payload fidelity.
 *   <li>Commit: record consumed offsets (idempotent) without error.
 *   <li>Heartbeat stability: send a heartbeat from a fully-assigned consumer and verify that the
 *       epoch and owned partitions are unchanged.
 * </ol>
 */
@SpringBootTest(
    classes = EventBridgeTestApplication.class,
    webEnvironment = WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StandaloneEventBridgeIT {

  /** Isolated, temporary data directory for RAFT partition storage. Shared across all tests. */
  @TempDir static Path dataDir;

  /** Injected HTTP port assigned by the embedded Tomcat server. */
  @LocalServerPort private int port;

  // -------------------------------------------------------------------------
  // Dynamic properties: allocate ports before the Spring context starts
  // -------------------------------------------------------------------------

  /**
   * Assigns a random free port for the Atomix/RAFT cluster transport so that parallel test-suite
   * runs never collide, and configures the isolated data directory.
   */
  @DynamicPropertySource
  static void configure(final DynamicPropertyRegistry registry) throws IOException {
    final int clusterPort;
    try (final var socket = new ServerSocket(0)) {
      clusterPort = socket.getLocalPort();
    }
    registry.add("event-bridge.cluster.bind-port", () -> clusterPort);
    registry.add("event-bridge.cluster.advertised-port", () -> clusterPort);
    registry.add("event-bridge.data.directory", () -> dataDir.toAbsolutePath().toString());
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private EventBridgeClient newClient() {
    return EventBridgeClient.create("http://localhost:" + port);
  }

  /**
   * Creates a {@link Consumer} and repeatedly sends heartbeats until the coordinator assigns at
   * least one partition, then returns the consumer.
   *
   * <p>Under the new model, {@link EventBridgeClient#subscribe(String, String)} is a local factory
   * call — no HTTP request is made. The consumer auto-registers with the coordinator on its first
   * {@link Consumer#sendHeartbeat()} call. The coordinator defers rebalance to its periodic loop
   * ({@code rebalanceIntervalMs = 2000 ms by default}), so the partition assignment arrives via a
   * {@code fullAssignment} field on a subsequent heartbeat response (when the coordinator's epoch
   * has advanced past the client's last-seen epoch).
   *
   * <p>Transient errors from {@code sendHeartbeat()} (e.g. a 503 before the coordinator is fully
   * ready) are swallowed and retried by Awaitility; the overall timeout is 15 seconds.
   */
  private Consumer registerConsumerAndAwaitAssignment(
      final EventBridgeClient client, final String groupId, final String consumerId) {
    final Consumer consumer;
    try {
      consumer = client.subscribe(groupId, consumerId).get(10, TimeUnit.SECONDS);
    } catch (final Exception e) {
      throw new AssertionError("Failed to create consumer handle: " + e.getMessage(), e);
    }

    await("consumer '" + consumerId + "' in group '" + groupId + "' receives partition assignment")
        .timeout(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(500))
        .until(
            () -> {
              try {
                consumer.sendHeartbeat().get(5, TimeUnit.SECONDS);
              } catch (final Exception ignored) {
                // transient coordinator errors are retried
              }
              return !consumer.getOwnedPartitions().isEmpty();
            });

    return consumer;
  }

  // -------------------------------------------------------------------------
  // Test 1 – Cluster start
  // -------------------------------------------------------------------------

  /**
   * Verifies that the Event Bridge application starts up successfully and RAFT elects a leader for
   * partition 0 within 30 seconds.
   *
   * <p>Uses {@code GET /v1/partitions/0/latest-position} as a readiness probe: the endpoint throws
   * an {@link io.camunda.eventbridge.client.EventBridgeException} while the broker is not yet the
   * RAFT leader and returns normally once it is.
   */
  @Test
  @Order(1)
  void shouldStartAndElectLeader() {
    // given
    final var client = newClient();

    // then — keep trying until the partition leader is available
    await("RAFT leader elected for partition 0")
        .timeout(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> assertThatCode(() -> client.getLatestPosition(0)).doesNotThrowAnyException());
  }

  // -------------------------------------------------------------------------
  // Test 2 – Publish
  // -------------------------------------------------------------------------

  /**
   * Verifies that a batch of binary events is accepted by the HTTP API and that sequential, unique
   * log positions are returned for each event in the batch.
   */
  @Test
  @Order(2)
  void shouldPublishBatchAndReturnSequentialPositions() throws Exception {
    // given
    final var client = newClient();
    final List<byte[]> events =
        List.of(
            "hello".getBytes(StandardCharsets.UTF_8),
            "world".getBytes(StandardCharsets.UTF_8),
            "event-bridge".getBytes(StandardCharsets.UTF_8));

    final var batch = EventDataBatch.create(10 * 1024 * 1024, 1024 * 1024, 1000);
    for (final byte[] event : events) {
      batch.tryAdd(new EventData(event));
    }

    // when
    final List<Long> positions = client.publishBatch(0, batch).get(10, TimeUnit.SECONDS);

    // then
    assertThat(positions).hasSize(3).doesNotContainNull();
    assertThat(positions.get(0)).isPositive();
    assertThat(positions.get(1)).isGreaterThan(positions.get(0));
    assertThat(positions.get(2)).isGreaterThan(positions.get(1));
  }

  // -------------------------------------------------------------------------
  // Test 3 – Auto-register via heartbeat
  // -------------------------------------------------------------------------

  /**
   * Verifies that a consumer auto-registers with the coordinator on its first heartbeat and
   * eventually receives partition 0 via a {@code fullAssignment} response.
   *
   * <p>Under the new model there is no explicit subscribe call. The consumer handle is created
   * locally by {@link EventBridgeClient#subscribe(String, String)} (which makes no HTTP request)
   * and becomes known to the coordinator only when {@link Consumer#sendHeartbeat()} is called. The
   * coordinator defers the actual rebalance to its periodic loop ({@code rebalanceIntervalMs} = 2 s
   * by default), so the assignment arrives on a heartbeat response after that loop has run.
   *
   * <p>Sequence:
   *
   * <ol>
   *   <li>First heartbeat (client epoch = 0): coordinator returns epoch = 1 with empty {@code
   *       fullAssignment} (no rebalance has run yet).
   *   <li>Coordinator loop fires, runs BALANCED_STICKY rebalance, epoch → 2, assigns partition 0.
   *   <li>Subsequent heartbeat (client epoch = 1 &lt; coordinator epoch = 2): coordinator returns
   *       {@code fullAssignment = [0]}; client updates {@code ownedPartitions} and sends an ACK.
   * </ol>
   */
  @Test
  @Order(3)
  void shouldAutoRegisterConsumerAndReceivePartitionAssignmentViaHeartbeat() {
    // given / when
    final var client = newClient();
    final Consumer consumer =
        registerConsumerAndAwaitAssignment(client, "grp-register-it", "consumer-1");

    // then
    assertThat(consumer.getOwnedPartitions())
        .as("single-partition broker must assign partition 0")
        .containsExactly(0);
    assertThat(consumer.getCurrentEpoch())
        .as("epoch must be >= 1 after coordinator runs first rebalance")
        .isGreaterThanOrEqualTo(1L);
  }

  // -------------------------------------------------------------------------
  // Test 4 – Poll
  // -------------------------------------------------------------------------

  /**
   * Verifies that events published to a partition can be retrieved via the consumer poll API with
   * correct payload content and log positions.
   *
   * <p>The consumer is registered via repeated heartbeats before polling. The consumer's {@code
   * nextPosition} starts at {@code -1}, which resolves to the oldest available log position, so
   * events from all prior tests are also returned — the assertion checks only that the two events
   * published in this test are present with correct payloads.
   */
  @Test
  @Order(4)
  void shouldPollAndReceivePublishedEvents() throws Exception {
    // given
    final var client = newClient();
    final byte[] payload1 = "poll-event-one".getBytes(StandardCharsets.UTF_8);
    final byte[] payload2 = "poll-event-two".getBytes(StandardCharsets.UTF_8);

    // Publish before registering so events are already in the log when poll is called.
    final var batchToPublish = EventDataBatch.create(10 * 1024 * 1024, 1024 * 1024, 1000);
    batchToPublish.tryAdd(new EventData(payload1));
    batchToPublish.tryAdd(new EventData(payload2));
    final List<Long> positions = client.publishBatch(0, batchToPublish).get(10, TimeUnit.SECONDS);
    assertThat(positions).hasSize(2);
    final long pos1 = positions.get(0);
    final long pos2 = positions.get(1);

    // Register consumer via heartbeat; wait for partition assignment before polling.
    final Consumer consumer =
        registerConsumerAndAwaitAssignment(client, "grp-poll-it", "consumer-1");

    // when — drain the log in batches until both target events are found.
    // Each poll() advances the consumer's internal nextPosition so repeated calls
    // scan forward through the log without revisiting already-seen records.
    final List<Event> allPolled = new ArrayList<>();
    await("both published events visible via poll")
        .timeout(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              // serverWaitMs=0: return immediately; no long-poll parking
              allPolled.addAll(consumer.poll(1000, Duration.ZERO));
              final var receivedPositions =
                  allPolled.stream().map(Event::position).collect(Collectors.toSet());
              return receivedPositions.contains(pos1) && receivedPositions.contains(pos2);
            });

    // then — verify payload fidelity for both events
    final Map<Long, byte[]> payloadByPosition =
        allPolled.stream().collect(Collectors.toMap(Event::position, Event::payload));
    assertThat(payloadByPosition.get(pos1)).isEqualTo(payload1);
    assertThat(payloadByPosition.get(pos2)).isEqualTo(payload2);
  }

  // -------------------------------------------------------------------------
  // Test 5 – Commit offset
  // -------------------------------------------------------------------------

  /**
   * Verifies that committed offsets are accepted and that re-committing the same position is
   * idempotent (no exception thrown on the second call).
   *
   * <p>The consumer is registered via repeated heartbeats before polling or committing.
   */
  @Test
  @Order(5)
  void shouldCommitOffsetIdempotently() throws Exception {
    // given
    final var client = newClient();
    final var commitBatch = EventDataBatch.create(10 * 1024 * 1024, 1024 * 1024, 1000);
    commitBatch.tryAdd(new EventData("commit-event".getBytes(StandardCharsets.UTF_8)));
    final List<Long> positions = client.publishBatch(0, commitBatch).get(10, TimeUnit.SECONDS);
    final long position = positions.get(0);

    final Consumer consumer =
        registerConsumerAndAwaitAssignment(client, "grp-commit-it", "consumer-1");

    // Poll until the commit-event is visible so nextPosition has advanced past it.
    await("commit-event visible via poll")
        .timeout(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                consumer.poll(1000, Duration.ZERO).stream()
                    .anyMatch(e -> e.position() == position));

    // when — first commit
    consumer.commitOffset(0, position).get(10, TimeUnit.SECONDS);

    // then — second commit at the same position is idempotent (must not throw)
    assertThatCode(() -> consumer.commitOffset(0, position).get(10, TimeUnit.SECONDS))
        .doesNotThrowAnyException();
  }

  // -------------------------------------------------------------------------
  // Test 6 – Heartbeat stability
  // -------------------------------------------------------------------------

  /**
   * Verifies that a consumer in a stable, fully-assigned state receives an empty delta on a
   * subsequent heartbeat and that the epoch and owned partitions are unchanged.
   *
   * <p>This exercises the {@code clientEpoch == coordinatorEpoch} delta path: once the consumer has
   * ACKed its current assignment and no rebalance has occurred, the coordinator returns {@code
   * revoke=[], assign=[], fullAssignment=[]} and the client's local state is unmodified.
   */
  @Test
  @Order(6)
  void shouldReceiveEmptyDeltaOnHeartbeatInStableState() throws Exception {
    // given — register and await initial partition assignment
    final var client = newClient();
    final Consumer consumer =
        registerConsumerAndAwaitAssignment(client, "grp-heartbeat-it", "consumer-1");
    final long epochAfterAssignment = consumer.getCurrentEpoch();
    final List<Integer> partitionsAfterAssignment = List.copyOf(consumer.getOwnedPartitions());

    // when — send one more heartbeat from a stable state
    consumer.sendHeartbeat().get(10, TimeUnit.SECONDS);

    // then — epoch unchanged (no rebalance occurred), partitions unchanged
    assertThat(consumer.getCurrentEpoch())
        .as("epoch must not change when no rebalance occurred")
        .isEqualTo(epochAfterAssignment);
    assertThat(consumer.getOwnedPartitions())
        .as("owned partitions must not change in stable state")
        .isEqualTo(partitionsAfterAssignment);
  }
}
