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
import io.atomix.raft.RaftServer.Role;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.util.health.HealthStatus;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rolls changes of one physical tenant out to three brokers, replication factor 3, while the tenant
 * takes continuous load through the gateway of every broker, and watches the replicas of the
 * tenant's partition on every broker during each rollout.
 *
 * <p>A replica counts as healthy while it is leader or follower and its partition reports itself
 * healthy. With fewer than two healthy replicas the partition has no quorum and cannot commit.
 */
final class PhysicalTenantRolloutReadinessIT {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantRolloutReadinessIT.class);

  private static final String BOOT_TENANT = "boottenant";
  private static final Duration TIMEOUT = Duration.ofMinutes(2);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int QUORUM = 2;
  private static final int ROLLOUTS = 3;

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(BOOT_TENANT, Storage.none())
          .build();

  @Test
  void shouldKeepAQuorumOfTheChangedTenantsPartitionUnderLoad() throws Exception {
    // given
    try (final var cluster = startCluster("SERVING")) {
      final var tenantId = uniqueTenantId();

      // when
      final var rollouts = rollOutUnderLoad(cluster, tenantId, "SERVING");

      // then
      assertThat(rollouts)
          .hasSize(ROLLOUTS)
          .allSatisfy(
              rollout -> {
                assertThat(rollout.minHealthyReplicas()).isGreaterThanOrEqualTo(QUORUM);
                assertThat(rollout.belowQuorum()).isZero();
              });
    }
  }

  @Test
  void shouldLoseTheQuorumWhenTheRolloutDoesNotWait() throws Exception {
    // given
    try (final var cluster = startCluster("NONE")) {
      final var tenantId = uniqueTenantId();

      // when
      final var rollouts = rollOutUnderLoad(cluster, tenantId, "NONE");

      // then
      assertThat(rollouts).hasSize(ROLLOUTS);
      assertThat(rollouts)
          .anySatisfy(rollout -> assertThat(rollout.minHealthyReplicas()).isLessThan(QUORUM));
    }
  }

  @Test
  void shouldMeasureARolloutThatOnlyWaitsForInstalledPartitions() throws Exception {
    // given
    try (final var cluster = startCluster("INSTALLED")) {
      final var tenantId = uniqueTenantId();

      // when
      final var rollouts = rollOutUnderLoad(cluster, tenantId, "INSTALLED");

      // then
      assertThat(rollouts).hasSize(ROLLOUTS);
    }
  }

  private static TestCluster startCluster(final String readiness) {
    final var cluster =
        TestCluster.builder()
            .withBrokersCount(3)
            .withReplicationFactor(3)
            .withPartitionsCount(1)
            .withBrokerConfig(
                broker ->
                    TENANTS
                        .configure(broker.withUnauthenticatedAccess())
                        .withProperty("physical-tenant-rollout.readiness", readiness))
            .build();
    cluster.start().awaitCompleteTopology();
    return cluster;
  }

  private static List<Rollout> rollOutUnderLoad(
      final TestCluster cluster, final String tenantId, final String readiness) throws Exception {
    Awaitility.await("the cluster accepts the creation of " + tenantId)
        .atMost(TIMEOUT)
        .until(
            () ->
                send(cluster, "POST", tenantId, tenantConfiguration(cluster, 1)).statusCode()
                    == 202);
    awaitRolledOut(cluster, tenantId, 1);
    final var clients = new ArrayList<CamundaClient>();
    cluster
        .brokers()
        .values()
        .forEach(
            broker ->
                clients.add(
                    TENANTS.newClientBuilder(broker, tenantId).preferRestOverGrpc(false).build()));
    final var load = new PublishLoad(clients);
    final var sampler = new QuorumSampler(cluster, tenantId);
    final var threads = new ArrayList<Thread>();
    clients.forEach(client -> threads.add(Thread.ofVirtual().start(() -> load.run(client))));
    final var samplerThread = Thread.ofVirtual().start(sampler);
    try {
      awaitServing(cluster, tenantId, sampler, load);
      final var rollouts = new ArrayList<Rollout>();
      for (var version = 2; version <= ROLLOUTS + 1; version++) {
        sampler.reset();
        load.reset();
        final var started = System.nanoTime();
        assertThat(
                send(cluster, "PUT", tenantId, tenantConfiguration(cluster, version)).statusCode())
            .isEqualTo(202);
        awaitRolledOut(cluster, tenantId, version);
        final var rolledOut = Duration.ofNanos(System.nanoTime() - started);
        awaitServing(cluster, tenantId, sampler, load);
        final var rollout =
            new Rollout(
                readiness,
                version,
                rolledOut,
                load.longestGap(),
                load.failed(),
                load.succeeded(),
                sampler.minHealthy(),
                sampler.belowQuorum());
        LOG.info("Rollout under load: {}", rollout);
        rollouts.add(rollout);
      }
      return rollouts;
    } finally {
      load.stop();
      sampler.stop();
      for (final var thread : threads) {
        thread.join();
      }
      samplerThread.join();
      clients.forEach(CamundaClient::close);
    }
  }

  private static void awaitServing(
      final TestCluster cluster,
      final String tenantId,
      final QuorumSampler sampler,
      final PublishLoad load) {
    Awaitility.await("every replica of " + tenantId + " is healthy")
        .atMost(TIMEOUT)
        .until(() -> sampler.healthyNow() == cluster.brokers().size());
    final var before = load.succeeded();
    Awaitility.await(tenantId + " accepts commands")
        .atMost(TIMEOUT)
        .until(() -> load.succeeded() > before + 20);
  }

  private static void awaitRolledOut(
      final TestCluster cluster, final String tenantId, final long version) {
    Awaitility.await("version %d of %s is applied by every broker".formatted(version, tenantId))
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              final var status = status(cluster, tenantId);
              assertThat(status.get("version").asLong()).isEqualTo(version);
              assertThat(status.get("rolledOut").asBoolean()).isTrue();
              assertThat(status.get("changing").asBoolean()).isFalse();
            });
  }

  private static JsonNode status(final TestCluster cluster, final String tenantId) {
    final var response = send(cluster, "GET", tenantId, null);
    assertThat(response.statusCode()).isEqualTo(200);
    try {
      return JSON.readTree(response.body());
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static Map<String, String> tenantConfiguration(
      final TestCluster cluster, final int version) {
    final Map<String, String> configuration = new HashMap<>();
    configuration.put("name", "Rolled out");
    configuration.put("data.secondary-storage.type", "none");
    configuration.put("data.snapshot-period", version + "m");
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

  private static HttpResponse<String> send(
      final TestCluster cluster,
      final String method,
      final String tenantId,
      final Map<String, String> body) {
    try (final var http = HttpClient.newHttpClient()) {
      final var request =
          HttpRequest.newBuilder(broker(cluster, 0).actuatorUri("physicalTenants", tenantId))
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

  private static String uniqueTenantId() {
    return "rr" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  /**
   * One rollout of a change to every broker.
   *
   * @param longestGap the longest time in which no command of the tenant was accepted through any
   *     broker
   * @param minHealthyReplicas the fewest healthy replicas of the tenant's partition seen at once
   * @param belowQuorum how long the partition had fewer than a quorum of healthy replicas
   */
  private record Rollout(
      String readiness,
      long version,
      Duration rolledOut,
      Duration longestGap,
      int failed,
      int succeeded,
      int minHealthyReplicas,
      Duration belowQuorum) {}

  /** Publishes messages to the tenant through the gateway of every broker, without pause. */
  private static final class PublishLoad {
    private final List<CamundaClient> clients;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger succeeded = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private long lastSuccessNanos = System.nanoTime();
    private long longestGapNanos;

    private PublishLoad(final List<CamundaClient> clients) {
      this.clients = clients;
    }

    private void run(final CamundaClient client) {
      while (running.get()) {
        try {
          client
              .newPublishMessageCommand()
              .messageName("measure")
              .correlationKey("key")
              .send()
              .join(5, TimeUnit.SECONDS);
          succeeded.incrementAndGet();
          accepted();
        } catch (final RuntimeException e) {
          failed.incrementAndGet();
        }
      }
    }

    private synchronized void accepted() {
      final var now = System.nanoTime();
      longestGapNanos = Math.max(longestGapNanos, now - lastSuccessNanos);
      lastSuccessNanos = now;
    }

    private synchronized void reset() {
      succeeded.set(0);
      failed.set(0);
      longestGapNanos = 0;
      lastSuccessNanos = System.nanoTime();
    }

    private void stop() {
      running.set(false);
    }

    private int succeeded() {
      return succeeded.get();
    }

    private int failed() {
      return failed.get();
    }

    private synchronized Duration longestGap() {
      return Duration.ofNanos(longestGapNanos);
    }

    @Override
    public String toString() {
      return "%d clients".formatted(clients.size());
    }
  }

  /**
   * Counts, every few milliseconds, the healthy replicas of the tenant's partition on every broker,
   * and keeps the fewest seen and the time spent below a quorum.
   */
  private static final class QuorumSampler implements Runnable {
    private static final Duration INTERVAL = Duration.ofMillis(10);

    private final TestCluster cluster;
    private final String tenantId;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile int healthyNow;
    private int minHealthy = Integer.MAX_VALUE;
    private long belowQuorumNanos;
    private long lastSampleNanos;

    private QuorumSampler(final TestCluster cluster, final String tenantId) {
      this.cluster = cluster;
      this.tenantId = tenantId;
    }

    @Override
    public void run() {
      while (running.get()) {
        final var healthy =
            (int) cluster.brokers().values().stream().filter(this::hasHealthyReplica).count();
        sample(healthy);
        Awaitility.await().pollDelay(INTERVAL).atMost(INTERVAL.multipliedBy(10)).until(() -> true);
      }
    }

    private synchronized void sample(final int healthy) {
      final var now = System.nanoTime();
      if (lastSampleNanos != 0 && healthy < QUORUM) {
        belowQuorumNanos += now - lastSampleNanos;
      }
      lastSampleNanos = now;
      healthyNow = healthy;
      minHealthy = Math.min(minHealthy, healthy);
    }

    private boolean hasHealthyReplica(final TestStandaloneBroker broker) {
      final var context = broker.bean(Broker.class).getBrokerContext();
      final var partitions = context == null ? null : context.getPartitionManagers().get(tenantId);
      if (partitions == null) {
        return false;
      }
      final var raft = partitions.getRaftPartitions();
      final var zeebe = partitions.getZeebePartitions();
      return !raft.isEmpty()
          && !zeebe.isEmpty()
          && raft.stream()
              .allMatch(
                  partition ->
                      partition.getRole() == Role.LEADER || partition.getRole() == Role.FOLLOWER)
          && zeebe.stream()
              .allMatch(
                  partition -> partition.getHealthReport().getStatus() == HealthStatus.HEALTHY);
    }

    private synchronized void reset() {
      minHealthy = Integer.MAX_VALUE;
      belowQuorumNanos = 0;
      lastSampleNanos = 0;
    }

    private int healthyNow() {
      return healthyNow;
    }

    private synchronized int minHealthy() {
      return minHealthy;
    }

    private synchronized Duration belowQuorum() {
      return Duration.ofNanos(belowQuorumNanos);
    }

    private void stop() {
      running.set(false);
    }
  }
}
