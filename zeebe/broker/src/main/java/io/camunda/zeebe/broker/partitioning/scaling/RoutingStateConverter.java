/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling;

import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class RoutingStateConverter {

  public static RoutingState fromScaleRecord(final ScaleRecord record) {
    final RequestHandling requestHandling;
    if (record.getDesiredPartitionCount() <= 0) {
      throw new IllegalArgumentException(
          "desired partition count must be greater than 0, was "
              + record.getDesiredPartitionCount());
    }
    if (record.getRedistributedPartitions().size() == record.getDesiredPartitionCount()) {
      requestHandling = new RequestHandling.AllPartitions(record.getDesiredPartitionCount());
    } else {
      if (record.getMessageCorrelationPartitions() > record.getRedistributedPartitions().size()) {
        throw new IllegalArgumentException(
            "partitions in message correlation cannot be more than partitions in request handling: %d > %d"
                .formatted(
                    record.getMessageCorrelationPartitions(),
                    record.getRedistributedPartitions().size()));
      }
      if (record.getRedistributedPartitions().size() > record.getDesiredPartitionCount()) {
        throw new IllegalArgumentException(
            "redistributed partitions cannot be more than desired partitions: %d > %d"
                .formatted(
                    record.getRedistributedPartitions().size(), record.getDesiredPartitionCount()));
      }
      requestHandling =
          new RequestHandling.ActivePartitions(
              record.getRedistributedPartitions().size(),
              Set.of(),
              IntStream.rangeClosed(
                      record.getRedistributedPartitions().size() + 1,
                      record.getDesiredPartitionCount())
                  .boxed()
                  .collect(Collectors.toSet()));
    }
    final var messageCorrelation =
        new MessageCorrelation.HashMod(record.getMessageCorrelationPartitions());
    return new RoutingState(0L, requestHandling, messageCorrelation);
  }
}
