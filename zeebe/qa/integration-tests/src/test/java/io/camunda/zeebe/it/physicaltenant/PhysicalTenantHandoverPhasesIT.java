/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import io.camunda.zeebe.broker.system.configuration.RaftCfg.TenantRestartHandover;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.restapi.ClusterRebalanceRestClient;
import io.camunda.zeebe.util.logging.HandoverTrace;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts every leadership handover of a physical tenant on one timeline, phase by phase, under a
 * workload shaped like the load tests: process instances of a one-task process created with their
 * result awaited, their jobs completed by workers. It measures three kinds of handover in one
 * cluster: a coordinated leadership transfer started by a leader and the rebalance that moves it
 * back, the handovers of a tenant restart with transfer back, and a forced snapshot without any
 * handover.
 *
 * <p>The phases come from the {@link HandoverTrace} lines of the brokers and gateways; the client
 * side from the latency of every process instance. The tables are logged, and the raw trace and
 * samples are written to {@code target/handover-phases/}.
 */
final class PhysicalTenantHandoverPhasesIT {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantHandoverPhasesIT.class);

  private static final int PARTITIONS = 3;
  private static final int RATE = Integer.getInteger("handoverPhases.rate", 60);
  private static final int CYCLES = Integer.getInteger("handoverPhases.cycles", 6);
  private static final String SNAPSHOT_PERIOD =
      System.getProperty("handoverPhases.snapshotPeriod", "5m");
  private static final boolean RESTART_SNAPSHOT = Boolean.getBoolean("handoverPhases.snapshot");
  private static final List<String> KINDS =
      List.of(System.getProperty("handoverPhases.kinds", "clt,snapshot,restart").split(","));
  private static final List<String> DEBUG_LOGGERS =
      List.of(System.getProperty("handoverPhases.debugLoggers", "").split(",")).stream()
          .filter(logger -> !logger.isBlank())
          .toList();
  private static final boolean DIAGNOSE = Boolean.getBoolean("handoverPhases.diagnose");
  private static final boolean STREAMING_WORKERS =
      Boolean.parseBoolean(System.getProperty("handoverPhases.streamingWorkers", "true"));
  private static final Duration TIMEOUT = Duration.ofMinutes(2);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration SETTLE = Duration.ofSeconds(4);
  private static final Duration WARM_UP = Duration.ofSeconds(20);
  private static final String JOB_TYPE = "handover-phases";
  private static final String PROCESS_ID = "handover_phases_one_task";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Path OUTPUT = Path.of("target", "handover-phases");

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .build();

  private static final List<String> QUIET_LOGGERS =
      List.of("io.camunda.zeebe", "io.camunda.zeebe.dynamic", "io.atomix", "io.atomix.raft");

  @Test
  void shouldDecomposeEveryHandoverIntoPhases() throws IOException {
    // given
    final var tenantId = "hp" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    final List<Mark> marks = Collections.synchronizedList(new ArrayList<>());
    final List<Sample> samples = Collections.synchronizedList(new ArrayList<>());
    final List<SnapshotProbe> snapshotProbes = new ArrayList<>();
    final List<TraceLine> trace;
    final List<JobSample> jobs;
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
                              rebalance.setTenantRestartHandover(
                                  TenantRestartHandover.TRANSFER_BACK);
                              rebalance.setTenantRestartSnapshot(RESTART_SNAPSHOT);
                            }))
            .build()) {
      cluster.start().awaitCompleteTopology();
      // attached once the brokers started, as each broker's Spring Boot start reconfigures logging
      final var capture = TraceCapture.capturing();
      final var coordinator = broker(cluster, 0);
      Awaitility.await("the cluster accepts the creation of " + tenantId)
          .atMost(TIMEOUT)
          .until(() -> send(coordinator, "POST", tenantId, configuration(1)) == 202);
      awaitRolledOut(coordinator, tenantId, 1);

      final Workload workload = new Workload(cluster, tenantId, samples);
      final var positions = new PositionSampler(cluster, tenantId);
      final var positionSampling = Executors.newSingleThreadScheduledExecutor();
      positionSampling.scheduleAtFixedRate(positions, 0, 50, TimeUnit.MILLISECONDS);
      try (workload) {
        workload.start();
        pause(WARM_UP);

        // when
        for (int cycle = 0; KINDS.contains("clt") && cycle < CYCLES; cycle++) {
          marks.add(new Mark("setup", System.currentTimeMillis()));
          placeOneLeaderOnEveryBroker(cluster, tenantId);
          pause(SETTLE);
          final int partition = cycle % PARTITIONS + 1;
          final var leader = leaderOf(cluster, tenantId, partition).orElseThrow().getValue();
          marks.add(new Mark("clt-transfer", System.currentTimeMillis()));
          handOverLeadership(broker(cluster, Integer.parseInt(leader.id())), tenantId);
          Awaitility.await("partition %d moved away from %s".formatted(partition, leader))
              .atMost(TIMEOUT)
              .until(
                  () ->
                      leaderOf(cluster, tenantId, partition)
                          .map(Map.Entry::getValue)
                          .filter(current -> !current.equals(leader))
                          .isPresent());
          pause(SETTLE);
          marks.add(new Mark("clt-rebalance", System.currentTimeMillis()));
          rebalance(coordinator);
          pause(SETTLE);
        }
        marks.add(new Mark("setup", System.currentTimeMillis()));
        placeOneLeaderOnEveryBroker(cluster, tenantId);
        pause(SETTLE);
        for (int cycle = 0; KINDS.contains("snapshot") && cycle < 4 * CYCLES; cycle++) {
          final int partition = cycle % PARTITIONS + 1;
          final boolean onLeader = cycle % 2 == 0;
          final var leader = broker(cluster, partition - 1);
          final var member = onLeader ? leader : broker(cluster, partition % 3);
          marks.add(
              new Mark(
                  "snapshot-" + (onLeader ? "leader" : "follower"), System.currentTimeMillis()));
          final var before = ProcessingLatency.of(leader, tenantId, partition);
          final var started = System.nanoTime();
          zeebePartition(member, tenantId, partition).forceSnapshot().join();
          final var took = Duration.ofNanos(System.nanoTime() - started);
          final var during = ProcessingLatency.of(leader, tenantId, partition).minus(before);
          final var controlStart = ProcessingLatency.of(leader, tenantId, partition);
          pause(took);
          final var control = ProcessingLatency.of(leader, tenantId, partition).minus(controlStart);
          snapshotProbes.add(
              new SnapshotProbe(
                  onLeader ? "leader" : "follower", partition, took, during, control));
          pause(Duration.ofSeconds(1));
        }
        for (int rollout = 0;
            KINDS.contains("restart") && rollout < Math.max(1, CYCLES / 3);
            rollout++) {
          marks.add(new Mark("setup", System.currentTimeMillis()));
          placeOneLeaderOnEveryBroker(cluster, tenantId);
          pause(SETTLE);
          marks.add(new Mark("tenant-restart", System.currentTimeMillis()));
          assertThat(send(coordinator, "PUT", tenantId, configuration(rollout + 2))).isEqualTo(202);
          if (DIAGNOSE) {
            for (final var at : List.of(2, 5, 9)) {
              pause(Duration.ofSeconds(at == 2 ? 2 : at == 5 ? 3 : 4));
              diagnose(cluster, tenantId, "rollout%d-%ds".formatted(rollout, at));
            }
          }
          awaitRolledOut(coordinator, tenantId, rollout + 2L);
          assertThat(cluster.brokers().values())
              .as("every member's restarted replicas caught up before the rollout moved on")
              .allSatisfy(
                  broker ->
                      assertThat(
                              broker
                                  .bean(PhysicalTenantLifecycleService.class)
                                  .lastRestart(tenantId)
                                  .orElseThrow()
                                  .afterRestart())
                          .hasSize(PARTITIONS));
          pause(SETTLE);
        }
      }
      positionSampling.shutdownNow();
      Files.createDirectories(OUTPUT);
      Files.write(OUTPUT.resolve("positions.csv"), positions.lines());
      trace = capture.lines();
      capture.close();
      jobs = workload.jobs();
    }

    // then
    final var handovers = Handover.reconstruct(trace, marks);
    final var snapshots = SnapshotStall.reconstruct(trace, marks);
    write(trace, samples, marks, jobs);
    logHandovers(handovers, samples);
    logSnapshots(snapshots, samples, snapshotProbes);
    if (KINDS.contains("clt")) {
      assertThat(handovers).extracting(Handover::label).contains("clt-transfer", "clt-rebalance");
    }
    if (KINDS.contains("restart")) {
      assertThat(handovers).extracting(Handover::label).contains("restart-away", "restart-back");
      assertThat(samples)
          .as("no request is turned away because the tenant restarts on the gateway's member")
          .noneMatch(sample -> sample.failure().contains("is unavailable"));
    }
    assertThat(handovers)
        .allSatisfy(handover -> assertThat(handover.result()).isEqualTo("TRANSFERRED"));
    if (KINDS.contains("snapshot")) {
      assertThat(snapshotProbes).isNotEmpty();
    }
  }

  private static Map<String, Object> configuration(final int version) {
    final Map<String, Object> configuration = new HashMap<>();
    configuration.put("name", "Phases " + version);
    configuration.put("cluster.partition-count", String.valueOf(PARTITIONS));
    configuration.put("data.secondary-storage.type", "none");
    configuration.put("data.snapshot-period", SNAPSHOT_PERIOD);
    configuration.put("security.initialization.default-roles.admin.users[0]", "admin");
    return configuration;
  }

  private static void pause(final Duration duration) {
    Awaitility.await().pollDelay(duration).atMost(duration.plusSeconds(1)).until(() -> true);
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

  private static ZeebePartition zeebePartition(
      final TestStandaloneBroker broker, final String tenantId, final int partition) {
    return broker
        .bean(Broker.class)
        .getBrokerContext()
        .getPartitionManagers()
        .get(tenantId)
        .getZeebePartitions()
        .stream()
        .filter(zeebePartition -> zeebePartition.getPartitionId() == partition)
        .findFirst()
        .orElseThrow();
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

  private static void placeOneLeaderOnEveryBroker(
      final TestCluster cluster, final String tenantId) {
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
        .until(
            () -> {
              final var status = client.getRebalance();
              return status.status() == 200
                  && status.body() != null
                  && status.body().getLastCompletedRebalance() != null
                  && status.body().getLastCompletedRebalance().getRebalanceId()
                      == rebalanceId.get();
            });
  }

  private static void handOverLeadership(final TestStandaloneBroker broker, final String tenantId) {
    try (final var http = HttpClient.newHttpClient()) {
      final var request =
          HttpRequest.newBuilder(
                  URI.create(
                      broker.actuatorUri("partitions", "handOverLeadership")
                          + "?physicalTenant="
                          + tenantId))
              .header("Content-Type", "application/json")
              .POST(BodyPublishers.ofString("{}"))
              .build();
      assertThat(http.send(request, BodyHandlers.ofString()).statusCode()).isEqualTo(200);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** Writes every broker's job streams and a thread dump of this JVM to the output directory. */
  private static void diagnose(
      final TestCluster cluster, final String tenantId, final String label) {
    try (final var http = HttpClient.newHttpClient()) {
      Files.createDirectories(OUTPUT);
      for (final var broker : cluster.brokers().entrySet()) {
        final var body =
            http.send(
                    HttpRequest.newBuilder(broker.getValue().actuatorUri("jobstreams"))
                        .GET()
                        .build(),
                    BodyHandlers.ofString())
                .body();
        Files.writeString(
            OUTPUT.resolve("jobstreams-%s-broker%s.json".formatted(label, broker.getKey().id())),
            body);
      }
      final var dump = new StringBuilder();
      for (final var info :
          java.lang.management.ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
        dump.append(info.toString());
        for (final var frame : info.getStackTrace()) {
          dump.append("\tat ").append(frame).append('\n');
        }
        dump.append('\n');
      }
      Files.writeString(OUTPUT.resolve("threads-%s.txt".formatted(label)), dump);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static int send(
      final TestStandaloneBroker broker,
      final String method,
      final String tenantId,
      final Map<String, Object> body) {
    try (final var http = HttpClient.newHttpClient()) {
      final var request =
          HttpRequest.newBuilder(broker.actuatorUri("physicalTenants", tenantId))
              .header("Content-Type", "application/json")
              .method(method, BodyPublishers.ofString(JSON.writeValueAsString(body)))
              .build();
      return http.send(request, BodyHandlers.ofString()).statusCode();
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void awaitRolledOut(
      final TestStandaloneBroker coordinator, final String tenantId, final long version) {
    Awaitility.await("version %d of %s rolled out".formatted(version, tenantId))
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(100))
        .ignoreExceptions()
        .until(
            () -> {
              try (final var http = HttpClient.newHttpClient()) {
                final var status =
                    JSON.readTree(
                        http.send(
                                HttpRequest.newBuilder(
                                        coordinator.actuatorUri("physicalTenants", tenantId))
                                    .GET()
                                    .build(),
                                BodyHandlers.ofString())
                            .body());
                return status.path("version").asLong() == version
                    && status.path("rolledOut").asBoolean();
              }
            });
  }

  private static void write(
      final List<TraceLine> trace,
      final List<Sample> samples,
      final List<Mark> marks,
      final List<JobSample> jobs)
      throws IOException {
    Files.createDirectories(OUTPUT);
    Files.write(
        OUTPUT.resolve("trace.log"),
        trace.stream().map(line -> line.node() + " " + line.message()).toList());
    synchronized (samples) {
      Files.write(
          OUTPUT.resolve("samples.csv"),
          samples.stream()
              .map(
                  sample ->
                      sample.sentAtMs()
                          + ","
                          + sample.latencyMs()
                          + ","
                          + sample.partition()
                          + ","
                          + sample.failure())
              .toList());
    }
    Files.write(
        OUTPUT.resolve("jobs.csv"),
        jobs.stream()
            .map(
                job ->
                    job.receivedAtMs()
                        + ","
                        + job.completeMs()
                        + ","
                        + job.partition()
                        + ","
                        + job.failure())
            .toList());
    Files.write(
        OUTPUT.resolve("marks.csv"),
        marks.stream().map(mark -> mark.atMs() + "," + mark.label()).toList());
  }

  private static void logHandovers(final List<Handover> handovers, final List<Sample> samples) {
    final var table = new StringBuilder();
    table.append(
        "\nhandover phases, ms after the old leader started the transfer"
            + " (label partition from->to: frozen caughtUp timeoutNowSent timeoutNowReceived"
            + " candidate raftLeader leaderReady oldFollower | transition(prepare+steps)"
            + " replay(positions/ms) processingStarted commandApiLeading topologyPublished"
            + " gateways firstAccepted firstCommand firstResponse | finished | old leader"
            + " rejected paused/notLeader | client: PIs on partition sent t0-1s..t0+3s"
            + " worst/p99 ms, before p99, failed on any partition)\n");
    final Map<String, List<Handover>> byLabel = new LinkedHashMap<>();
    for (final var handover : handovers) {
      byLabel.computeIfAbsent(handover.label(), ignored -> new ArrayList<>()).add(handover);
      final var client = ClientImpact.of(samples, handover.partition(), handover.startedAtMs());
      handover.client = client;
      table.append(handover.describe()).append(" | ").append(client).append('\n');
    }
    table.append("\nmedians per kind (ms after transfer start)\n");
    table.append(
        "kind n frozen caughtUp timeoutNowSent raftLeader leaderReady transitionDone"
            + " replayPositions replayMs processingStarted topologyPublished lastGateway"
            + " firstResponse clientWorst clientP99 beforeP99\n");
    byLabel.forEach(
        (label, list) ->
            table
                .append(label)
                .append(' ')
                .append(list.size())
                .append(' ')
                .append(median(list, h -> h.at("transfer.frozen")))
                .append(' ')
                .append(median(list, h -> h.at("transfer.caughtUp")))
                .append(' ')
                .append(median(list, h -> h.at("transfer.timeoutNowSent")))
                .append(' ')
                .append(median(list, h -> h.at("raft.leader")))
                .append(' ')
                .append(median(list, h -> h.at("raft.leaderReady")))
                .append(' ')
                .append(median(list, h -> h.at("partition.transition")))
                .append(' ')
                .append(median(list, h -> h.number("replay.finished", "positions")))
                .append(' ')
                .append(median(list, h -> h.number("replay.finished", "tookMs")))
                .append(' ')
                .append(median(list, h -> h.at("processing.started")))
                .append(' ')
                .append(median(list, h -> h.at("topology.leaderPublished")))
                .append(' ')
                .append(median(list, Handover::lastGatewayAt))
                .append(' ')
                .append(median(list, h -> h.at("processing.firstResponse")))
                .append(' ')
                .append(median(list, h -> h.client.worstMs()))
                .append(' ')
                .append(median(list, h -> h.client.p99Ms()))
                .append(' ')
                .append(median(list, h -> h.client.beforeP99Ms()))
                .append('\n'));
    LOG.info("{}", table);
  }

  private static void logSnapshots(
      final List<SnapshotStall> snapshots,
      final List<Sample> samples,
      final List<SnapshotProbe> probes) {
    final var table = new StringBuilder();
    table.append(
        "\nforced snapshots (label partition node: transient ms, total ms, outcome | client: PIs"
            + " on the partition sent during the snapshot and 1 s after, worst/p99 ms, before"
            + " p99, failed on any partition)\n");
    for (final var snapshot : snapshots) {
      final var client =
          ClientImpact.during(
              samples, snapshot.partition(), snapshot.startedAtMs(), snapshot.finishedAtMs());
      table.append(snapshot).append(" | ").append(client).append('\n');
    }
    table.append(
        "\nforced snapshots against an equally long control window right after, on the leader of"
            + " the partition: commands processed, mean and max processing latency (ms from"
            + " written to processed)\n");
    for (final var probe : probes) {
      table.append(probe).append('\n');
    }
    for (final var role : List.of("leader", "follower")) {
      final var ofRole = probes.stream().filter(probe -> probe.role().equals(role)).toList();
      final long commandsDuring = ofRole.stream().mapToLong(p -> p.during().count()).sum();
      final long commandsControl = ofRole.stream().mapToLong(p -> p.control().count()).sum();
      final double totalDuring = ofRole.stream().mapToDouble(p -> p.during().totalMs()).sum();
      final double totalControl = ofRole.stream().mapToDouble(p -> p.control().totalMs()).sum();
      table.append(
          "snapshot on the %s, %d snapshots: mean processing latency %.2f ms over %d commands"
                  .formatted(
                      role,
                      ofRole.size(),
                      totalDuring / Math.max(1, commandsDuring),
                      commandsDuring)
              + " during, %.2f ms over %d commands in the control windows\n"
                  .formatted(totalControl / Math.max(1, commandsControl), commandsControl));
    }
    LOG.info("{}", table);
  }

  private static <T> long median(
      final List<T> items, final java.util.function.ToLongFunction<T> f) {
    final var values = items.stream().mapToLong(f).filter(v -> v >= 0).sorted().toArray();
    return values.length == 0 ? -1 : values[values.length / 2];
  }

  private record Mark(String label, long atMs) {}

  /** The processing latency timer of one partition on one broker, read at one moment. */
  private record ProcessingLatency(long count, double totalMs) {

    static ProcessingLatency of(
        final TestStandaloneBroker broker, final String tenantId, final int partition) {
      final var timers =
          Search.in(broker.bean(MeterRegistry.class))
              .name("zeebe.stream.processor.latency")
              .tag("partition", String.valueOf(partition))
              .tag("physicalTenant", tenantId)
              .timers();
      return new ProcessingLatency(
          timers.stream().mapToLong(Timer::count).sum(),
          timers.stream().mapToDouble(timer -> timer.totalTime(TimeUnit.MILLISECONDS)).sum());
    }

    ProcessingLatency minus(final ProcessingLatency earlier) {
      return new ProcessingLatency(count - earlier.count, totalMs - earlier.totalMs);
    }

    double meanMs() {
      return count == 0 ? -1 : totalMs / count;
    }
  }

  private record SnapshotProbe(
      String role,
      int partition,
      Duration took,
      ProcessingLatency during,
      ProcessingLatency control) {

    @Override
    public String toString() {
      return "%s p%d took %d ms: during %d commands mean %.2f ms, control %d commands mean %.2f ms"
          .formatted(
              role,
              partition,
              took.toMillis(),
              during.count(),
              during.meanMs(),
              control.count(),
              control.meanMs());
    }
  }

  private record Sample(long sentAtMs, long latencyMs, int partition, String failure) {}

  /**
   * Samples, per broker and partition of the tenant, the Raft commit index and the stream
   * processor's last processed position, to tell a stalled commit from stalled processing.
   */
  private static final class PositionSampler implements Runnable {
    private final TestCluster cluster;
    private final String tenantId;
    private final ConcurrentLinkedQueue<String> lines = new ConcurrentLinkedQueue<>();

    private PositionSampler(final TestCluster cluster, final String tenantId) {
      this.cluster = cluster;
      this.tenantId = tenantId;
    }

    @Override
    public void run() {
      final var now = System.currentTimeMillis();
      for (final var broker : cluster.brokers().entrySet()) {
        try {
          final var registry = broker.getValue().bean(MeterRegistry.class);
          for (final var name :
              List.of(
                  "atomix.partition.raft.commit.index",
                  "zeebe.stream.processor.last.processed.position")) {
            Search.in(registry).name(name).tag("physicalTenant", tenantId).gauges().stream()
                .forEach(
                    gauge ->
                        lines.add(
                            "%d,%s,%s,%s,%d"
                                .formatted(
                                    now,
                                    broker.getKey().id(),
                                    name,
                                    gauge.getId().getTag("partition"),
                                    (long) gauge.value())));
          }
        } catch (final RuntimeException e) {
          lines.add(
              "%d,%s,error,%s,0"
                  .formatted(now, broker.getKey().id(), e.getClass().getSimpleName()));
        }
      }
    }

    List<String> lines() {
      return List.copyOf(lines);
    }
  }

  /** A job a worker received, and how its completion went. */
  private record JobSample(long receivedAtMs, long completeMs, int partition, String failure) {}

  private record TraceLine(String node, long atMs, String event, Map<String, String> fields) {
    private static final Pattern FIELD = Pattern.compile("(\\w+)=(\\S*)");

    static Optional<TraceLine> parse(final String node, final String message) {
      if (!message.startsWith("handover ")) {
        return Optional.empty();
      }
      final Map<String, String> fields = new HashMap<>();
      final var matcher = FIELD.matcher(message);
      while (matcher.find()) {
        fields.put(matcher.group(1), matcher.group(2));
      }
      return Optional.of(
          new TraceLine(node, Long.parseLong(fields.get("at")), fields.get("event"), fields));
    }

    String message() {
      return "at=%d event=%s %s".formatted(atMs, event, fields);
    }

    String field(final String name) {
      return fields.getOrDefault(name, "");
    }

    int partition() {
      return Integer.parseInt(fields.getOrDefault("partition", "0"));
    }

    boolean on(final String otherNode) {
      return node.equals(otherNode);
    }
  }

  /** What the clients saw on one partition around an event. */
  private record ClientImpact(long worstMs, long p99Ms, long beforeP99Ms, long failed, long count) {

    static ClientImpact of(final List<Sample> samples, final int partition, final long atMs) {
      return between(samples, partition, atMs - 1000, atMs + 3000, atMs);
    }

    static ClientImpact during(
        final List<Sample> samples, final int partition, final long fromMs, final long toMs) {
      return between(samples, partition, fromMs, toMs + 1000, fromMs);
    }

    private static ClientImpact between(
        final List<Sample> samples,
        final int partition,
        final long fromMs,
        final long toMs,
        final long anchorMs) {
      final List<Sample> copy;
      synchronized (samples) {
        copy = List.copyOf(samples);
      }
      final Predicate<Sample> onPartition =
          sample -> sample.partition() == partition || sample.partition() == 0;
      final var window =
          copy.stream()
              .filter(onPartition)
              .filter(sample -> sample.sentAtMs() >= fromMs && sample.sentAtMs() < toMs)
              .toList();
      final var before =
          copy.stream()
              .filter(sample -> sample.partition() == partition)
              .filter(
                  sample ->
                      sample.sentAtMs() >= anchorMs - 11_000 && sample.sentAtMs() < anchorMs - 1000)
              .mapToLong(Sample::latencyMs)
              .sorted()
              .toArray();
      final var latencies =
          window.stream()
              .filter(sample -> sample.failure().isEmpty())
              .mapToLong(Sample::latencyMs)
              .sorted()
              .toArray();
      return new ClientImpact(
          latencies.length == 0 ? -1 : latencies[latencies.length - 1],
          percentile(latencies, 0.99),
          percentile(before, 0.99),
          window.stream().filter(sample -> !sample.failure().isEmpty()).count(),
          window.size());
    }

    private static long percentile(final long[] sorted, final double quantile) {
      if (sorted.length == 0) {
        return -1;
      }
      return sorted[Math.min(sorted.length - 1, (int) Math.ceil(quantile * sorted.length) - 1)];
    }

    @Override
    public String toString() {
      return "%d/%d before %d failed %d of %d"
          .formatted(worstMs, p99Ms, beforeP99Ms, failed, count);
    }
  }

  /** One leadership transfer, from the old leader's start to the new leader's first response. */
  private static final class Handover {
    private final String label;
    private final String group;
    private final int partition;
    private final String from;
    private final String to;
    private final long startedAtMs;
    private final Map<String, TraceLine> events = new LinkedHashMap<>();
    private final List<TraceLine> gateways = new ArrayList<>();
    private ClientImpact client;

    private Handover(
        final String label,
        final String group,
        final int partition,
        final String from,
        final String to,
        final long startedAtMs) {
      this.label = label;
      this.group = group;
      this.partition = partition;
      this.from = from;
      this.to = to;
      this.startedAtMs = startedAtMs;
    }

    static List<Handover> reconstruct(final List<TraceLine> trace, final List<Mark> marks) {
      final List<Handover> handovers = new ArrayList<>();
      final var starts =
          trace.stream().filter(line -> line.event().equals("transfer.started")).toList();
      for (final var start : starts) {
        final var label = labelOf(trace, marks, start);
        if (label == null) {
          continue;
        }
        final var handover =
            new Handover(
                label,
                start.field("group"),
                start.partition(),
                start.node(),
                start.field("target"),
                start.atMs());
        final long until =
            starts.stream()
                .filter(
                    other ->
                        other.atMs() > start.atMs()
                            && other.partition() == start.partition()
                            && other.field("group").equals(start.field("group")))
                .mapToLong(TraceLine::atMs)
                .min()
                .orElse(start.atMs() + 10_000);
        final var window =
            trace.stream()
                .filter(line -> line.atMs() >= start.atMs() && line.atMs() < until)
                .filter(
                    line ->
                        line.partition() == start.partition()
                            && line.field("group").equals(start.field("group")))
                .toList();
        handover.collect(window);
        handovers.add(handover);
      }
      return handovers;
    }

    private static String labelOf(
        final List<TraceLine> trace, final List<Mark> marks, final TraceLine start) {
      final var mark =
          marks.stream()
              .filter(candidate -> candidate.atMs() <= start.atMs())
              .max(Comparator.comparingLong(Mark::atMs))
              .orElse(null);
      if (mark == null || start.atMs() - mark.atMs() > 60_000) {
        return null;
      }
      return switch (mark.label()) {
        case "tenant-restart" -> {
          final var requestedByTarget =
              trace.stream()
                  .anyMatch(
                      line ->
                          line.event().equals("transfer.requested")
                              && line.on("Broker-" + start.field("target"))
                              && line.partition() == start.partition()
                              && line.atMs() <= start.atMs()
                              && start.atMs() - line.atMs() < 2000);
          yield requestedByTarget ? "restart-back" : "restart-away";
        }
        case "clt-transfer" -> start.atMs() - mark.atMs() < 2000 ? "clt-transfer" : null;
        case "clt-rebalance" -> "clt-rebalance";
        default -> null;
      };
    }

    private void collect(final List<TraceLine> window) {
      final var target = "Broker-" + to;
      for (final var line : window) {
        final var event = line.event();
        final String key;
        if (line.on(from)) {
          key =
              switch (event) {
                case "transfer.frozen",
                    "transfer.caughtUp",
                    "transfer.timeoutNowSent",
                    "transfer.timeoutNowAccepted",
                    "transfer.finished" ->
                    event;
                case "raft.role" ->
                    line.field("role").equals("FOLLOWER") ? "raft.oldFollower" : null;
                case "commandApi.paused" -> "old.commandApi.paused";
                case "commandApi.notLeading" -> "old.commandApi.notLeading";
                case "commandApi.resumed" -> "old.commandApi.resumed";
                default -> null;
              };
        } else if (line.on(target)) {
          key =
              switch (event) {
                case "raft.timeoutNowReceived",
                    "raft.leaderReady",
                    "replay.finished",
                    "processing.started",
                    "commandApi.leading",
                    "commandApi.firstAccepted",
                    "processing.firstCommand",
                    "processing.firstResponse",
                    "topology.leaderPublished" ->
                    event;
                case "partition.transition" ->
                    line.field("role").equals("LEADER") ? "partition.transition" : null;
                case "raft.role" ->
                    switch (line.field("role")) {
                      case "CANDIDATE" -> "raft.candidate";
                      case "LEADER" -> "raft.leader";
                      default -> null;
                    };
                default -> null;
              };
        } else {
          key = null;
        }
        if (event.equals("gateway.leaderChanged") && line.field("leader").equals(to)) {
          if (gateways.stream()
              .noneMatch(seen -> seen.field("gateway").equals(line.field("gateway")))) {
            gateways.add(line);
          }
          continue;
        }
        if (key != null) {
          events.putIfAbsent(key, line);
        }
      }
    }

    String label() {
      return label;
    }

    int partition() {
      return partition;
    }

    long startedAtMs() {
      return startedAtMs;
    }

    String result() {
      return Optional.ofNullable(events.get("transfer.finished"))
          .map(line -> line.field("result"))
          .orElse("unknown");
    }

    long at(final String event) {
      final var line = events.get(event);
      return line == null ? -1 : line.atMs() - startedAtMs;
    }

    long number(final String event, final String field) {
      final var line = events.get(event);
      if (line == null || line.field(field).isEmpty()) {
        return -1;
      }
      return Long.parseLong(line.field(field));
    }

    long lastGatewayAt() {
      return gateways.stream().mapToLong(line -> line.atMs() - startedAtMs).max().orElse(-1);
    }

    String describe() {
      final var transition = events.get("partition.transition");
      final var replay = events.get("replay.finished");
      final var oldPaused = events.get("old.commandApi.resumed");
      final var oldNotLeading = events.get("old.commandApi.notLeading");
      return "%s p%d %s->%s: %d %d %d %d %d %d %d %d | %d(%s+%s %s) %s/%s %d %d %d %s %d %d %d | %s %d | %s/%s"
          .formatted(
              label,
              partition,
              from.replace("Broker-", ""),
              to,
              at("transfer.frozen"),
              at("transfer.caughtUp"),
              at("transfer.timeoutNowSent"),
              at("raft.timeoutNowReceived"),
              at("raft.candidate"),
              at("raft.leader"),
              at("raft.leaderReady"),
              at("raft.oldFollower"),
              at("partition.transition"),
              transition == null ? "?" : transition.field("prepareMs"),
              transition == null ? "?" : transition.field("stepsMs"),
              transition == null ? "" : transition.field("steps"),
              replay == null ? "?" : replay.field("positions"),
              replay == null ? "?" : replay.field("tookMs"),
              at("processing.started"),
              at("commandApi.leading"),
              at("topology.leaderPublished"),
              gateways.stream()
                  .map(line -> line.field("gateway") + "@" + (line.atMs() - startedAtMs))
                  .collect(Collectors.joining(",", "[", "]")),
              at("commandApi.firstAccepted"),
              at("processing.firstCommand"),
              at("processing.firstResponse"),
              result(),
              at("transfer.finished"),
              (oldPaused == null ? "?" : oldPaused.field("rejectedPaused"))
                  + "+"
                  + (oldNotLeading == null ? "?" : oldNotLeading.field("rejectedPaused")),
              oldNotLeading == null ? "?" : oldNotLeading.field("rejectedNotLeader"));
    }
  }

  /** A forced snapshot, from the director's start to its persisted snapshot. */
  private record SnapshotStall(
      String label,
      int partition,
      String node,
      long startedAtMs,
      long finishedAtMs,
      String transientMs,
      String outcome) {

    static List<SnapshotStall> reconstruct(final List<TraceLine> trace, final List<Mark> marks) {
      return trace.stream()
          .filter(line -> line.event().equals("snapshot.finished"))
          .filter(line -> line.field("forced").equals("true"))
          .map(
              line -> {
                final var mark =
                    marks.stream()
                        .filter(candidate -> candidate.atMs() <= line.atMs())
                        .max(Comparator.comparingLong(Mark::atMs))
                        .map(Mark::label)
                        .orElse("none");
                final long took = Long.parseLong(line.field("tookMs"));
                return new SnapshotStall(
                    mark,
                    line.partition(),
                    line.node(),
                    line.atMs() - took,
                    line.atMs(),
                    line.field("transientMs"),
                    line.field("outcome"));
              })
          .toList();
    }

    @Override
    public String toString() {
      return "%s p%d %s: %s %d %s"
          .formatted(label, partition, node, transientMs, finishedAtMs - startedAtMs, outcome);
    }
  }

  /**
   * Creates process instances of a one-task process at a fixed rate, awaiting each one's result,
   * through every broker's gateway in turn, and completes their jobs with a worker per gateway. The
   * workers use clients of their own, as the load tests' workers do: on a shared client, instances
   * awaiting their result can take every stream of the connection and starve the workers.
   */
  private static final class Workload implements AutoCloseable {
    private final TestCluster cluster;
    private final String tenantId;
    private final List<Sample> samples;
    private final List<JobSample> jobs = Collections.synchronizedList(new ArrayList<>());
    private final List<CamundaClient> clients = new ArrayList<>();
    private final List<CamundaClient> workerClients = new ArrayList<>();
    private final List<JobWorker> workers = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger next = new AtomicInteger();
    private final ScheduledExecutorService schedule = Executors.newSingleThreadScheduledExecutor();

    private Workload(final TestCluster cluster, final String tenantId, final List<Sample> samples) {
      this.cluster = cluster;
      this.tenantId = tenantId;
      this.samples = samples;
    }

    void start() {
      for (final var broker : cluster.brokers().values()) {
        clients.add(
            TENANTS
                .newClientBuilder(broker, tenantId)
                .preferRestOverGrpc(false)
                .defaultRequestTimeout(REQUEST_TIMEOUT)
                .build());
      }
      TENANTS.deploy(
          clients.getFirst(),
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent()
              .serviceTask("task", task -> task.zeebeJobType(JOB_TYPE))
              .endEvent()
              .done(),
          PROCESS_ID);
      for (final var broker : cluster.brokers().values()) {
        final var workerClient =
            TENANTS
                .newClientBuilder(broker, tenantId)
                .preferRestOverGrpc(false)
                .defaultRequestTimeout(REQUEST_TIMEOUT)
                .build();
        workerClients.add(workerClient);
        workers.add(
            workerClient
                .newWorker()
                .jobType(JOB_TYPE)
                .handler(
                    (jobClient, job) -> {
                      final var receivedAt = System.currentTimeMillis();
                      jobClient
                          .newCompleteCommand(job)
                          .send()
                          .toCompletableFuture()
                          .whenComplete(
                              (ignored, error) ->
                                  jobs.add(
                                      new JobSample(
                                          receivedAt,
                                          System.currentTimeMillis() - receivedAt,
                                          Protocol.decodePartitionId(job.getKey()),
                                          error == null ? "" : describe(error))));
                    })
                .maxJobsActive(64)
                .streamEnabled(STREAMING_WORKERS)
                .pollInterval(Duration.ofMillis(10))
                .open());
      }
      schedule.scheduleAtFixedRate(
          this::createOne, 0, TimeUnit.SECONDS.toNanos(1) / RATE, TimeUnit.NANOSECONDS);
    }

    private void createOne() {
      if (!running.get()) {
        return;
      }
      final var client = clients.get(Math.floorMod(next.getAndIncrement(), clients.size()));
      final var sentAt = System.currentTimeMillis();
      inFlight.incrementAndGet();
      try {
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .withResult()
            .requestTimeout(REQUEST_TIMEOUT)
            .send()
            .toCompletableFuture()
            .whenComplete(
                (result, error) -> {
                  final var latency = System.currentTimeMillis() - sentAt;
                  if (error == null) {
                    samples.add(
                        new Sample(
                            sentAt,
                            latency,
                            Protocol.decodePartitionId(result.getProcessInstanceKey()),
                            ""));
                  } else {
                    samples.add(new Sample(sentAt, latency, 0, describe(error)));
                  }
                  inFlight.decrementAndGet();
                });
      } catch (final RuntimeException e) {
        samples.add(new Sample(sentAt, 0, 0, describe(e)));
        inFlight.decrementAndGet();
      }
    }

    List<JobSample> jobs() {
      synchronized (jobs) {
        return List.copyOf(jobs);
      }
    }

    private static String describe(final Throwable error) {
      final var cause = error.getCause() != null ? error.getCause() : error;
      final var firstLine = String.valueOf(cause.getMessage()).lines().findFirst().orElse("");
      return (cause.getClass().getSimpleName()
              + ": "
              + firstLine.substring(0, Math.min(100, firstLine.length())))
          .replace(',', ';');
    }

    @Override
    public void close() {
      running.set(false);
      schedule.shutdownNow();
      Awaitility.await("every created instance completed or failed")
          .atMost(REQUEST_TIMEOUT.plusSeconds(10))
          .until(() -> inFlight.get() == 0);
      workers.forEach(JobWorker::close);
      workerClients.forEach(CamundaClient::close);
      clients.forEach(CamundaClient::close);
    }
  }

  /**
   * Records the handover trace lines of every broker in this JVM, with the broker they came from.
   */
  private static final class TraceCapture extends AbstractAppender implements AutoCloseable {
    private final ConcurrentLinkedQueue<String[]> lines = new ConcurrentLinkedQueue<>();
    private final Map<String, Level> previousLevels = new HashMap<>();

    private TraceCapture() {
      super("handover-phases-" + UUID.randomUUID(), null, null, true, Property.EMPTY_ARRAY);
    }

    static TraceCapture capturing() {
      final var capture = new TraceCapture();
      capture.start();
      for (final var logger : QUIET_LOGGERS) {
        capture.previousLevels.put(logger, LogManager.getLogger(logger).getLevel());
        Configurator.setLevel(logger, Level.INFO);
      }
      Configurator.setLevel(HandoverTrace.LOGGER_NAME, Level.INFO);
      for (final var logger : DEBUG_LOGGERS) {
        capture.previousLevels.put(logger, LogManager.getLogger(logger).getLevel());
        Configurator.setLevel(logger, Level.DEBUG);
      }
      final var context = (LoggerContext) LogManager.getContext(false);
      context
          .getConfiguration()
          .getLoggerConfig(HandoverTrace.LOGGER_NAME)
          .addAppender(capture, Level.INFO, null);
      context.updateLoggers();
      return capture;
    }

    @Override
    public void append(final LogEvent event) {
      final var scheduler = event.getContextData().<String>getValue("actor-scheduler");
      lines.add(
          new String[] {
            scheduler == null ? event.getThreadName() : scheduler,
            event.getMessage().getFormattedMessage()
          });
    }

    List<TraceLine> lines() {
      return lines.stream()
          .map(line -> TraceLine.parse(line[0], line[1]))
          .flatMap(Optional::stream)
          .sorted(Comparator.comparingLong(TraceLine::atMs))
          .toList();
    }

    @Override
    public void close() {
      final var context = (LoggerContext) LogManager.getContext(false);
      context
          .getConfiguration()
          .getLoggerConfig(HandoverTrace.LOGGER_NAME)
          .removeAppender(getName());
      previousLevels.forEach(Configurator::setLevel);
      context.updateLoggers();
      stop();
    }
  }
}
