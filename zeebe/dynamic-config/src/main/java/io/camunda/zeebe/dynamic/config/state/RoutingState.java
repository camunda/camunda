/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import io.camunda.zeebe.protocol.Protocol;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record RoutingState(
    Set<Integer> activePartitions, MessageRoutingConfiguration messageRoutingConfiguration) {

  public static RoutingState ofFixed(final int partitionCount) {
    final var activePartitions =
        IntStream.rangeClosed(Protocol.START_PARTITION_ID, partitionCount)
            .boxed()
            .collect(Collectors.toSet());
    return new RoutingState(activePartitions, MessageRoutingConfiguration.fixed(partitionCount));
  }
}
