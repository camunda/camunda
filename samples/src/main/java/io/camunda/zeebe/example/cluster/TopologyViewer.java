/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.example.cluster;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.api.response.Topology;

/**
 * Example application that connects to a cluster on Camunda Cloud, or a locally deployed cluster.
 *
 * <p>When connecting to a cluster in Camunda Cloud, this application assumes that the following
 * environment variables are set:
 *
 * <ul>
 *   <li>ZEEBE_ADDRESS
 *   <li>ZEEBE_CLIENT_ID
 *   <li>ZEEBE_CLIENT_SECRET
 *   <li>ZEEBE_AUTHORIZATION_SERVER_URL
 * </ul>
 *
 * <p><strong>Hint:</strong> When you create client credentials in Camunda Cloud you have the option
 * to download a file with above lines filled out for you.
 *
 * <p>When {@code ZEEBE_ADDRESS} is not set, it connects to a broker running on localhost with
 * default ports
 */
public final class TopologyViewer {

  public static void main(final String[] args) {
    final String defaultAddress = "localhost:26500";
    final String envVarAddress = System.getenv("ZEEBE_ADDRESS");

    final ZeebeClientBuilder clientBuilder;
    final String contactPoint;
    if (envVarAddress != null) {
      /* Connect to Camunda Cloud Cluster, assumes that credentials are set in environment variables.
       * See JavaDoc on class level for details
       */
      contactPoint = envVarAddress;
      clientBuilder = ZeebeClient.newClientBuilder().gatewayAddress(envVarAddress);
    } else {
      // connect to local deployment; assumes that authentication is disabled
      contactPoint = defaultAddress;
      clientBuilder = ZeebeClient.newClientBuilder().gatewayAddress(defaultAddress).usePlaintext();
    }

    try (final ZeebeClient client = clientBuilder.build()) {
      System.out.println("Requesting topology with initial contact point " + contactPoint);

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
