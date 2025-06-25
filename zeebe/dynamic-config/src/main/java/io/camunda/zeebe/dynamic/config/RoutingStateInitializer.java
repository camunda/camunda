/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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
    if (configuration.routingState().isPresent()) {
      return CompletableActorFuture.completed(configuration);
    }
    // read it from the file
    RoutingState routingState = null;
    if (routingStatePath.isPresent() && Files.exists(routingStatePath.get())) {
      final byte[] routingStateContent;
      try {
        routingStateContent = Files.readAllBytes(routingStatePath.get());
        routingState =
            serializer.deserializeRoutingState(routingStateContent, 0, routingStateContent.length);
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
    final var withRoutingState =
        new ClusterConfiguration(
            configuration.version(),
            configuration.members(),
            configuration.lastChange(),
            configuration.pendingChanges(),
            Optional.of(routingState));
    return CompletableActorFuture.completed(withRoutingState);
  }
}
