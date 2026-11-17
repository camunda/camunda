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
import io.camunda.zeebe.metrics.Connected;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Deploys the benchmark process once the cluster is {@link Connected}, retries until it succeeds,
 * then publishes {@link ProcessDeployed}. The deployment is asynchronous, so the thread that
 * published {@link Connected} is not blocked.
 */
@Component
@Profile("starter")
public class ProcessDeployer {

  private static final Duration RETRY_DELAY = Duration.ofMillis(200);
  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(ProcessDeployer.class), Duration.ofSeconds(5));
  private static final Logger LOG = LoggerFactory.getLogger(ProcessDeployer.class);

  private final CamundaClient loaderClient;
  private final StarterProperties starterCfg;
  private final StarterLivenessIndicator livenessIndicator;
  private final ApplicationEventPublisher eventPublisher;
  private final TaskScheduler scheduler;

  public ProcessDeployer(
      final CamundaClient loaderClient,
      final LoadTesterProperties properties,
      final StarterLivenessIndicator livenessIndicator,
      final ApplicationEventPublisher eventPublisher,
      final TaskScheduler scheduler) {
    this.loaderClient = loaderClient;
    starterCfg = properties.getStarter();
    this.livenessIndicator = livenessIndicator;
    this.eventPublisher = eventPublisher;
    this.scheduler = scheduler;
  }

  @EventListener(Connected.class)
  void onConnected() {
    livenessIndicator.recordStarted();
    deploy(constructDeploymentCommand());
  }

  private void deploy(final DeployResourceCommandStep2 deployCmd) {
    deployCmd
        .send()
        .whenComplete(
            (result, error) -> {
              if (error == null) {
                final long processDefinitionKey =
                    result.getProcesses().stream()
                        .filter(p -> p.getBpmnProcessId().equals(starterCfg.getProcessId()))
                        .findFirst()
                        .map(Process::getProcessDefinitionKey)
                        .orElse(0L);
                eventPublisher.publishEvent(new ProcessDeployed(processDefinitionKey));
              } else {
                THROTTLED_LOGGER.warn("Failed to deploy process, retrying", error);
                scheduler.schedule(() -> deploy(deployCmd), Instant.now().plus(RETRY_DELAY));
              }
            });
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
