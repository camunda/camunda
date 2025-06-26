/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes the routing state of the cluster configuration if the partition scaling feature is
 * enabled. All members will initialize the same routing state (as long as their statically
 * configured partition counts match).
 */
public class RoutingStateInitializer implements ClusterConfigurationModifier {
  private static final Logger LOG = LoggerFactory.getLogger(RoutingStateInitializer.class);

  private final int staticPartitionCount;
  private final Optional<Path> routingStatePath;
  private final ProtoBufSerializer serializer;

  public RoutingStateInitializer(
      final int staticPartitionCount, final Optional<Path> routingStatePath) {
    this.routingStatePath = routingStatePath;
    this.staticPartitionCount = staticPartitionCount;
    serializer = new ProtoBufSerializer();
  }

  @Override
  public ActorFuture<ClusterConfiguration> modify(final ClusterConfiguration configuration) {
    RoutingState routingState = null;
    AwaitRedistributionCompletion operation = null;
    if (routingStatePath.isPresent() && Files.exists(routingStatePath.get())) {
      final byte[] routingStateContent;
      try {
        routingStateContent = Files.readAllBytes(routingStatePath.get());
        routingState =
            serializer.deserializeRoutingState(routingStateContent, 0, routingStateContent.length);
        operation =
            switch (routingState.requestHandling()) {
              case final RequestHandling.ActivePartitions activePartitions ->
                  new AwaitRedistributionCompletion(
                      MemberId.from("0"),
                      activePartitions.basePartitionCount()
                          + activePartitions.additionalActivePartitions().size()
                          + activePartitions.inactivePartitions().size(),
                      new TreeSet<>(activePartitions.inactivePartitions()));
              case final RequestHandling.AllPartitions allPartitions ->
                  // no need to add an operation
                  null;
              default ->
                  throw new IllegalStateException(
                      "Unexpected request handling type: " + routingState.requestHandling());
            };
        LOG.debug(
            "Initialized routing state from file '{}': {}", routingStatePath.get(), routingState);
      } catch (final IOException e) {
        LOG.warn(
            "Failed to read routing state from file '{}'. Initializing with static partition count.",
            routingStatePath.get());
      }
    }
    if (routingState == null) {
      LOG.debug("Initializing routing state with static partition count: {}", staticPartitionCount);
      routingState = RoutingState.initializeWithPartitionCount(staticPartitionCount);
    }
    var pendingChanges = configuration.pendingChanges();
    if (pendingChanges.isEmpty() && operation != null) {
      pendingChanges = Optional.of(ClusterChangePlan.init(1L, List.of(operation)));
    }
    final var withRoutingState =
        new ClusterConfiguration(
            configuration.version(),
            configuration.members(),
            configuration.lastChange(),
            pendingChanges,
            Optional.of(routingState));
    return CompletableActorFuture.completed(withRoutingState);
  }
}
