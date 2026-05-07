/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.client.api.response.Topology;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deploys the bundled BPMN resources on coordinator startup so the dynamic-config workflows are
 * available without requiring an out-of-band deploy. Mirrors the pattern used by the default-user
 * initializer: configuration-driven, runs once during boot, idempotent in the sense that
 * re-deploying simply creates a new version.
 *
 * <p>Deployment runs on a virtual thread that first polls the cluster topology until it responds —
 * the broker is up before the gateway is, so we cannot deploy synchronously from the bootstrap
 * thread.
 */
public final class BpmnAutoDeployer {

  private static final Logger LOG = LoggerFactory.getLogger(BpmnAutoDeployer.class);
  private static final String RESOURCE_DIR = "bpmn/";
  // Note: the three scheduler BPMNs (marker/backup/retention) are intentionally NOT in this list.
  // They are deployed by CheckpointSchedulingService with timer cycles derived from BackupCfg, so
  // shipping a static copy here would race with the config-driven one.
  private static final List<String> BPMN_RESOURCES =
      List.of("scale-operation.bpmn", "exporter-operation.bpmn", "modification_starter.bpmn");
  private static final Duration TOPOLOGY_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration RETRY_INTERVAL = Duration.ofSeconds(2);

  private final CamundaClient camundaClient;

  public BpmnAutoDeployer(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  /** Schedules the deploy on a virtual thread; returns immediately. */
  public void deployAll() {
    Thread.ofVirtual().name("bpmn-auto-deployer").start(this::awaitClusterAndDeploy);
  }

  private void awaitClusterAndDeploy() {
    int attempt = 0;
    while (!Thread.currentThread().isInterrupted()) {
      attempt++;
      if (isClusterReady()) {
        LOG.info("Cluster topology reachable after {} attempt(s), deploying BPMNs", attempt);
        doDeploy();
        return;
      }
      LOG.debug("Cluster not ready yet (attempt {}), retrying in {}", attempt, RETRY_INTERVAL);
      try {
        Thread.sleep(RETRY_INTERVAL.toMillis());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.warn("BPMN auto-deploy interrupted before cluster became ready");
        return;
      }
    }
  }

  private boolean isClusterReady() {
    try {
      final Topology topology =
          camundaClient
              .newTopologyRequest()
              .send()
              .get(TOPOLOGY_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      return topology != null && !topology.getBrokers().isEmpty();
    } catch (final Exception e) {
      LOG.debug("Topology check failed: {}", e.getMessage());
      return false;
    }
  }

  private void doDeploy() {
    DeployResourceCommandStep2 cmd = null;
    int loaded = 0;
    for (final var name : BPMN_RESOURCES) {
      final byte[] bytes;
      try {
        bytes = readResource(RESOURCE_DIR + name);
      } catch (final IOException e) {
        LOG.warn("Skipping BPMN auto-deploy for {} — failed to read resource", name, e);
        continue;
      }
      cmd = (cmd == null ? startCommand(name, bytes) : cmd.addResourceBytes(bytes, name));
      loaded++;
    }
    if (cmd == null) {
      LOG.warn("No BPMN resources found to auto-deploy");
      return;
    }
    final int total = loaded;
    cmd.send()
        .whenComplete(
            (response, error) -> {
              if (error != null) {
                LOG.warn("BPMN auto-deploy failed", error);
              } else {
                LOG.info(
                    "BPMN auto-deploy complete: {} resources requested, {} processes deployed",
                    total,
                    response.getProcesses().size());
              }
            });
  }

  private DeployResourceCommandStep2 startCommand(final String name, final byte[] bytes) {
    final DeployResourceCommandStep1 step1 = camundaClient.newDeployResourceCommand();
    return step1.addResourceBytes(bytes, name);
  }

  private byte[] readResource(final String path) throws IOException {
    try (final InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
      if (in == null) {
        throw new IOException("Resource not found on classpath: " + path);
      }
      return in.readAllBytes();
    }
  }
}
