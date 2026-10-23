/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol.automatic;

import java.util.List;

/**
 * The configured physical tenants of a cluster, as far as the automatic write rate limit is
 * concerned. Leadership is deliberately not part of it: the start value and the floor must not move
 * when leadership moves.
 */
public record WriteRateLimitTopology(int brokerCount, List<Tenant> tenants) {

  public WriteRateLimitTopology {
    if (brokerCount <= 0) {
      throw new IllegalArgumentException("brokerCount must be greater than 0");
    }
    tenants = List.copyOf(tenants);
  }

  /** Replicas hosted by one broker, assuming an even distribution over all brokers. */
  public double replicasPerBroker() {
    return totalReplicas() / brokerCount;
  }

  /** Mean weight over all replicas, i.e. weighted by partitions × replication factor. */
  public double meanWeight() {
    return tenants.stream().mapToDouble(t -> t.replicas() * t.weight()).sum() / totalReplicas();
  }

  /**
   * Share of its replicas that a broker leads when one broker is down and the remaining brokers
   * share its leaders evenly. With a single broker this is 1.
   */
  public double leaderShareWithOneBrokerDown() {
    if (brokerCount == 1) {
      return 1;
    }
    final double partitions = tenants.stream().mapToInt(Tenant::partitions).sum();
    final double leadersPerBroker = partitions / (brokerCount - 1);
    return Math.min(1, leadersPerBroker / replicasPerBroker());
  }

  private double totalReplicas() {
    return tenants.stream().mapToDouble(Tenant::replicas).sum();
  }

  public record Tenant(String id, int partitions, int replicationFactor, double weight) {

    public Tenant(final String id, final int partitions, final int replicationFactor) {
      this(id, partitions, replicationFactor, 1);
    }

    double replicas() {
      return (double) partitions * replicationFactor;
    }
  }
}
