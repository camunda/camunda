/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.response.CompleteJobResponse;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmokeTestIT {

  private static final Logger LOG = LoggerFactory.getLogger(SmokeTestIT.class);

  @Test(timeout = 300_000L)
  public void smokeTest() throws InterruptedException {
    try (ZeebeClient client = ZeebeClient.newClientBuilder().usePlaintext().build()) {
      final String startKey = UUID.randomUUID().toString();
      final String correlationKey = UUID.randomUUID().toString();

      DeploymentEvent deploymentEvent = null;

      while (deploymentEvent == null) {
        try {
          deploymentEvent =
              client.newDeployCommand().addResourceFromClasspath("smoke-test.bpmn").send().join();
        }
        catch (Exception e) {
          LOG.warn("Failed to deploy process", e);
          Thread.sleep(100);
        }
      }

      LOG.info("Deployment Event: {} ", deploymentEvent);

      final ZeebeFuture<ProcessInstanceResult> processInstanceEvent =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(deploymentEvent.getProcesses().get(0).getBpmnProcessId())
              .latestVersion()
              .variables(Map.of("startKey", startKey))
              .withResult()
              .send();

      client
          .newWorker()
          .jobType("smoke-test-send-message")
          .handler(
              (jobClient, job) -> {
                assertThat(job.getVariablesAsMap().get("startKey")).isEqualTo(startKey);
                final PublishMessageResponse publishMessageResponse =
                    client
                        .newPublishMessageCommand()
                        .messageName("smoke-test-message")
                        .correlationKey(correlationKey)
                        .timeToLive(Duration.ofMinutes(5))
                        .send()
                        .join();

                LOG.info("Publish Message Response: {}", publishMessageResponse.getMessageKey());

                final CompleteJobResponse completeJobResponse =
                    jobClient
                        .newCompleteCommand(job.getKey())
                        .variables(Map.of("correlationKey", correlationKey))
                        .send()
                        .join();
              })
          .open();

      final ProcessInstanceResult processInstanceResult = processInstanceEvent.join();
      LOG.info("Process Instance Result: {}", processInstanceResult);

      assertThat(processInstanceResult.getVariablesAsMap().get("correlationKey"))
          .isEqualTo(correlationKey);
    }
  }
}
