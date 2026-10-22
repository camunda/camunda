/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.system.configuration.FailureDetectorCfg;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import io.camunda.zeebe.broker.system.syspartition.detector.swim.SwimFailureDetector;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import java.util.stream.Collectors;

/** Installs the configured failure detector, if any, on the system partition. */
public final class FailureDetectorFactory {

  private FailureDetectorFactory() {}

  /**
   * Installs the configured failure detector on the system partition, which starts it with the
   * partition. Must be called before the partition starts.
   *
   * @param members the brokers running the system partition, which report to each other
   * @param regionLayout the region of each broker, empty for a cluster without regions
   */
  public static void install(
      final FailureDetectorCfg cfg,
      final SystemPartition systemPartition,
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService communicationService,
      final MemberId localMemberId,
      final Set<MemberId> members,
      final RegionLayout regionLayout,
      final MeterRegistry meterRegistry) {
    switch (cfg.getType()) {
      case NONE -> {}
      case SWIM -> {
        final var memberIds =
            members.stream().map(MemberId::id).collect(Collectors.toUnmodifiableSet());
        // SWIM gossips about every broker, not only those running the system partition
        final var brokers = regionLayout.isEmpty() ? memberIds : regionLayout.allBrokers();
        final var detector =
            new SwimFailureDetector(
                systemPartition,
                membershipService,
                communicationService,
                localMemberId.id(),
                memberIds,
                brokers,
                regionLayout,
                cfg.getSwim(),
                meterRegistry);
        systemPartition.addComponent(detector);
        systemPartition.addListener(detector);
      }
      default ->
          throw new IllegalArgumentException(
              "Expected a known failure detector type, but got %s".formatted(cfg.getType()));
    }
  }
}
