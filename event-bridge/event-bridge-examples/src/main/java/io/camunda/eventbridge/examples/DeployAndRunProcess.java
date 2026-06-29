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
 * Deploys a tiny process to an orchestration-cluster gateway and starts one instance, generating
 * Zeebe records (deployment, process instance, job, …) for the {@code ZeebeRecordExporter} to
 * publish.
 *
 * <pre>
 *   java io.camunda.eventbridge.examples.DeployAndRunProcess [processId]
 *   -Dzeebe.grpc=http://localhost:26500
 * </pre>
 */
public final class DeployAndRunProcess {

  private DeployAndRunProcess() {}

  public static void main(final String[] args) {
    final var grpcAddress = System.getProperty("zeebe.grpc", "http://localhost:26500");
    final var processId = args.length > 0 ? args[0] : "smoke-process";

    try (final var client =
        CamundaClient.newClientBuilder()
            .grpcAddress(URI.create(grpcAddress))
            .preferRestOverGrpc(false)
            .build()) {

      client
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess(processId)
                  .startEvent()
                  .serviceTask("task")
                  .zeebeJobType("smoke-job")
                  .endEvent()
                  .done(),
              processId + ".bpmn")
          .send()
          .join();
      System.out.println("Deployed process '" + processId + "'");

      final var instance =
          client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
      System.out.println(
          "Started instance key=" + instance.getProcessInstanceKey() + " of '" + processId + "'");
    }
  }
}
