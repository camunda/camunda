/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService.TenantRestart;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipHandover.Outcome;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipHandover.PartitionReport;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipReclaim;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipReclaim.PartitionReclaim;
import io.camunda.zeebe.broker.partitioning.PartitionRestartSnapshots;
import io.camunda.zeebe.broker.partitioning.PartitionRestartSnapshots.PartitionSnapshot;
import io.camunda.zeebe.broker.system.configuration.RaftCfg.TenantRestartHandover;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.restapi.ClusterRebalanceRestClient;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Measures how long a physical tenant is interrupted while a configuration change restarts its
 * module on one member after the other, for every way of dealing with the leadership of the
 * member's partitions: handed over and taken back, handed over and left, handed over and rebalanced
 * after the rollout, and not handed over; the first two with and without a snapshot before the
 * restart. Three brokers, replication factor 3, a tenant with three partitions, and continuous load
 * on every partition through the gateway of every broker.
 */
final class PhysicalTenantRestartHandoverIT {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantRestartHandoverIT.class);

  private static final String OTHER_TENANT = "othertenant";
  private static final int PARTITIONS = 3;
  private static final int MEASURED_ROLLOUTS = 4;
  private static final Duration TIMEOUT = Duration.ofMinutes(2);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration SETTLE = Duration.ofSeconds(2);
  private static final Duration LOAD_INTERVAL = Duration.ofMillis(10);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Duration ELECTION_TIMEOUT = Duration.ofMillis(2500);
  private static final Map<String, Integer> ONE_EACH = Map.of("0", 1, "1", 1, "2", 1);

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(OTHER_TENANT, Storage.none())
          .build();

  @Test
  void shouldKeepARestartedTenantLedThroughEveryVariant() {
    // given
    final var arms =
        List.of(
            new Arm(
                "transfer back with snapshot", TenantRestartHandover.TRANSFER_BACK, true, false),
            new Arm("transfer back", TenantRestartHandover.TRANSFER_BACK, false, false),
            new Arm(
                "transfer away with snapshot", TenantRestartHandover.TRANSFER_AWAY, true, false),
            new Arm("transfer away", TenantRestartHandover.TRANSFER_AWAY, false, false),
            new Arm(
                "transfer away, rebalance after", TenantRestartHandover.TRANSFER_AWAY, false, true),
            new Arm("no handover", TenantRestartHandover.NONE, false, false));

    // when
    arms.forEach(Arm::run);

    // then
    arms.forEach(Arm::log);
    logComparison(arms);
    final var withoutHandover = arms.getLast();
    for (final var arm : arms) {
      assertThat(arm.leaderRestarts()).as(arm.name).hasSizeGreaterThanOrEqualTo(MEASURED_ROLLOUTS);
      assertThat(arm.leaderlessAfterRollouts).as(arm.name).isEmpty();
      assertThat(arm.otherTenantFailed).as(arm.name).isZero();
      if (arm == withoutHandover) {
        continue;
      }
      assertThat(median(arm.leaderRestarts(), Restart::unavailableMillis))
          .as(arm.name)
          .isLessThan(median(withoutHandover.leaderRestarts(), Restart::unavailableMillis));
      assertThat(arm.handovers())
          .as(arm.name)
          .filteredOn(PartitionReport::led)
          .isNotEmpty()
          .allSatisfy(handover -> assertThat(handover.outcome()).isEqualTo(Outcome.TRANSFERRED));
      assertThat(worst(arm.leaderRestarts(), Restart::leaderlessMillis))
          .as("%s: longest time a partition had no leader", arm.name)
          .isLessThan(ELECTION_TIMEOUT.toMillis());
    }
    for (final var arm : arms) {
      if (arm.handover == TenantRestartHandover.TRANSFER_BACK) {
        assertThat(arm.reclaims())
            .as(arm.name)
            .isNotEmpty()
            .allSatisfy(
                reclaim ->
                    assertThat(reclaim.outcome())
                        .isEqualTo(PartitionLeadershipReclaim.Outcome.RECLAIMED));
        assertThat(arm.leadersAfterRollouts).as(arm.name).allMatch(ONE_EACH::equals);
      }
      if (arm.snapshot) {
        assertThat(arm.snapshots())
            .as(arm.name)
            .extracting(PartitionSnapshot::outcome)
            .contains(PartitionRestartSnapshots.Outcome.TAKEN);
      }
      if (arm.rebalanceAfter) {
        assertThat(arm.leadersAfterRebalances).as(arm.name).allMatch(ONE_EACH::equals);
      }
    }
  }

  private static void logComparison(final List<Arm> arms) {
    final var report = new StringBuilder(System.lineSeparator());
    report.append("variant comparison").append(System.lineSeparator());
    report
        .append(
            "variant | leader restart unavailable median/worst ms | max latency median/worst ms"
                + " | failed per restart median/worst | timed out | leaderless worst ms"
                + " | rollout unavailable (sum over restarts and rebalance) median/worst ms"
                + " | rollout duration median ms | leaders per member after rollouts"
                + " | rebalance unavailable worst ms")
        .append(System.lineSeparator());
    for (final var arm : arms) {
      final var leaders = arm.leaderRestarts();
      final var totals = arm.rolloutTotals();
      report.append(
          "%s | %d/%d | %d/%d | %d/%d | %d | %d | %d/%d | %d | %s | %s%n"
              .formatted(
                  arm.name,
                  median(leaders, Restart::unavailableMillis),
                  worst(leaders, Restart::unavailableMillis),
                  median(leaders, Restart::maxLatencyMillis),
                  worst(leaders, Restart::maxLatencyMillis),
                  median(leaders, Restart::failed),
                  worst(leaders, Restart::failed),
                  leaders.stream().mapToLong(Restart::timedOut).sum(),
                  worst(leaders, Restart::leaderlessMillis),
                  medianOf(totals),
                  totals.stream().mapToLong(Long::longValue).max().orElse(0),
                  medianOf(arm.rolloutMillis),
                  arm.leadersAfterRollouts,
                  arm.rebalances.isEmpty()
                      ? "-"
                      : arm.rebalances.stream()
                              .mapToLong(Rebalance::unavailableMillis)
                              .max()
                              .orElse(0)
                          + " (leaders after: "
                          + arm.leadersAfterRebalances
                          + ")"));
    }
    report
        .append(System.lineSeparator())
        .append(
            "replay after a restart, per partition: variant, log positions since the snapshot,"
                + " installed after ms, caught up after ms, outcome")
        .append(System.lineSeparator());
    for (final var arm : arms) {
      for (final var replay : arm.afterRestarts()) {
        report.append(
            "replay | %s | %d | %d | %d | %s%n"
                .formatted(
                    arm.name,
                    replay.logSinceSnapshot(),
                    replay.installedAfter().toMillis(),
                    replay.caughtUpAfter().toMillis(),
                    replay.outcome()));
      }
    }
    report
        .append(System.lineSeparator())
        .append(
            "snapshots before a restart, per partition: variant, outcome, took ms, bytes, failure")
        .append(System.lineSeparator());
    for (final var arm : arms) {
      for (final var snapshot : arm.snapshots()) {
        report.append(
            "snapshot | %s | %s | %d | %d | %s%n"
                .formatted(
                    arm.name,
                    snapshot.outcome(),
                    snapshot.took().toMillis(),
                    snapshot.snapshot() == null ? -1 : snapshot.snapshot().sizeInBytes(),
                    snapshot.failure()));
      }
    }
    LOG.info("{}", report);
  }

  private static long medianOf(final List<Long> values) {
    if (values.isEmpty()) {
      return 0;
    }
    final var sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
    return sorted[sorted.length / 2];
  }

  private static long median(final List<Restart> restarts, final ToLongFunction<Restart> metric) {
    final var sorted = restarts.stream().mapToLong(metric).sorted().toArray();
    return sorted[sorted.length / 2];
  }

  private static long worst(final List<Restart> restarts, final ToLongFunction<Restart> metric) {
    return restarts.stream().mapToLong(metric).max().orElse(0);
  }

  private static Map<String, String> tenantConfiguration(
      final TestCluster cluster, final String snapshotPeriod) {
    final Map<String, String> configuration = new HashMap<>();
    configuration.put("name", "Restarted");
    configuration.put("cluster.partition-count", String.valueOf(PARTITIONS));
    configuration.put("data.secondary-storage.type", "none");
    configuration.put("data.snapshot-period", snapshotPeriod);
    configuration.put("security.initialization.default-roles.admin.users[0]", "admin");
    if (broker(cluster, 0)
        .unifiedConfig()
        .getData()
        .getExporters()
        .containsKey(TestStandaloneBroker.RECORDING_EXPORTER_ID)) {
      configuration.put("data.exporters-assigned[0]", TestStandaloneBroker.RECORDING_EXPORTER_ID);
    }
    return configuration;
  }

  private static TestStandaloneBroker broker(final TestCluster cluster, final int nodeId) {
    return cluster.brokers().get(MemberId.from(String.valueOf(nodeId)));
  }

  private static Collection<RaftPartition> raftPartitions(
      final TestStandaloneBroker broker, final String tenantId) {
    final var context = broker.bean(Broker.class).getBrokerContext();
    if (context == null) {
      return List.of();
    }
    final var manager = context.getPartitionManagers().get(tenantId);
    return manager == null ? List.of() : manager.getRaftPartitions();
  }

  private static HttpResponse<String> send(
      final TestStandaloneBroker broker,
      final String method,
      final String tenantId,
      final Map<String, String> body) {
    try (final var http = HttpClient.newHttpClient()) {
      final var request =
          HttpRequest.newBuilder(broker.actuatorUri("physicalTenants", tenantId))
              .header("Content-Type", "application/json")
              .method(
                  method,
                  body == null
                      ? BodyPublishers.noBody()
                      : BodyPublishers.ofString(JSON.writeValueAsString(body)))
              .build();
      return http.send(request, BodyHandlers.ofString());
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static JsonNode status(final TestStandaloneBroker broker, final String tenantId) {
    try {
      return JSON.readTree(send(broker, "GET", tenantId, null).body());
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void awaitRolledOut(
      final TestStandaloneBroker broker, final String tenantId, final long version) {
    Awaitility.await("version %d of %s is applied by every broker".formatted(version, tenantId))
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              final var status = status(broker, tenantId);
              assertThat(status.get("version").asLong()).isEqualTo(version);
              assertThat(status.get("rolledOut").asBoolean()).isTrue();
              assertThat(status.get("changing").asBoolean()).isFalse();
            });
  }

  /** The restart of every member in one rollout, ordered by start. */
  private record Rollout(
      int number, List<Map.Entry<MemberId, TenantRestart>> restarts, long rolledOutNanos) {}

  /** One request of the load: when it started and ended, and whether it was accepted. */
  private record Sample(long startNanos, long endNanos, boolean ok, String failure) {}

  /**
   * The interruption one member restart caused, as seen through the gateways of the other members
   * (the restarting member's own gateway stops serving the tenant during its restart either way).
   * Unavailable is the longest time a partition accepted no request through any of them; away and
   * back split it at the moment the member's module started again, so back holds the transfer back.
   * Timed out counts the requests that waited for the client's whole timeout.
   */
  private record Restart(
      int rollout,
      MemberId member,
      boolean led,
      List<PartitionReport> handover,
      long unavailableMillis,
      long maxLatencyMillis,
      long failed,
      long timedOut,
      long leaderlessMillis,
      long ownGatewayUnavailableMillis,
      long ownGatewayFailed,
      long awayUnavailableMillis,
      long backUnavailableMillis,
      List<PartitionSnapshot> snapshots,
      List<PartitionReclaim> afterRestart) {}

  /** The interruption a rebalance after a rollout caused. */
  private record Rebalance(
      int rollout, long tookMillis, long unavailableMillis, long maxLatencyMillis, long failed) {}

  /**
   * Publishes messages to one partition of a tenant through one gateway: one request at a time, a
   * new one on the next tick of the load's schedule once the previous one completed.
   */
  private static final class Load implements Runnable {
    private final CamundaClient client;
    private final MemberId gateway;
    private final String correlationKey;
    private final AtomicBoolean running;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final List<Sample> samples = Collections.synchronizedList(new ArrayList<>());
    private volatile long lastSuccessNanos;

    private Load(
        final CamundaClient client,
        final MemberId gateway,
        final String correlationKey,
        final AtomicBoolean running) {
      this.client = client;
      this.gateway = gateway;
      this.correlationKey = correlationKey;
      this.running = running;
    }

    @Override
    public void run() {
      if (!running.get() || !inFlight.compareAndSet(false, true)) {
        return;
      }
      final var start = System.nanoTime();
      try {
        client
            .newPublishMessageCommand()
            .messageName("handover")
            .correlationKey(correlationKey)
            .timeToLive(Duration.ZERO)
            .requestTimeout(REQUEST_TIMEOUT)
            .send()
            .toCompletableFuture()
            .whenComplete((response, error) -> complete(start, error));
      } catch (final RuntimeException e) {
        complete(start, e);
      }
    }

    private void complete(final long start, final Throwable error) {
      final var end = System.nanoTime();
      final var ok = error == null;
      samples.add(new Sample(start, end, ok, ok ? "" : describe(error)));
      if (ok) {
        lastSuccessNanos = end;
      }
      inFlight.set(false);
    }

    private static String describe(final Throwable error) {
      final var cause = error.getCause() != null ? error.getCause() : error;
      final var message = String.valueOf(cause.getMessage());
      final var firstLine = message.lines().findFirst().orElse("");
      return cause.getClass().getSimpleName()
          + ": "
          + firstLine.substring(0, Math.min(140, firstLine.length()));
    }

    private boolean idle() {
      return !inFlight.get();
    }

    /**
     * The longest time between two accepted requests where the first of them completed in the
     * window, which starts one request interval early so that a gap opened by the restart counts
     * for it.
     */
    private long longestGapNanos(final long fromNanos, final long toNanos) {
      long longest = 0;
      long previous = -1;
      for (final var sample : sorted()) {
        if (!sample.ok()) {
          continue;
        }
        final var slack = 10 * LOAD_INTERVAL.toNanos();
        if (previous >= fromNanos - slack && previous < toNanos - slack) {
          longest = Math.max(longest, sample.endNanos() - previous);
        }
        previous = sample.endNanos();
      }
      return longest;
    }

    private List<Sample> sorted() {
      synchronized (samples) {
        return samples.stream().sorted(Comparator.comparingLong(Sample::endNanos)).toList();
      }
    }

    private List<Sample> startedIn(final long fromNanos, final long toNanos) {
      return sorted().stream()
          .filter(sample -> sample.startNanos() >= fromNanos && sample.startNanos() < toNanos)
          .toList();
    }
  }

  /** Samples which member leads each partition of the tenant, to find leaderless periods. */
  private static final class LeaderSampler implements Runnable {
    private final TestCluster cluster;
    private final String tenantId;
    private final Map<Integer, List<long[]>> leaderless = new ConcurrentHashMap<>();
    private final Map<Integer, Long> leaderlessSince = new HashMap<>();

    private LeaderSampler(final TestCluster cluster, final String tenantId) {
      this.cluster = cluster;
      this.tenantId = tenantId;
    }

    @Override
    public void run() {
      final var now = System.nanoTime();
      final Map<Integer, Integer> leaders = new HashMap<>();
      for (final var broker : cluster.brokers().values()) {
        try {
          for (final var partition : raftPartitions(broker, tenantId)) {
            leaders.merge(
                partition.id().number(), partition.getRole() == Role.LEADER ? 1 : 0, Integer::sum);
          }
        } catch (final RuntimeException ignored) {
          // a partition that stops while it is sampled counts as not leading
        }
      }
      for (int partition = 1; partition <= PARTITIONS; partition++) {
        final var led = leaders.getOrDefault(partition, 0) > 0;
        final var since = leaderlessSince.get(partition);
        if (!led && since == null) {
          leaderlessSince.put(partition, now);
        } else if (led && since != null) {
          leaderlessSince.remove(partition);
          leaderless
              .computeIfAbsent(partition, ignored -> new ArrayList<>())
              .add(new long[] {since, now});
        }
      }
    }

    /** The longest period without a leader of any partition that overlaps the window. */
    private long longestLeaderlessNanos(final long fromNanos, final long toNanos) {
      return leaderless.values().stream()
          .flatMap(List::stream)
          .filter(period -> period[0] < toNanos && period[1] > fromNanos)
          .mapToLong(period -> period[1] - period[0])
          .max()
          .orElse(0);
    }
  }

  /** One cluster, with the handover turned on or off, through several rolling restarts. */
  private static final class Arm {
    private final String name;
    private final TenantRestartHandover handover;
    private final boolean snapshot;
    private final boolean rebalanceAfter;
    private final List<Restart> restarts = new ArrayList<>();
    private final List<Rebalance> rebalances = new ArrayList<>();
    private final List<Map<String, Integer>> leadersAfterRollouts = new ArrayList<>();
    private final List<Map<String, Integer>> leadersAfterRebalances = new ArrayList<>();
    private final List<Long> rolloutMillis = new ArrayList<>();
    private final List<long[]> rebalanceWindows = new ArrayList<>();
    private final List<String> leaderlessAfterRollouts = new ArrayList<>();
    private final List<long[]> rolloutWindows = new ArrayList<>();
    private long otherTenantFailed;
    private long otherTenantMaxLatencyMillis;
    private long otherTenantMaxLatencyBetweenRolloutsMillis;
    private long otherTenantRequests;
    private final Map<String, Long> failureReasons = new HashMap<>();

    private Arm(
        final String name,
        final TenantRestartHandover handover,
        final boolean snapshot,
        final boolean rebalanceAfter) {
      this.name = name;
      this.handover = handover;
      this.snapshot = snapshot;
      this.rebalanceAfter = rebalanceAfter;
    }

    private void run() {
      final var tenantId = "ho" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
      final var running = new AtomicBoolean(true);
      final List<CamundaClient> clients = new ArrayList<>();
      final List<Load> allLoads = new ArrayList<>();
      final var loadSchedule = Executors.newScheduledThreadPool(2);
      final Map<MemberId, List<Load>> loads = new HashMap<>();
      final List<Load> otherTenantLoads = new ArrayList<>();
      final List<Rollout> rollouts = new ArrayList<>();
      ScheduledExecutorService sampling = null;
      try (final var cluster =
          TestCluster.builder()
              .withBrokersCount(3)
              .withReplicationFactor(3)
              .withPartitionsCount(1)
              .useRecordingExporter(false)
              .withBrokerConfig(
                  broker ->
                      TENANTS
                          .configure(broker.withUnauthenticatedAccess())
                          .withClusterConfig(
                              config -> {
                                final var rebalance = config.getRaft().getRebalance();
                                rebalance.setTenantRestartHandover(handover);
                                rebalance.setTenantRestartSnapshot(snapshot);
                              }))
              .build()) {
        cluster.start().awaitCompleteTopology();
        final var coordinator = broker(cluster, 0);
        Awaitility.await("the cluster accepts the creation of " + tenantId)
            .atMost(TIMEOUT)
            .until(
                () ->
                    send(coordinator, "POST", tenantId, tenantConfiguration(cluster, "5m"))
                            .statusCode()
                        == 202);
        awaitRolledOut(coordinator, tenantId, 1);

        final var keys = correlationKeys(cluster, tenantId);
        for (final var entry : cluster.brokers().entrySet()) {
          final var tenantClient =
              TENANTS
                  .newClientBuilder(entry.getValue(), tenantId)
                  .preferRestOverGrpc(false)
                  .defaultRequestTimeout(REQUEST_TIMEOUT)
                  .build();
          final var otherClient =
              TENANTS
                  .newClientBuilder(entry.getValue(), OTHER_TENANT)
                  .preferRestOverGrpc(false)
                  .defaultRequestTimeout(REQUEST_TIMEOUT)
                  .build();
          clients.add(tenantClient);
          clients.add(otherClient);
          for (final var key : keys.values()) {
            final var load = new Load(tenantClient, entry.getKey(), key, running);
            loads.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(load);
            allLoads.add(load);
          }
          final var otherLoad = new Load(otherClient, entry.getKey(), "other", running);
          otherTenantLoads.add(otherLoad);
          allLoads.add(otherLoad);
        }
        for (final var load : allLoads) {
          loadSchedule.scheduleAtFixedRate(load, 0, LOAD_INTERVAL.toNanos(), TimeUnit.NANOSECONDS);
        }
        final var sampler = new LeaderSampler(cluster, tenantId);
        sampling = Executors.newSingleThreadScheduledExecutor();
        sampling.scheduleAtFixedRate(sampler, 0, 5, TimeUnit.MILLISECONDS);

        for (int rollout = 0; rollout <= MEASURED_ROLLOUTS; rollout++) {
          placeOneLeaderOnEveryBroker(cluster, tenantId);
          awaitServing(loads.values().stream().flatMap(List::stream).toList());
          final var version = rollout + 2L;
          final var started = System.nanoTime();
          assertThat(
                  send(
                          coordinator,
                          "PUT",
                          tenantId,
                          tenantConfiguration(cluster, (6 + rollout) + "m"))
                      .statusCode())
              .isEqualTo(202);
          awaitRolledOut(coordinator, tenantId, version);
          final var rolledOut = System.nanoTime();
          checkEveryPartitionLeads(cluster, tenantId, rollout);
          awaitServing(loads.values().stream().flatMap(List::stream).toList());
          awaitCaughtUp(cluster, tenantId);
          final var restartsOfRollout = memberRestarts(cluster, tenantId);
          final var leadersAfterRollout = leadersPerMember(cluster, tenantId);
          long[] rebalanceWindow = null;
          if (rebalanceAfter) {
            final var rebalanceStarted = System.nanoTime();
            rebalance(coordinator);
            awaitServing(loads.values().stream().flatMap(List::stream).toList());
            rebalanceWindow = new long[] {rebalanceStarted, System.nanoTime() + SETTLE.toNanos()};
          }
          if (rollout > 0) {
            rolloutWindows.add(new long[] {started, rolledOut + SETTLE.toNanos()});
            rollouts.add(new Rollout(rollout, restartsOfRollout, rolledOut));
            rolloutMillis.add(TimeUnit.NANOSECONDS.toMillis(rolledOut - started));
            leadersAfterRollouts.add(leadersAfterRollout);
            if (rebalanceWindow != null) {
              rolloutWindows.add(rebalanceWindow);
              rebalanceWindows.add(rebalanceWindow);
              leadersAfterRebalances.add(leadersPerMember(cluster, tenantId));
            }
          }
        }
        running.set(false);
        Awaitility.await("every load's last request completed")
            .atMost(REQUEST_TIMEOUT.multipliedBy(2))
            .until(() -> allLoads.stream().allMatch(Load::idle));
        loadSchedule.shutdownNow();
        sampling.shutdownNow();
        assertThat(sampling.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        for (final var rollout : rollouts) {
          collectRestarts(rollout, loads, sampler);
        }
        for (int index = 0; index < rebalanceWindows.size(); index++) {
          collectRebalance(index + 1, rebalanceWindows.get(index), loads);
        }
        collectOtherTenant(otherTenantLoads);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      } finally {
        running.set(false);
        loadSchedule.shutdownNow();
        if (sampling != null) {
          sampling.shutdownNow();
        }
        clients.forEach(CamundaClient::close);
      }
    }

    /** Finds one correlation key per partition of the tenant. */
    private Map<Integer, String> correlationKeys(final TestCluster cluster, final String tenantId) {
      final Map<Integer, String> keys = new HashMap<>();
      try (final var client =
          TENANTS
              .newClientBuilder(broker(cluster, 0), tenantId)
              .preferRestOverGrpc(false)
              .build()) {
        for (int candidate = 0; keys.size() < PARTITIONS; candidate++) {
          final var key = "key-" + candidate;
          final var messageKey =
              Awaitility.await("publish to the tenant")
                  .atMost(TIMEOUT)
                  .ignoreExceptions()
                  .until(
                      () ->
                          client
                              .newPublishMessageCommand()
                              .messageName("handover")
                              .correlationKey(key)
                              .timeToLive(Duration.ZERO)
                              .send()
                              .join(10, TimeUnit.SECONDS)
                              .getMessageKey(),
                      messageKeyOrNull -> messageKeyOrNull != null);
          keys.putIfAbsent(Protocol.decodePartitionId(messageKey), key);
        }
      }
      return keys;
    }

    /**
     * Waits until every member followed the catch-up of its restarted partitions to the end, so
     * that its last restart reports it; gives up after a while, the report then says so.
     */
    private static void awaitCaughtUp(final TestCluster cluster, final String tenantId) {
      try {
        Awaitility.await("every restarted partition of %s caught up".formatted(tenantId))
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(50))
            .until(
                () ->
                    cluster.brokers().values().stream()
                        .allMatch(
                            broker ->
                                broker
                                    .bean(PhysicalTenantLifecycleService.class)
                                    .lastRestart(tenantId)
                                    .map(restart -> restart.afterRestart().size() >= PARTITIONS)
                                    .orElse(false)));
      } catch (final org.awaitility.core.ConditionTimeoutException e) {
        LOG.warn("Not every restarted partition of {} reported its catch-up", tenantId);
      }
    }

    private static Map<String, Integer> leadersPerMember(
        final TestCluster cluster, final String tenantId) {
      final Map<String, Integer> leaders = new TreeMap<>();
      for (final var broker : cluster.brokers().entrySet()) {
        leaders.put(
            broker.getKey().id(),
            (int)
                raftPartitions(broker.getValue(), tenantId).stream()
                    .filter(partition -> partition.getRole() == Role.LEADER)
                    .count());
      }
      return leaders;
    }

    /** Rebalances the cluster's leadership through the rebalance coordinator and waits for it. */
    private static void rebalance(final TestStandaloneBroker coordinator) {
      final var client = ClusterRebalanceRestClient.of(coordinator.restAddress());
      final var rebalanceId = new AtomicLong();
      Awaitility.await("a rebalance is accepted")
          .atMost(TIMEOUT)
          .pollInterval(Duration.ofMillis(200))
          .until(
              () -> {
                final var response = client.triggerRebalance();
                if (response.status() != 202
                    || response.body() == null
                    || response.body().getRunningRebalance() == null) {
                  return false;
                }
                rebalanceId.set(response.body().getRunningRebalance().getRebalanceId());
                return true;
              });
      Awaitility.await("rebalance %d completed".formatted(rebalanceId.get()))
          .atMost(TIMEOUT)
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(
              () -> {
                final var status = client.getRebalance();
                assertThat(status.status()).isEqualTo(200);
                assertThat(status.body()).isNotNull();
                assertThat(status.body().getLastCompletedRebalance()).isNotNull();
                assertThat(status.body().getLastCompletedRebalance().getRebalanceId())
                    .isEqualTo(rebalanceId.get());
              });
      LOG.info("Rebalance after the rollout: {}", client.getRebalance().body());
    }

    /**
     * Gives every broker the leadership of one partition of the tenant, so that every member
     * restart of the next rollout restarts a leader, in both arms.
     */
    private void placeOneLeaderOnEveryBroker(final TestCluster cluster, final String tenantId) {
      Awaitility.await("every broker leads one partition of " + tenantId)
          .atMost(TIMEOUT)
          .pollInterval(Duration.ofMillis(200))
          .untilAsserted(
              () -> {
                for (int partition = 1; partition <= PARTITIONS; partition++) {
                  final var desired = MemberId.from(String.valueOf(partition - 1));
                  final var leader = leaderOf(cluster, tenantId, partition);
                  assertThat(leader).isPresent();
                  if (!leader.get().getValue().equals(desired)) {
                    assertThat(
                            leader
                                .get()
                                .getKey()
                                .transferLeadership(desired)
                                .get(30, TimeUnit.SECONDS))
                        .isEqualTo(LeadershipTransferResult.TRANSFERRED);
                  }
                }
                for (int partition = 1; partition <= PARTITIONS; partition++) {
                  assertThat(leaderOf(cluster, tenantId, partition).map(Map.Entry::getValue))
                      .contains(MemberId.from(String.valueOf(partition - 1)));
                }
              });
    }

    private static Optional<Map.Entry<RaftPartition, MemberId>> leaderOf(
        final TestCluster cluster, final String tenantId, final int partition) {
      for (final var broker : cluster.brokers().entrySet()) {
        for (final var raftPartition : raftPartitions(broker.getValue(), tenantId)) {
          if (raftPartition.id().number() == partition && raftPartition.getRole() == Role.LEADER) {
            return Optional.of(Map.entry(raftPartition, broker.getKey()));
          }
        }
      }
      return Optional.empty();
    }

    private void checkEveryPartitionLeads(
        final TestCluster cluster, final String tenantId, final int rollout) {
      try {
        Awaitility.await("every partition of %s has a leader".formatted(tenantId))
            .atMost(Duration.ofSeconds(10))
            .until(
                () -> {
                  for (int partition = 1; partition <= PARTITIONS; partition++) {
                    if (leaderOf(cluster, tenantId, partition).isEmpty()) {
                      return false;
                    }
                  }
                  return true;
                });
      } catch (final org.awaitility.core.ConditionTimeoutException e) {
        leaderlessAfterRollouts.add("rollout " + rollout);
      }
    }

    private static void awaitServing(final List<Load> loads) {
      final var since = System.nanoTime();
      Awaitility.await("every partition serves through every gateway")
          .atMost(TIMEOUT)
          .untilAsserted(
              () ->
                  assertThat(loads)
                      .filteredOn(load -> load.lastSuccessNanos <= since)
                      .extracting(load -> load.gateway.id() + "/" + load.correlationKey)
                      .isEmpty());
    }

    private static List<Map.Entry<MemberId, TenantRestart>> memberRestarts(
        final TestCluster cluster, final String tenantId) {
      final List<Map.Entry<MemberId, TenantRestart>> memberRestarts = new ArrayList<>();
      cluster
          .brokers()
          .forEach(
              (member, broker) ->
                  memberRestarts.add(
                      Map.entry(
                          member,
                          broker
                              .bean(PhysicalTenantLifecycleService.class)
                              .lastRestart(tenantId)
                              .orElseThrow())));
      memberRestarts.sort(Comparator.comparingLong(entry -> entry.getValue().startedNanos()));
      return memberRestarts;
    }

    /**
     * The longest time any partition went without an accepted request through the given loads
     * together, counted as {@link Load#longestGapNanos} counts it: a request that one gateway's
     * client waits on does not interrupt the partition while another gateway serves it.
     */
    private static long longestPartitionGapNanos(
        final List<Load> loads, final long fromNanos, final long toNanos) {
      final var byPartition =
          loads.stream().collect(Collectors.groupingBy(load -> load.correlationKey));
      long longest = 0;
      for (final var partitionLoads : byPartition.values()) {
        final var accepted =
            partitionLoads.stream()
                .flatMap(load -> load.sorted().stream())
                .filter(Sample::ok)
                .mapToLong(Sample::endNanos)
                .sorted()
                .toArray();
        final var slack = 10 * LOAD_INTERVAL.toNanos();
        for (int index = 1; index < accepted.length; index++) {
          final var previous = accepted[index - 1];
          if (previous >= fromNanos - slack && previous < toNanos - slack) {
            longest = Math.max(longest, accepted[index] - previous);
          }
        }
      }
      return longest;
    }

    private void collectRestarts(
        final Rollout measured,
        final Map<MemberId, List<Load>> loads,
        final LeaderSampler sampler) {
      final var memberRestarts = measured.restarts();
      final var rollout = measured.number();
      final var rolledOut = measured.rolledOutNanos();
      for (int index = 0; index < memberRestarts.size(); index++) {
        final var member = memberRestarts.get(index).getKey();
        final var restart = memberRestarts.get(index).getValue();
        final var from = restart.startedNanos();
        final var to =
            index + 1 < memberRestarts.size()
                ? memberRestarts.get(index + 1).getValue().startedNanos()
                : rolledOut + SETTLE.toNanos();
        long unavailable = 0;
        long maxLatency = 0;
        long failed = 0;
        long timedOut = 0;
        long ownUnavailable = 0;
        long ownFailed = 0;
        long away = 0;
        long back = 0;
        final var restartedAt = Math.min(Math.max(restart.startedAgainNanos(), from), to);
        final var otherGateways =
            loads.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(member))
                .flatMap(entry -> entry.getValue().stream())
                .toList();
        unavailable = longestPartitionGapNanos(otherGateways, from, to);
        away = longestPartitionGapNanos(otherGateways, from, restartedAt);
        back = longestPartitionGapNanos(otherGateways, restartedAt, to);
        for (final var entry : loads.entrySet()) {
          for (final var load : entry.getValue()) {
            final var started = load.startedIn(from, to);
            final var gap = load.longestGapNanos(from, to);
            final var failures = started.stream().filter(sample -> !sample.ok()).count();
            started.stream()
                .filter(sample -> !sample.ok())
                .forEach(sample -> failureReasons.merge(sample.failure(), 1L, Long::sum));
            if (entry.getKey().equals(member)) {
              ownUnavailable = Math.max(ownUnavailable, gap);
              ownFailed += failures;
            } else {
              failed += failures;
              timedOut +=
                  started.stream()
                      .filter(
                          sample ->
                              sample.endNanos() - sample.startNanos()
                                  >= REQUEST_TIMEOUT.minusMillis(100).toNanos())
                      .count();
              maxLatency =
                  Math.max(
                      maxLatency,
                      started.stream()
                          .mapToLong(sample -> sample.endNanos() - sample.startNanos())
                          .max()
                          .orElse(0));
            }
          }
        }
        restarts.add(
            new Restart(
                rollout,
                member,
                restart.led(),
                restart.handover().partitions(),
                TimeUnit.NANOSECONDS.toMillis(unavailable),
                TimeUnit.NANOSECONDS.toMillis(maxLatency),
                failed,
                timedOut,
                TimeUnit.NANOSECONDS.toMillis(sampler.longestLeaderlessNanos(from, to)),
                TimeUnit.NANOSECONDS.toMillis(ownUnavailable),
                ownFailed,
                TimeUnit.NANOSECONDS.toMillis(away),
                TimeUnit.NANOSECONDS.toMillis(back),
                restart.snapshots(),
                restart.afterRestart()));
      }
    }

    private void collectRebalance(
        final int rollout, final long[] window, final Map<MemberId, List<Load>> loads) {
      long unavailable = 0;
      long maxLatency = 0;
      long failed = 0;
      final var allLoads = loads.values().stream().flatMap(List::stream).toList();
      unavailable = longestPartitionGapNanos(allLoads, window[0], window[1]);
      for (final var load : allLoads) {
        final var started = load.startedIn(window[0], window[1]);
        failed += started.stream().filter(sample -> !sample.ok()).count();
        maxLatency =
            Math.max(
                maxLatency,
                started.stream()
                    .mapToLong(sample -> sample.endNanos() - sample.startNanos())
                    .max()
                    .orElse(0));
      }
      rebalances.add(
          new Rebalance(
              rollout,
              TimeUnit.NANOSECONDS.toMillis(window[1] - window[0] - SETTLE.toNanos()),
              TimeUnit.NANOSECONDS.toMillis(unavailable),
              TimeUnit.NANOSECONDS.toMillis(maxLatency),
              failed));
    }

    /** Per measured rollout, the interruption of every member restart and of the rebalance. */
    private List<Long> rolloutTotals() {
      final Map<Integer, Long> totals = new TreeMap<>();
      restarts.forEach(
          restart -> totals.merge(restart.rollout(), restart.unavailableMillis(), Long::sum));
      rebalances.forEach(
          rebalance -> totals.merge(rebalance.rollout(), rebalance.unavailableMillis(), Long::sum));
      return List.copyOf(totals.values());
    }

    private List<PartitionReclaim> reclaims() {
      return afterRestarts().stream()
          .filter(reclaim -> reclaim.outcome() != PartitionLeadershipReclaim.Outcome.CAUGHT_UP)
          .toList();
    }

    private List<PartitionReclaim> afterRestarts() {
      return restarts.stream().flatMap(restart -> restart.afterRestart().stream()).toList();
    }

    private List<PartitionSnapshot> snapshots() {
      return restarts.stream().flatMap(restart -> restart.snapshots().stream()).toList();
    }

    private void collectOtherTenant(final List<Load> otherTenantLoads) {
      for (final var load : otherTenantLoads) {
        for (final var sample : load.samples) {
          final var inRollout =
              rolloutWindows.stream()
                  .anyMatch(
                      window ->
                          sample.startNanos() >= window[0] && sample.startNanos() < window[1]);
          final var latency =
              TimeUnit.NANOSECONDS.toMillis(sample.endNanos() - sample.startNanos());
          if (inRollout) {
            otherTenantRequests++;
            otherTenantMaxLatencyMillis = Math.max(otherTenantMaxLatencyMillis, latency);
            if (!sample.ok()) {
              otherTenantFailed++;
            }
          } else {
            otherTenantMaxLatencyBetweenRolloutsMillis =
                Math.max(otherTenantMaxLatencyBetweenRolloutsMillis, latency);
          }
        }
      }
    }

    private List<Restart> leaderRestarts() {
      return restarts.stream().filter(Restart::led).toList();
    }

    private List<PartitionReport> handovers() {
      return restarts.stream().flatMap(restart -> restart.handover().stream()).toList();
    }

    private void log() {
      final var report = new StringBuilder();
      report.append(
          "%n%s: %d member restarts, %d of a leader%n"
              .formatted(name, restarts.size(), leaderRestarts().size()));
      report.append(
          "rollout member led unavailable-ms max-latency-ms failed timed-out leaderless-ms own-gateway-unavailable-ms own-gateway-failed away-ms back-ms handover reclaim%n"
              .formatted());
      for (final var restart : restarts) {
        report.append(
            "%d %s %s %d %d %d %d %d %d %d %d %d %s %s%n"
                .formatted(
                    restart.rollout(),
                    restart.member().id(),
                    restart.led(),
                    restart.unavailableMillis(),
                    restart.maxLatencyMillis(),
                    restart.failed(),
                    restart.timedOut(),
                    restart.leaderlessMillis(),
                    restart.ownGatewayUnavailableMillis(),
                    restart.ownGatewayFailed(),
                    restart.awayUnavailableMillis(),
                    restart.backUnavailableMillis(),
                    restart.handover().stream()
                        .filter(PartitionReport::led)
                        .map(
                            partition ->
                                partition.partition().number()
                                    + ":"
                                    + partition.outcome()
                                    + "->"
                                    + partition.newLeader()
                                    + "/"
                                    + partition.took().toMillis()
                                    + "ms")
                        .toList(),
                    restart.afterRestart().stream()
                        .filter(
                            reclaim ->
                                reclaim.outcome() != PartitionLeadershipReclaim.Outcome.CAUGHT_UP)
                        .map(
                            reclaim ->
                                reclaim.partition()
                                    + ":"
                                    + reclaim.outcome()
                                    + "/"
                                    + reclaim.took().toMillis()
                                    + "ms"
                                    + reclaim.answers())
                        .collect(Collectors.joining(", ", "[", "]"))));
      }
      final var leaders = leaderRestarts();
      report.append(
          "leader restarts: unavailable median %d worst %d ms; max latency median %d worst %d ms;"
                  .formatted(
                      median(leaders, Restart::unavailableMillis),
                      worst(leaders, Restart::unavailableMillis),
                      median(leaders, Restart::maxLatencyMillis),
                      worst(leaders, Restart::maxLatencyMillis))
              + " failed median %d worst %d; timed out %d in all; leaderless median %d worst %d ms%n"
                  .formatted(
                      median(leaders, Restart::failed),
                      worst(leaders, Restart::failed),
                      leaders.stream().mapToLong(Restart::timedOut).sum(),
                      median(leaders, Restart::leaderlessMillis),
                      worst(leaders, Restart::leaderlessMillis)));
      report.append(
          "failed requests of the restarted tenant by reason: %s%n".formatted(failureReasons)
              + "other tenant during rollouts: %d requests, %d failed, max latency %d ms (%d ms between rollouts)"
                  .formatted(
                      otherTenantRequests,
                      otherTenantFailed,
                      otherTenantMaxLatencyMillis,
                      otherTenantMaxLatencyBetweenRolloutsMillis));
      LOG.info("{}", report);
    }
  }
}
