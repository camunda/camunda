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
import io.zeebe.client.api.response.WorkflowInstanceEvent;

public final class WorkflowInstanceCreator {

  public static void main(final String[] args) {
    final String broker = "127.0.0.1:26500";

    final String bpmnProcessId = "demoProcess";

    final ZeebeClientBuilder builder =
        ZeebeClient.newClientBuilder().gatewayAddress(broker).usePlaintext();

    try (final ZeebeClient client = builder.build()) {

      System.out.println("Creating workflow instance");

      final WorkflowInstanceEvent workflowInstanceEvent =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(bpmnProcessId)
              .latestVersion()
              .send()
              .join();

      System.out.println(
          "Workflow instance created with key: " + workflowInstanceEvent.getWorkflowInstanceKey());
    }
  }
}
