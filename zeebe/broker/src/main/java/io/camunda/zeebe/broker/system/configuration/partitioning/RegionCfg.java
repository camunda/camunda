/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.partitioning;

/**
 * Per-region configuration for the {@link Scheme#REGION_AWARE} partitioning scheme.
 *
 * <p>{@link #numberOfBrokers} brokers must be deployed in this region, each configured with {@code
 * zeebe.broker.cluster.region} set to this region's name. The {@link #nodeId} of each broker must
 * be unique within the region (0-indexed, i.e. in the range {@code [0, numberOfBrokers)}).
 *
 * <p>{@link #numberOfReplicas} controls how many replicas of each partition are placed in this
 * region. It must satisfy {@code numberOfReplicas <= numberOfBrokers}, because two replicas of the
 * same partition cannot reside on the same broker.
 *
 * <p>{@link #priority} determines the preferred leader location. The region with the highest
 * priority will have its brokers assigned the highest Raft election priorities, so they win leader
 * elections first. If all brokers in the highest-priority region are unavailable, leadership fails
 * over to the next region automatically via Raft's priority-decrement mechanism.
 */
public final class RegionCfg {

  private String name;
  private int numberOfReplicas;
  private int numberOfBrokers;
  private int priority;

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public int getNumberOfReplicas() {
    return numberOfReplicas;
  }

  public void setNumberOfReplicas(final int numberOfReplicas) {
    this.numberOfReplicas = numberOfReplicas;
  }

  public int getNumberOfBrokers() {
    return numberOfBrokers;
  }

  public void setNumberOfBrokers(final int numberOfBrokers) {
    this.numberOfBrokers = numberOfBrokers;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(final int priority) {
    this.priority = priority;
  }

  @Override
  public String toString() {
    return "RegionCfg{"
        + "name='"
        + name
        + '\''
        + ", numberOfReplicas="
        + numberOfReplicas
        + ", numberOfBrokers="
        + numberOfBrokers
        + ", priority="
        + priority
        + '}';
  }
}
