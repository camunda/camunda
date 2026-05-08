/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition;

import io.camunda.client.CamundaClient;
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
 * Deploys the cluster-management BPMN resources onto the system partition when this replica becomes
 * the Raft leader. Runs on a virtual thread so the main bootstrap path is not blocked.
 *
 * <p>The deployer subscribes to leader-role transitions via {@link
 * SystemPartition#addLeaderListener}; on each transition to leader it triggers {@link #deployAll()}
 * which polls the cluster until the gateway is reachable and then submits a single bulk deployment
 * request.
 *
 * <p>The BPMNs are read from the system-partition module's classpath under {@code bpmn/}.
 */
public final class SystemPartitionBpmnAutoDeployer {

  private static final Logger LOG = LoggerFactory.getLogger(SystemPartitionBpmnAutoDeployer.class);
  private static final String RESOURCE_DIR = "bpmn/";
  private static final List<String> BPMN_RESOURCES =
      List.of("scale-operation.bpmn", "exporter-operation.bpmn", "modification_starter.bpmn");
  private static final Duration TOPOLOGY_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration RETRY_INTERVAL = Duration.ofSeconds(2);

  private final CamundaClient camundaClient;

  public SystemPartitionBpmnAutoDeployer(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  /**
   * Registers this deployer as a leader listener on the given {@link SystemPartition}. When the
   * partition transitions to leader, {@link #deployAll()} is invoked on a virtual thread.
   */
  public void register(final SystemPartition systemPartition) {
    systemPartition.addLeaderListener(
        isLeader -> {
          if (isLeader) {
            deployAll();
          }
        });
  }

  /** Schedules the deploy on a virtual thread; returns immediately. */
  public void deployAll() {
    Thread.ofVirtual().name("system-partition-bpmn-deployer").start(this::awaitClusterAndDeploy);
  }

  private void awaitClusterAndDeploy() {
    int attempt = 0;
    while (!Thread.currentThread().isInterrupted()) {
      attempt++;
      if (isClusterReady()) {
        LOG.info(
            "Cluster topology reachable after {} attempt(s), deploying system-partition BPMNs",
            attempt);
        doDeploy();
        return;
      }
      LOG.debug(
          "Cluster not ready yet for system-partition BPMN deploy (attempt {}), retrying in {}",
          attempt,
          RETRY_INTERVAL);
      try {
        Thread.sleep(RETRY_INTERVAL.toMillis());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.warn("System-partition BPMN auto-deploy interrupted before cluster became ready");
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
        LOG.warn(
            "Skipping system-partition BPMN auto-deploy for {} — failed to read resource", name, e);
        continue;
      }
      if (cmd == null) {
        cmd = camundaClient.newDeployResourceCommand().addResourceBytes(bytes, name);
      } else {
        cmd = cmd.addResourceBytes(bytes, name);
      }
      loaded++;
    }
    if (cmd == null) {
      LOG.warn("No system-partition BPMN resources found to auto-deploy");
      return;
    }
    final int total = loaded;
    cmd.send()
        .whenComplete(
            (response, error) -> {
              if (error != null) {
                LOG.warn("System-partition BPMN auto-deploy failed", error);
              } else {
                LOG.info(
                    "System-partition BPMN auto-deploy complete: {} resources requested,"
                        + " {} processes deployed",
                    total,
                    response.getProcesses().size());
              }
            });
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
