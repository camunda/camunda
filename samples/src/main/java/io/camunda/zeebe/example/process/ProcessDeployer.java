/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.example.process;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.api.response.DeploymentEvent;

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
public final class ProcessDeployer {

  public static void main(final String[] args) {
    final String defaultAddress = "localhost:26500";
    final String envVarAddress = System.getenv("ZEEBE_ADDRESS");

    final ZeebeClientBuilder clientBuilder;
    if (envVarAddress != null) {
      /* Connect to Camunda Cloud Cluster, assumes that credentials are set in environment variables.
       * See JavaDoc on class level for details
       */
      clientBuilder = ZeebeClient.newClientBuilder().gatewayAddress(envVarAddress);
    } else {
      // connect to local deployment; assumes that authentication is disabled
      clientBuilder = ZeebeClient.newClientBuilder().gatewayAddress(defaultAddress).usePlaintext();
    }

    try (final ZeebeClient client = clientBuilder.build()) {

      final DeploymentEvent deploymentEvent =
          client.newDeployCommand().addResourceFromClasspath("demoProcess.bpmn").send().join();

      System.out.println("Deployment created with key: " + deploymentEvent.getKey());
    }
  }
}
