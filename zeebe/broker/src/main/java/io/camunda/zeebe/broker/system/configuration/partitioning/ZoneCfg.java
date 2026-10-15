/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.partitioning;

/**
 * Per-region configuration for the {@link Scheme#ZONE_AWARE} partitioning scheme.
 *
 * <p>{@link #numberOfBrokers} brokers must be deployed in this region, each configured with {@code
 * zeebe.broker.cluster.region} set to this region's name.
 *
 * <p>{@link #numberOfReplicas} controls how many replicas of each partition are placed in this
 * region. It must satisfy {@code numberOfReplicas <= numberOfBrokers}, because two replicas of the
 * same partition cannot reside on the same broker.
 *
 * <p>{@link #priority} determines the preferred leader location. The region with the highest
 * priority will have its brokers assigned the highest Raft election priorities, so they win leader
 * elections first. If all brokers in the highest-priority region are unavailable, leadership fails
 * over to the next region automatically via Raft's priority-decrement mechanism.
 *
 * <p>{@link #role} marks a {@link ZoneRole#CONTROL control} zone, whose brokers host no data
 * partitions and only run the system partition; it has no replicas.
 */
public record ZoneCfg(
    String name, int numberOfBrokers, int numberOfReplicas, int priority, ZoneRole role) {

  /** A data zone. */
  public ZoneCfg(
      final String name,
      final int numberOfBrokers,
      final int numberOfReplicas,
      final int priority) {
    this(name, numberOfBrokers, numberOfReplicas, priority, ZoneRole.DATA);
  }

  public boolean isControl() {
    return role == ZoneRole.CONTROL;
  }
}
