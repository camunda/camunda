/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.system.configuration.FailureDetectorCfg;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import io.camunda.zeebe.broker.system.syspartition.detector.ticktock.TickTockFailureDetector;
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
   * @param members the brokers running the system partition, all of which the detector observes
   * @param regionLayout the region of each member, empty for a cluster without regions
   */
  public static void install(
      final FailureDetectorCfg cfg,
      final SystemPartition systemPartition,
      final ClusterCommunicationService communicationService,
      final MemberId localMemberId,
      final Set<MemberId> members,
      final RegionLayout regionLayout,
      final MeterRegistry meterRegistry) {
    switch (cfg.getType()) {
      case NONE -> {}
      case TICK_TOCK -> {
        final var detector =
            new TickTockFailureDetector(
                systemPartition,
                communicationService,
                localMemberId.id(),
                members.stream().map(MemberId::id).collect(Collectors.toUnmodifiableSet()),
                regionLayout,
                cfg.getTickTock(),
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
