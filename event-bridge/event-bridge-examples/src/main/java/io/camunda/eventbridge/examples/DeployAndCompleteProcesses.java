/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.examples;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.net.URI;

/**
 * Deploys a process with no wait states (start → end) and starts N instances, which therefore
 * <em>complete</em> immediately — emitting process-instance {@code ELEMENT_COMPLETED} records that
 * the analytics pipeline turns into execution-time facts. Used to verify the windowed "N completed
 * per definition" metric end-to-end.
 *
 * <pre>
 *   java io.camunda.eventbridge.examples.DeployAndCompleteProcesses [processId] [count]
 *   -Dzeebe.grpc=http://localhost:26500
 * </pre>
 */
public final class DeployAndCompleteProcesses {

  private DeployAndCompleteProcesses() {}

  public static void main(final String[] args) {
    final var restAddress = System.getProperty("camunda.rest", "http://localhost:8088");
    final var processId = args.length > 0 ? args[0] : "exec-time-demo";
    final var count = args.length > 1 ? Integer.parseInt(args[1]) : 5;

    try (final var client =
        CamundaClient.newClientBuilder()
            .restAddress(URI.create(restAddress))
            .preferRestOverGrpc(true)
            .build()) {

      client
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
              processId + ".bpmn")
          .send()
          .join();
      System.out.println("Deployed auto-completing process '" + processId + "'");

      for (int i = 0; i < count; i++) {
        final var instance =
            client
                .newCreateInstanceCommand()
                .bpmnProcessId(processId)
                .latestVersion()
                .send()
                .join();
        System.out.println("Started+completed instance key=" + instance.getProcessInstanceKey());
      }
      System.out.println("Started " + count + " instances of '" + processId + "'");
    }
  }
}
