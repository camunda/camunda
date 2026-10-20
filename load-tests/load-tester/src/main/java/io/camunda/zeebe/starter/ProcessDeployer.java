/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.starter;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.client.api.response.Process;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.StarterProperties;
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("starter")
public class ProcessDeployer {

  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(ProcessDeployer.class), Duration.ofSeconds(5));
  private static final Logger LOG = LoggerFactory.getLogger(ProcessDeployer.class);

  private final CamundaClient loaderClient;
  private final StarterProperties starterCfg;
  private final ConnectionMonitor connectionMonitor;
  private final StarterLivenessIndicator livenessIndicator;

  public ProcessDeployer(
      final CamundaClient loaderClient,
      final LoadTesterProperties properties,
      final ConnectionMonitor connectionMonitor,
      final StarterLivenessIndicator livenessIndicator) {
    this.loaderClient = loaderClient;
    starterCfg = properties.getStarter();
    this.connectionMonitor = connectionMonitor;
    this.livenessIndicator = livenessIndicator;
  }

  /**
   * Waits for the cluster topology, then deploys the benchmark resources and blocks, retrying until
   * the deployment succeeds.
   */
  public long deploy() {
    connectionMonitor.awaitAndPrintTopology();
    livenessIndicator.recordStarted();

    final var deployCmd = constructDeploymentCommand();

    while (true) {
      try {
        final var result = deployCmd.send().join();
        return result.getProcesses().stream()
            .filter(p -> p.getBpmnProcessId().equals(starterCfg.getProcessId()))
            .findFirst()
            .map(Process::getProcessDefinitionKey)
            .orElse(0L);
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Failed to deploy process, retrying", e);
        try {
          Thread.sleep(200);
        } catch (final InterruptedException ex) {
          // ignore
        }
      }
    }
  }

  private DeployResourceCommandStep2 constructDeploymentCommand() {
    LOG.info(
        "Deploying main resource: {}, extra resources: {}",
        starterCfg.getBpmnXmlPath(),
        starterCfg.getExtraBpmnModels());
    final var deployCmd =
        loaderClient
            .newDeployResourceCommand()
            .addResourceFromClasspath(starterCfg.getBpmnXmlPath());

    final var extraBpmnModels = starterCfg.getExtraBpmnModels();
    if (extraBpmnModels != null) {
      for (final var model : extraBpmnModels) {
        deployCmd.addResourceFromClasspath(model);
      }
    }
    return deployCmd;
  }
}
