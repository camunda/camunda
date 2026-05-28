/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import io.camunda.client.CamundaClient;
import io.camunda.container.cluster.BrokerNode;
import io.camunda.container.cluster.CamundaClusterBuilder;
import io.camunda.container.cluster.GatewayNode;
import io.camunda.container.volume.CamundaVolume;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.util.SemanticVersion;
import io.camunda.zeebe.util.VersionUtil;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.utility.DockerImageName;

final class ClusterHelper {
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("task1", s -> s.zeebeJobType("firstTask"))
          .serviceTask("task2", s -> s.zeebeJobType("secondTask"))
          .endEvent()
          .done();

  static void updateBroker(final BrokerNode<?> broker, final int id, final String version) {
    if ("CURRENT".equals(version)) {
      broker.setDockerImageName(
          ZeebeTestContainerDefaults.defaultTestImage().asCanonicalNameString());
      broker.withEnv(VersionUtil.VERSION_OVERRIDE_ENV_NAME, currentVersion());
    } else {
      broker.setDockerImageName(
          DockerImageName.parse("camunda/camunda").withTag(version).asCanonicalNameString());
    }

    final var semVer = SemanticVersion.parse(version);

    // For versions < 8.8 Unified configuration is not supported.
    // Clustering parameters have to be passed using the ENV vars in the containers

    if (semVer.isPresent() && semVer.get().minor() < 8) {
      broker.setDockerImageName(
          DockerImageName.parse("camunda/camunda").withTag(version).asCanonicalNameString());
      broker
          .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
          .withEnv("ZEEBE_BROKER_CLUSTER_PARTITIONS_COUNT", "1")
          .withEnv("ZEEBE_BROKER_CLUSTER_REPLICATIONFACTOR", "3")
          .withEnv("ZEEBE_BROKER_CLUSTER_CLUSTER_SIZE", "3")
          .withEnv("ZEEBE_BROKER_CLUSTER_NODE_ID", String.valueOf(id))
          .withEnv("ZEEBE_BROKER_CLUSTER_CLUSTER_NAME", CamundaClusterBuilder.DEFAULT_CLUSTER_NAME)
          .withEnv(
              "ZEEBE_BROKER_CLUSTER_INITIALCONTACTPOINTS",
              String.join(",", broker.getConfiguration().getCluster().getInitialContactPoints()))
          .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
          .withEnv("ZEEBE_BROKER_NETWORK_HOST", "0.0.0.0")
          .withEnv("ZEEBE_BROKER_NETWORK_ADVERTISEDHOST", broker.getInternalHost())
          .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "true")
          .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false");
    }
  }

  static void configureBroker(
      final BrokerNode<?> broker,
      final List<String> initialContactPoints,
      final Collection<CamundaVolume> volumes) {
    final var volume =
        CamundaVolume.newVolume(
            cfg -> {
              // Workaround for
              // https://github.com/camunda-community-hub/zeebe-test-container/issues/656
              final var labels = new HashMap<>(cfg.getLabels());
              labels.put(
                  DockerClientFactory.TESTCONTAINERS_SESSION_ID_LABEL,
                  DockerClientFactory.SESSION_ID);
              return cfg.withLabels(labels);
            });
    volumes.add(volume);
    broker
        .withCamundaData(volume)
        .withUnifiedConfig(
            cfg -> {
              cfg.getCluster().getMembership().setBroadcastUpdates(true);
              cfg.getCluster().getMembership().setSyncInterval(Duration.ofMillis(250));
              cfg.getCluster().getMembership().setProbeInterval(Duration.ofMillis(100));
              cfg.getCluster().getMembership().setProbeTimeout(Duration.ofSeconds(1));
              cfg.getCluster().getMembership().setFailureTimeout(Duration.ofSeconds(2));
              cfg.getCluster().getMembership().setSuspectProbes(2);
            })

        // Pass the old env vars for the old version broker
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_BROADCASTUPDATES", "true")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_SYNCINTERVAL", "250ms")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_PROBEINTERVAL", "100ms")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_PROBETIMEOUT", "1s")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_FAILURETIMEOUT", "2s")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_SUSPECTPROBES", "2")
        .withEnv("ZEEBE_BROKER_NETWORK_ADVERTISEDHOST", broker.getInternalHost())
        .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
        .withEnv("ZEEBE_BROKER_CLUSTER_CLUSTERNAME", CamundaClusterBuilder.DEFAULT_CLUSTER_NAME)
        .withEnv(
            "ZEEBE_BROKER_CLUSTER_INITIALCONTACTPOINTS", String.join(",", initialContactPoints))
        .withEnv("CAMUNDA_SYSTEM_UPGRADE_ENABLEVERSIONCHECK", "false")
        .withEnv("CAMUNDA_DATABASE_SCHEMAMANAGER_VERSIONCHECKRESTRICTIONENABLED", "false")
        // ensure we have an exporter present to test sharing exporter state across nodes
        .withEnv("ZEEBE_BROKER_EXECUTIONMETRICSEXPORTERENABLED", "true")
        .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
        // user - needs to be set to `1001` to allow a smooth update from zeebe 8.3 to 8.4,
        // as the default user changed to `1001` with 8.4 and was `1000` with 8.3
        // group - needs to be set to `0` as the data volume in 8.3 is owned by 1000:0
        // thus zeebe 8.4 needs to run with group `0` to be able to create new files in
        // the root of the data volume (in particular it creates a new `.topology.meta` file)
        // TODO remove after 8.4 release
        .withCreateContainerCmdModifier(
            createContainerCmd -> createContainerCmd.withUser("1001:0"));
  }

  static String currentVersion() {
    // Act as if the current in-development version were released already.
    // Otherwise, updates will be rejected because we don't allow upgrading to
    // pre-release versions.
    return VersionUtil.getVersion().replace("-SNAPSHOT", "");
  }

  static void deployProcess(final CamundaClient client) {
    client
        .newDeployResourceCommand()
        .addProcessModel(PROCESS, "process.bpmn")
        .send()
        .join(10, TimeUnit.SECONDS);
  }

  static CamundaClient newClient(final GatewayNode<?> gateway) {
    return CamundaClient.newClientBuilder()
        .preferRestOverGrpc(false)
        .grpcAddress(gateway.getGrpcAddress())
        .restAddress(gateway.getRestAddress())
        .build();
  }

  static long createProcessInstance(final CamundaClient client) {
    return Awaitility.await("process instance creation")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(100))
        .ignoreExceptions()
        .until(
            () ->
                client
                    .newCreateInstanceCommand()
                    .bpmnProcessId("process")
                    .latestVersion()
                    .variables(Map.of("foo", "bar"))
                    .send()
                    .join(),
            Objects::nonNull)
        .getProcessInstanceKey();
  }
}
