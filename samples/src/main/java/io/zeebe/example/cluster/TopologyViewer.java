/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.example.cluster;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.api.response.Topology;

public final class TopologyViewer {

  public static void main(final String[] args) {
    final String broker = "127.0.0.1:26500";

    final ZeebeClientBuilder builder =
        ZeebeClient.newClientBuilder().gatewayAddress(broker).usePlaintext();

    try (final ZeebeClient client = builder.build()) {
      System.out.println("Requesting topology with initial contact point " + broker);

      final Topology topology = client.newTopologyRequest().send().join();

      System.out.println("Topology:");
      topology
          .getBrokers()
          .forEach(
              b -> {
                System.out.println("    " + b.getAddress());
                b.getPartitions()
                    .forEach(
                        p ->
                            System.out.println(
                                "      " + p.getPartitionId() + " - " + p.getRole()));
              });

      System.out.println("Done.");
    }
  }
}
