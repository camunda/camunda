/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import java.util.List;
import java.util.Map;

public final class PhysicalTenantFixtures {

  public static final String TENANT_A = "tenant-a";

  private PhysicalTenantFixtures() {}

  /**
   * The given single-group topology as a multi-group configuration whose two partition groups are
   * mirror images: the default tenant and {@link #TENANT_A} run the same partitions over the same
   * brokers. The input's single group is renamed to the default group in the process — a group's
   * name is irrelevant to the operations these fixtures plan, and the mirrored shape is what the
   * tests assert on.
   *
   * <p>Mirroring rather than varying the two is what makes a plan easy to read: whatever a request
   * does to the default tenant it must do to the other one as well, so a plan that only ever saw
   * the default group is distinguishable by the group it is missing, not by a placement difference
   * that has to be derived.
   */
  public static CurrentClusterConfiguration withMirroredTenant(
      final CurrentClusterConfiguration topology) {
    if (topology.partitionGroups().size() != 1) {
      throw new IllegalArgumentException(
          "Expected a topology with exactly one partition group, but got "
              + topology.partitionGroups().keySet());
    }
    final var singleGroup = topology.partitionGroups().values().iterator().next();
    return new CurrentClusterConfiguration(
        topology.version(),
        topology.globalConfiguration(),
        Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, singleGroup, TENANT_A, singleGroup),
        topology.phasedChangeState());
  }

  /**
   * The phase of a plan that carries the per-tenant work, which is where a plan that only ever saw
   * the default partition group differs from one that saw every tenant's.
   */
  public static PartitionGroupPhase partitionGroupPhase(final List<Phase> phases) {
    return phases.stream()
        .filter(PartitionGroupPhase.class::isInstance)
        .map(PartitionGroupPhase.class::cast)
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("expected the plan to contain partition work: " + phases));
  }
}
