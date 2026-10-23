/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol.automatic;

/**
 * Floor and start value of the per-partition ceiling, derived from the topology and the CPU limit
 * of a broker. In the first version the cap is the start value.
 *
 * <pre>
 * cost per replica    = λ × LEADER + (1 − λ) × FOLLOWER
 * start per partition = R × min(1, cpu_limit / (replicas on the broker × cost per replica))
 * </pre>
 *
 * The floor uses {@code λ = 1} (the broker leads all its replicas), the start value uses the leader
 * share with one broker down.
 */
public record WriteRateLimitBounds(double floor, double start, double meanWeight) {

  public static WriteRateLimitBounds of(
      final WriteRateLimitTopology topology,
      final double cpuLimit,
      final AutomaticWriteRateLimitCfg cfg) {
    final var floor = perPartition(topology, cpuLimit, cfg, 1);
    final var start =
        perPartition(topology, cpuLimit, cfg, topology.leaderShareWithOneBrokerDown());
    return new WriteRateLimitBounds(floor, start, topology.meanWeight());
  }

  /** The cap of the controller; equal to the start value in the first version. */
  public double cap() {
    return start;
  }

  /** Per-partition limit of a tenant with the given weight: {@code ceiling × w_t / w̄}. */
  public double limitFor(final double ceiling, final double weight) {
    return ceiling * weight / meanWeight;
  }

  public double clamp(final double ceiling) {
    return Math.clamp(ceiling, floor, cap());
  }

  private static double perPartition(
      final WriteRateLimitTopology topology,
      final double cpuLimit,
      final AutomaticWriteRateLimitCfg cfg,
      final double lambda) {
    final var cpuNeeded = topology.replicasPerBroker() * cfg.costPerReplica(lambda);
    return cfg.recordsPerCpuSecond() * Math.min(1, cpuLimit / cpuNeeded);
  }
}
