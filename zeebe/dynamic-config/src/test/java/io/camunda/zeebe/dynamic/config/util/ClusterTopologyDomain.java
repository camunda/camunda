/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.util.ReflectUtil;
import java.util.SortedSet;
import java.util.TreeSet;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Provide;
import net.jqwik.api.domains.DomainContextBase;

/**
 * Contains all arbitraries needed to generate a {@link ClusterConfiguration}. The topology is not
 * semantically correct (e.g. contains operations for members that don't exist) but all fields
 * should have valid values.
 */
public final class ClusterTopologyDomain extends DomainContextBase {

  @Provide
  Arbitrary<ClusterConfiguration> clusterTopologies() {
    // Combine arbitraries (instead of just using `Arbitraries.forType(ClusterTopology.class)`
    // here so that we have control over the version. Version must be greater than 0 for
    // `ClusterTopology#isUninitialized` to return false.
    final var arbitraryVersion = Arbitraries.integers().greaterOrEqual(0);
    final var arbitraryMembers =
        Arbitraries.maps(memberIds(), Arbitraries.forType(MemberState.class).enableRecursion())
            .ofMaxSize(10);
    final var arbitraryCompletedChange =
        Arbitraries.forType(CompletedChange.class).enableRecursion().optional();
    final var arbitraryChangePlan =
        Arbitraries.forType(ClusterChangePlan.class).enableRecursion().optional();
    final var arbitraryRoutingState = routingStates().optional();
    final var arbitraryClusterId = Arbitraries.strings().ofMinLength(1).ofMaxLength(50).optional();
    return Combinators.combine(
            arbitraryVersion,
            arbitraryMembers,
            arbitraryCompletedChange,
            arbitraryChangePlan,
            arbitraryRoutingState,
            arbitraryClusterId)
        .as(ClusterConfiguration::new);
  }

  @Provide
  Arbitrary<RoutingState> routingStates() {
    final var version = Arbitraries.longs().greaterOrEqual(0);
    return Combinators.combine(version, requestHandling(), messageCorrelation())
        .as(RoutingState::new);
  }

  @Provide
  Arbitrary<RequestHandling> requestHandling() {
    return Arbitraries.of(
            ReflectUtil.implementationsOfSealedInterface(RequestHandling.class).toList())
        .flatMap(Arbitraries::forType);
  }

  @Provide
  Arbitrary<AllPartitions> allPartitions() {
    return Arbitraries.integers().between(1, 5).map(AllPartitions::new);
  }

  @Provide
  Arbitrary<ActivePartitions> activePartitions() {
    final var basePartitionCount = Arbitraries.integers().between(1, 3);
    final var activePartitions = Arbitraries.integers().between(4, 8).set();
    final var inactivePartitions = Arbitraries.integers().between(9, 12).set();

    return Combinators.combine(basePartitionCount, activePartitions, inactivePartitions)
        .as(ActivePartitions::new);
  }

  @Provide
  Arbitrary<MessageCorrelation> messageCorrelation() {
    return Arbitraries.of(
            ReflectUtil.implementationsOfSealedInterface(MessageCorrelation.class).toList())
        .flatMap(Arbitraries::forType);
  }

  @Provide
  Arbitrary<ClusterConfigurationChangeOperation> topologyChangeOperations() {
    // jqwik does not support sealed classes yet, so we have to use reflection to get all possible
    // types. See https://github.com/jqwik-team/jqwik/issues/523
    return Arbitraries.of(
            ReflectUtil.implementationsOfSealedInterface(ClusterConfigurationChangeOperation.class)
                .toList())
        .flatMap(Arbitraries::forType);
  }

  @Provide
  Arbitrary<SortedSet<Integer>> sortedIntegerSets() {
    return Arbitraries.integers().list().map(TreeSet::new);
  }

  @Provide
  Arbitrary<MemberId> memberIds() {
    return Arbitraries.integers().greaterOrEqual(0).map(id -> MemberId.from(id.toString()));
  }

  @Provide
  Arbitrary<ExportersConfig> exportersConfigs() {
    return Arbitraries.forType(ExportersConfig.class).enableRecursion();
  }

  @Provide
  Arbitrary<DynamicPartitionConfig> dynamicPartitionConfigs() {
    return Arbitraries.forType(ExportersConfig.class)
        .enableRecursion()
        .map(DynamicPartitionConfig::new)
        .filter(DynamicPartitionConfig::isInitialized);
  }
}
