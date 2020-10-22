/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.example.workflow;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.api.response.DeploymentEvent;

public final class WorkflowDeployer {

  public static void main(final String[] args) {
    final String broker = "localhost:26500";

    final ZeebeClientBuilder clientBuilder =
        ZeebeClient.newClientBuilder().gatewayAddress(broker).usePlaintext();

    try (final ZeebeClient client = clientBuilder.build()) {

      final DeploymentEvent deploymentEvent =
          client.newDeployCommand().addResourceFromClasspath("demoProcess.bpmn").send().join();

      System.out.println("Deployment created with key: " + deploymentEvent.getKey());
    }
  }
}
