/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import io.camunda.zeebe.engine.state.immutable.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.engine.state.routing.DbRoutingState;
import java.util.Set;

public class RoutingUtil {

  public static RequestHandling requestHandling(final DbRoutingState state) {

    final var desiredPartitions = state.desiredPartitions();
    final var currentPartitions = state.currentPartitions();
    return desiredPartitions.equals(currentPartitions)
        ? new RequestHandling.AllPartitions(currentPartitions.size())
        // check if we need to do it better;
        : new RequestHandling.ActivePartitions(
            currentPartitions.size(), Set.of(), desiredPartitions);
  }

  public static MessageCorrelation messageCorrelation(final DbRoutingState state) {
    return switch (state.messageCorrelation()) {
      case final HashMod hashMod ->
          new RoutingState.MessageCorrelation.HashMod(hashMod.partitionCount());
    };
  }

  public static RoutingState routingState(final long version, final DbRoutingState state) {
    return new RoutingState(version, requestHandling(state), messageCorrelation(state));
  }
}
