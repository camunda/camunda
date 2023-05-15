/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.serde;

import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.client.api.response.Topology;
import java.io.OutputStream;
import java.io.PrintWriter;

public final class HumanOutputFormatter implements OutputFormatter {
  private static final String TAB = "  ";

  @Override
  public void write(final OutputStream output, final Topology topology) {
    try (final var writer = new PrintWriter(output)) {
      print(writer, topology);
    }
  }

  private void print(final PrintWriter writer, final Topology topology) {
    writer
        .format("Cluster size: %d%n", topology.getClusterSize())
        .format("Partitions count: %d%n", topology.getPartitionsCount())
        .format("Replication factor: %d%n", topology.getReplicationFactor())
        .format("Gateway version: %s%n", topology.getGatewayVersion())
        .format("Brokers: %n");

    for (final var broker : topology.getBrokers()) {
      print(writer, broker);
    }
  }

  private void print(final PrintWriter writer, final BrokerInfo broker) {
    writer
        .format(TAB + "Broker %d - %s%n", broker.getNodeId(), broker.getAddress())
        .format(TAB.repeat(2) + "Version: %s%n", broker.getVersion());
    for (final var partition : broker.getPartitions()) {
      print(writer, partition);
    }
  }

  private void print(final PrintWriter writer, final PartitionInfo partition) {
    writer.format(
        TAB.repeat(2) + "Partition %d : %s, %s%n",
        partition.getPartitionId(),
        partition.getRole().name(),
        partition.getHealth().name());
  }
}
