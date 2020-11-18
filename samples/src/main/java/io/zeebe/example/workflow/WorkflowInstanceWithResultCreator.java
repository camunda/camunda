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
import io.zeebe.client.api.response.WorkflowInstanceResult;
import java.time.Duration;
import java.util.Map;

public class WorkflowInstanceWithResultCreator {
  public static void main(final String[] args) {
    final String broker = "127.0.0.1:26500";

    final String bpmnProcessId = "demoProcessSingleTask";

    final ZeebeClientBuilder builder =
        ZeebeClient.newClientBuilder().gatewayAddress(broker).usePlaintext();

    try (final ZeebeClient client = builder.build()) {

      openJobWorker(client); // open job workers so that task are executed and workflow is completed
      System.out.println("Creating workflow instance");

      final WorkflowInstanceResult workflowInstanceResult =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(bpmnProcessId)
              .latestVersion()
              .withResult() // to await the completion of workflow execution and return result
              .send()
              .join();

      System.out.println(
          "Workflow instance created with key: "
              + workflowInstanceResult.getWorkflowInstanceKey()
              + " and completed with results: "
              + workflowInstanceResult.getVariables());
    }
  }

  private static void openJobWorker(final ZeebeClient client) {
    client
        .newWorker()
        .jobType("foo")
        .handler(
            (jobClient, job) ->
                jobClient
                    .newCompleteCommand(job.getKey())
                    .variables(Map.of("job", job.getKey()))
                    .send())
        .timeout(Duration.ofSeconds(10))
        .open();
  }
}
