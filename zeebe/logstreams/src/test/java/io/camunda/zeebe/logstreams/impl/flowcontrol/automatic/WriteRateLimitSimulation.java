/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol.automatic;

import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.limit.WindowedLimit;
import io.camunda.zeebe.logstreams.impl.flowcontrol.StabilizingAIMDLimit;
import io.camunda.zeebe.logstreams.impl.flowcontrol.automatic.AutomaticWriteRateController.Decision;
import io.camunda.zeebe.logstreams.impl.flowcontrol.automatic.AutomaticWriteRateController.PartitionObservation;
import io.camunda.zeebe.logstreams.impl.flowcontrol.automatic.AutomaticWriteRateController.WindowObservation;
import io.camunda.zeebe.logstreams.impl.flowcontrol.automatic.WriteRateLimitTopology.Tenant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * Discrete-time simulation of a cluster of brokers, each running its own {@link
 * AutomaticWriteRateController}.
 *
 * <p>Model, per tick of {@link #TICK} (one CFS period):
 *
 * <ul>
 *   <li>Every tenant distributes its partitions round robin, starting at broker 0, with the
 *       priorities of {@code PartitionPriorityAssigner}. The leader is the replica with the highest
 *       priority on a broker that is up (priority election plus automatic rebalancing).
 *   <li>The leader admits {@code min(offered, limit × tick)} records. When the exporter backlog is
 *       above {@link #ACCEPTABLE_EXPORT_BACKLOG}, the exporter throttle caps admission at the
 *       export rate and the partition is excluded. A rejection while not throttled calls {@link
 *       AutomaticWriteRateController#onRejected(long)}.
 *   <li>Unless disabled, every leader has a request limiter as configured by default in the broker:
 *       {@code WindowedLimit} over {@code StabilizingAIMDLimit} with an expected RTT of 200 ms,
 *       initial limit 100, min 1, max 1000, backoff 0.9. It is checked before the write rate limit
 *       and caps the in-flight commands of the leader (every record is a user command, no
 *       follow-ups). The RTT is {@link #BASE_LATENCY} plus the backlog divided by the processing
 *       rate of the leader. A queued command holds its slot; a free slot serves one command per
 *       {@link #BASE_LATENCY}. A new leader starts with a new request limiter.
 *   <li>Admitted records queue up on the leader and on every follower that is up.
 *   <li>Processing one record costs {@code LEADER / trueR × tenantCost} CPU-seconds on a leader
 *       (exporting included) and {@code FOLLOWER / trueR × tenantCost} on a follower. A replica
 *       uses at most one core. When the demand of a broker exceeds its CPU limit, every replica
 *       gets a proportional share and the tick counts as throttled.
 *   <li>A broker that is down loses its queues and receives nothing. It restarts with a new
 *       controller, at the start value.
 * </ul>
 */
final class WriteRateLimitSimulation {

  static final Duration TICK = Duration.ofMillis(100);

  /**
   * Offered rate of a flooding partition, as a multiple of the real records/CPU-s of its leader.
   */
  static final double FLOOD_FACTOR = 2;

  static final long ACCEPTABLE_EXPORT_BACKLOG = 10_000;
  static final Duration BASE_LATENCY = Duration.ofMillis(5);
  private static final Duration EXPECTED_RTT = Duration.ofMillis(200);
  private static final double EPSILON = 1e-9;

  private final AutomaticWriteRateLimitCfg cfg;
  private final int ticksPerWindow;
  private final List<Broker> brokers = new ArrayList<>();
  private final Map<String, SimTenant> tenants = new LinkedHashMap<>();
  private final List<WindowSample> samples = new ArrayList<>();
  private long tick;
  private boolean requestLimiterEnabled = true;
  private boolean writeRateLimitEnabled = true;

  WriteRateLimitSimulation(
      final AutomaticWriteRateLimitCfg cfg, final int brokerCount, final double cpuLimit) {
    this.cfg = cfg;
    ticksPerWindow = (int) (cfg.window().toMillis() / TICK.toMillis());
    for (int i = 0; i < brokerCount; i++) {
      brokers.add(new Broker(i, cpuLimit, cfg.recordsPerCpuSecond()));
    }
    final var topology = topology();
    brokers.forEach(b -> b.controller = b.newController(topology));
  }

  // ---------------------------------------------------------------- scenario setup

  SimTenant addTenant(final Tenant spec) {
    final var tenant = new SimTenant(spec);
    final var brokerCount = brokers.size();
    for (int p = 0; p < spec.partitions(); p++) {
      // same as RoundRobinPartitionDistributor + PartitionPriorityAssigner: the secondaries'
      // priority order flips every round of brokerCount partitions
      final var replicas = new int[spec.replicationFactor()];
      final var evenRound = p / brokerCount % 2 == 0;
      replicas[0] = p % brokerCount;
      for (int r = 1; r < replicas.length; r++) {
        final var offset = evenRound ? r : replicas.length - r;
        replicas[r] = (p + offset) % brokerCount;
      }
      tenant.partitions.add(new SimPartition(tenant, p + 1, replicas));
    }
    tenants.put(spec.id(), tenant);
    final var topology = topology();
    brokers.forEach(b -> b.onTopologyChanged(topology));
    return tenant;
  }

  SimTenant tenant(final String id) {
    return tenants.get(id);
  }

  Broker broker(final int id) {
    return brokers.get(id);
  }

  List<Broker> brokers() {
    return brokers;
  }

  WriteRateLimitTopology topology() {
    return new WriteRateLimitTopology(
        brokers.size(), tenants.values().stream().map(t -> t.spec).toList());
  }

  /** Only the request limiter applies: shows what tenants get from backpressure alone. */
  WriteRateLimitSimulation withoutWriteRateLimit() {
    writeRateLimitEnabled = false;
    return this;
  }

  WriteRateLimitSimulation withoutRequestLimiter() {
    requestLimiterEnabled = false;
    return this;
  }

  void stopBroker(final int id) {
    final var broker = brokers.get(id);
    broker.up = false;
    tenants.values().stream()
        .flatMap(t -> t.partitions.stream())
        .forEach(p -> p.pending.remove(id));
  }

  void startBroker(final int id) {
    final var broker = brokers.get(id);
    broker.up = true;
    broker.controller = broker.newController(topology());
  }

  // ---------------------------------------------------------------- running

  void run(final Duration duration) {
    final long ticks = duration.toMillis() / TICK.toMillis();
    for (long i = 0; i < ticks; i++) {
      step();
    }
  }

  private void step() {
    // admission happens during the tick, the window is evaluated at its end
    final long tickStartMillis = tick * TICK.toMillis();
    tick++;
    final long nowMillis = tick * TICK.toMillis();
    final double dt = TICK.toMillis() / 1000.0;

    for (final var tenant : tenants.values()) {
      for (final var partition : tenant.partitions) {
        admit(partition, tickStartMillis, dt);
      }
    }
    for (final var broker : brokers) {
      if (broker.up) {
        process(broker, dt);
      }
    }
    if (requestLimiterEnabled) {
      sampleRequestLimiters(tickStartMillis, dt);
    }
    if (tick % ticksPerWindow == 0) {
      endWindow(nowMillis);
    }
  }

  private void admit(final SimPartition partition, final long nowMillis, final double dt) {
    final var leader = leaderOf(partition);
    if (leader == null) {
      return;
    }
    final var tenant = partition.tenant;
    final var excluded = partition.exportBacklog > ACCEPTABLE_EXPORT_BACKLOG;
    double limit =
        writeRateLimitEnabled
            ? leader.controller.limitFor(tenant.spec.weight()).orElse(Double.MAX_VALUE)
            : Double.MAX_VALUE;
    if (excluded) {
      limit = Math.min(limit, tenant.exportRatePerPartition);
    }
    final var offered = tenant.offeredRatePerPartition(leader) * dt;
    var admitted = offered;
    if (requestLimiterEnabled) {
      if (partition.requestLimitOwner != leader) {
        partition.requestLimitOwner = leader;
        partition.requestLimit = newRequestLimit();
      }
      // Little's law: a free slot serves dt / BASE_LATENCY commands per tick; queued commands
      // hold their slot
      final var queued = partition.pending.getOrDefault(leader.id, 0.0);
      final var freeSlots = Math.max(0, partition.requestLimit.getLimit() - queued);
      admitted = Math.min(offered, freeSlots * TICK.toNanos() / BASE_LATENCY.toNanos());
    }
    final var accepted = Math.min(admitted, limit * dt);
    if (admitted > accepted + EPSILON && !excluded) {
      leader.controller.onRejected(nowMillis);
    }

    partition.offeredInWindow += offered;
    partition.acceptedInWindow += accepted;
    partition.acceptedLastTick = accepted;
    tenant.acceptedInWindow += accepted;
    for (final int replica : partition.replicas) {
      if (brokers.get(replica).up) {
        partition.pending.merge(replica, accepted, Double::sum);
      }
    }
  }

  private void process(final Broker broker, final double dt) {
    final List<Work> work = new ArrayList<>();
    double demand = 0;
    for (final var tenant : tenants.values()) {
      for (final var partition : tenant.partitions) {
        final var pending = partition.pending.get(broker.id);
        if (pending == null) {
          continue;
        }
        final var isLeader = leaderOf(partition) == broker;
        final var relativeCost = isLeader ? cfg.leaderCost() : cfg.followerCost();
        final var costPerRecord = relativeCost * tenant.cost / broker.trueRecordsPerCpuSecond;
        // a replica is processed by a single thread
        final var cpu = Math.min(pending * costPerRecord, dt);
        work.add(new Work(partition, isLeader, costPerRecord, cpu));
        demand += cpu;
      }
    }

    final var capacity = broker.cpuLimit * dt;
    final var share = demand > capacity + EPSILON ? capacity / demand : 1;
    if (share < 1) {
      broker.throttledTicks++;
    }
    for (final var w : work) {
      final var processed = w.cpu * share / w.costPerRecord;
      w.partition.pending.merge(broker.id, -processed, Double::sum);
      if (w.isLeader) {
        final var p = w.partition;
        p.processedLastTick = processed;
        p.exportBacklog += processed;
        p.exportBacklog -= Math.min(p.exportBacklog, p.tenant.exportRatePerPartition * dt);
      }
    }
  }

  /** One sample per tick; the windowed limit updates the AIMD limit about once per second. */
  private void sampleRequestLimiters(final long tickStartMillis, final double dt) {
    final var startNanos = Duration.ofMillis(tickStartMillis).toNanos();
    for (final var tenant : tenants.values()) {
      for (final var partition : tenant.partitions) {
        final var leader = leaderOf(partition);
        if (leader == null || partition.requestLimit == null) {
          continue;
        }
        final var backlog = partition.pending.getOrDefault(leader.id, 0.0);
        final var throughput = partition.processedLastTick / dt;
        final double queueingSeconds =
            backlog <= EPSILON ? 0 : throughput > EPSILON ? backlog / throughput : 10;
        final var rtt = BASE_LATENCY.toNanos() + (long) (queueingSeconds * 1e9);
        final var inFlight =
            backlog + partition.acceptedLastTick / dt * BASE_LATENCY.toNanos() / 1e9;
        partition.requestLimit.onSample(startNanos, rtt, (int) Math.ceil(inFlight), false);
        partition.processedLastTick = 0;
      }
    }
  }

  private static Limit newRequestLimit() {
    return WindowedLimit.newBuilder()
        .build(
            StabilizingAIMDLimit.newBuilder()
                .expectedRTT(EXPECTED_RTT.toMillis(), TimeUnit.MILLISECONDS)
                .initialLimit(100)
                .minLimit(1)
                .maxLimit(1000)
                .backoffRatio(0.9)
                .build());
  }

  private void endWindow(final long nowMillis) {
    final var windowSeconds = cfg.window().toMillis() / 1000.0;
    final Map<String, Double> tenantRates = new LinkedHashMap<>();
    tenants.values().forEach(t -> tenantRates.put(t.spec.id(), t.acceptedInWindow / windowSeconds));

    for (final var broker : brokers) {
      if (!broker.up) {
        continue;
      }
      final List<PartitionObservation> leaders = new ArrayList<>();
      final Map<String, Double> partitionLimits = new HashMap<>();
      for (final var tenant : tenants.values()) {
        for (final var partition : tenant.partitions) {
          if (leaderOf(partition) != broker) {
            continue;
          }
          leaders.add(
              new PartitionObservation(
                  tenant.spec.id(),
                  partition.id,
                  partition.offeredInWindow > 0,
                  partition.exportBacklog > ACCEPTABLE_EXPORT_BACKLOG));
        }
        broker
            .controller
            .limitFor(tenant.spec.weight())
            .ifPresent(l -> partitionLimits.put(tenant.spec.id(), l));
      }
      final var throttledRatio = (double) broker.throttledTicks / ticksPerWindow;
      final var requestLimit =
          tenants.values().stream()
              .flatMap(t -> t.partitions.stream())
              .filter(p -> leaderOf(p) == broker && p.requestLimit != null)
              .mapToInt(p -> p.requestLimit.getLimit())
              .average()
              .orElse(0);
      final var backlog =
          tenants.values().stream()
              .flatMap(t -> t.partitions.stream())
              .filter(p -> leaderOf(p) == broker)
              .mapToLong(p -> p.backlog(broker.id))
              .sum();
      final var decision =
          broker.controller.onWindow(
              new WindowObservation(OptionalDouble.of(throttledRatio), leaders));
      samples.add(
          new WindowSample(
              nowMillis / 1000,
              broker.id,
              broker.controller.ceiling(),
              throttledRatio,
              decision,
              leaders.size(),
              backlog,
              requestLimit,
              partitionLimits,
              tenantRates));
      broker.throttledTicks = 0;
    }

    for (final var tenant : tenants.values()) {
      tenant.acceptedInWindow = 0;
      tenant.partitions.forEach(
          p -> {
            p.offeredInWindow = 0;
            p.acceptedInWindow = 0;
          });
    }
  }

  private Broker leaderOf(final SimPartition partition) {
    for (final int replica : partition.replicas) {
      final var broker = brokers.get(replica);
      if (broker.up) {
        return broker;
      }
    }
    return null;
  }

  // ---------------------------------------------------------------- results

  List<WindowSample> samples() {
    return samples;
  }

  List<WindowSample> samplesSince(final long fromSecond) {
    return samples.stream().filter(s -> s.second() > fromSecond).toList();
  }

  long now() {
    return tick * TICK.toMillis() / 1000;
  }

  /** Accepted records per second of a tenant, summed over its partitions, in the last window. */
  double lastAcceptedRate(final String tenantId) {
    return samples.getLast().tenantRates().get(tenantId);
  }

  String timeline() {
    final var sb = new StringBuilder();
    sb.append(
        String.format(
            "%6s %6s %9s %9s %-24s %7s %8s %7s  %s%n",
            "t[s]",
            "broker",
            "ceiling",
            "throttled",
            "decision",
            "leaders",
            "backlog",
            "reqLim",
            "tenant rates"));
    long lastSecond = -1;
    for (final var s : samples) {
      sb.append(
          String.format(
              "%6s %6d %9.1f %9.2f %-24s %7d %8d %7.0f  %s%n",
              s.second() == lastSecond ? "" : s.second(),
              s.brokerId(),
              s.ceiling(),
              s.throttledRatio(),
              s.decision(),
              s.leaders(),
              s.backlog(),
              s.requestLimit(),
              s.second() == lastSecond ? "" : formatRates(s.tenantRates())));
      lastSecond = s.second();
    }
    return sb.toString();
  }

  private static String formatRates(final Map<String, Double> rates) {
    final var sb = new StringBuilder();
    rates.forEach((id, rate) -> sb.append(String.format("%s=%.0f ", id, rate)));
    return sb.toString();
  }

  // ---------------------------------------------------------------- model types

  record WindowSample(
      long second,
      int brokerId,
      double ceiling,
      double throttledRatio,
      Decision decision,
      int leaders,
      long backlog,
      double requestLimit,
      Map<String, Double> partitionLimits,
      Map<String, Double> tenantRates) {}

  static final class SimTenant {
    final Tenant spec;
    final List<SimPartition> partitions = new ArrayList<>();
    @Nullable Double offeredRatePerPartition;
    double floodFactor = FLOOD_FACTOR;
    double cost = 1;
    double exportRatePerPartition = Double.MAX_VALUE;
    double acceptedInWindow;

    SimTenant(final Tenant spec) {
      this.spec = spec;
    }

    SimTenant offering(final double ratePerPartition) {
      offeredRatePerPartition = ratePerPartition;
      return this;
    }

    /** Sends {@code factor} × the real records/CPU-s of the leader, more than a partition does. */
    SimTenant flooding(final double factor) {
      offeredRatePerPartition = null;
      floodFactor = factor;
      return this;
    }

    double offeredRatePerPartition(final Broker leader) {
      return offeredRatePerPartition != null
          ? offeredRatePerPartition
          : floodFactor * leader.trueRecordsPerCpuSecond;
    }

    SimTenant withCost(final double cost) {
      this.cost = cost;
      return this;
    }

    SimTenant exportingAt(final double ratePerPartition) {
      exportRatePerPartition = ratePerPartition;
      return this;
    }
  }

  static final class SimPartition {
    final SimTenant tenant;
    final int id;
    final int[] replicas;
    final Map<Integer, Double> pending = new HashMap<>();
    @Nullable Limit requestLimit;
    @Nullable Broker requestLimitOwner;
    double processedLastTick;
    double acceptedLastTick;
    double exportBacklog;
    double offeredInWindow;
    double acceptedInWindow;

    SimPartition(final SimTenant tenant, final int id, final int[] replicas) {
      this.tenant = tenant;
      this.id = id;
      this.replicas = replicas;
    }

    long backlog(final int brokerId) {
      return Math.round(pending.getOrDefault(brokerId, 0.0));
    }
  }

  private record Work(SimPartition partition, boolean isLeader, double costPerRecord, double cpu) {}

  final class Broker {
    final int id;
    final double cpuLimit;
    double trueRecordsPerCpuSecond;
    boolean up = true;
    AutomaticWriteRateController controller;
    int throttledTicks;

    Broker(final int id, final double cpuLimit, final double trueRecordsPerCpuSecond) {
      this.id = id;
      this.cpuLimit = cpuLimit;
      this.trueRecordsPerCpuSecond = trueRecordsPerCpuSecond;
    }

    AutomaticWriteRateController newController(final WriteRateLimitTopology topology) {
      return new AutomaticWriteRateController(cfg, topology, OptionalDouble.of(cpuLimit));
    }

    void onTopologyChanged(final WriteRateLimitTopology topology) {
      controller.onTopologyChanged(topology);
    }

    /** The real records per CPU-second of the hardware, unknown to the controller. */
    Broker withTrueRecordsPerCpuSecond(final double value) {
      trueRecordsPerCpuSecond = value;
      return this;
    }
  }
}
