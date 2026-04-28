/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.network;

import static io.camunda.application.commons.security.CamundaSecurityConfiguration.UNPROTECTED_API_ENV_VAR;
import static io.camunda.zeebe.test.util.asserts.TopologyAssert.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Topology;
import io.camunda.configuration.Exporter;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.container.CamundaContainer;
import io.camunda.container.CamundaContainer.BrokerContainer;
import io.camunda.container.CamundaContainer.WebAppContainer;
import io.camunda.container.CamundaContainer.WebAppContainer.WebApp;
import io.camunda.container.cluster.CamundaPort;
import io.camunda.exporter.CamundaExporter;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
final class SecuredClusteredMessagingIT {
  private static final SelfSignedCertificate CERTIFICATE = newCertificate();

  @AutoClose private static final Network NETWORK = Network.newNetwork();

  @SuppressWarnings("unused")
  @Container
  private static final ElasticsearchContainer ELASTIC =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withNetwork(NETWORK)
          .withNetworkAliases("elastic")
          .withStartupTimeout(Duration.ofMinutes(5));

  private final String testPrefix = UUID.randomUUID().toString();
  private final String esUrl = "http://elastic:9200";

  @Container
  private final BrokerContainer zeebe =
      new BrokerContainer(ZeebeTestContainerDefaults.defaultTestImage())
          .withNetwork(NETWORK)
          .withNetworkAliases("zeebe")
          .withCopyToContainer(
              MountableFile.forHostPath(CERTIFICATE.certificate().toPath(), 0777),
              "/tmp/certificate.pem")
          .withCopyToContainer(
              MountableFile.forHostPath(CERTIFICATE.privateKey().toPath(), 0777), "/tmp/key.pem")
          .withUnifiedConfig(
              cfg -> {
                final var transportCluster =
                    cfg.getSecurity().getTransportLayerSecurity().getCluster();
                transportCluster.setEnabled(true);
                transportCluster.setCertificateChainPath(new File("/tmp/certificate.pem"));
                transportCluster.setCertificatePrivateKeyPath(new File("/tmp/key.pem"));

                cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.elasticsearch);
                cfg.getData().getSecondaryStorage().getElasticsearch().setIndexPrefix(testPrefix);
                cfg.getData().getSecondaryStorage().getElasticsearch().setUrl(esUrl);
                cfg.getData()
                    .getExporters()
                    .computeIfAbsent(
                        "camundaexporter",
                        exp -> {
                          final var exporter = new Exporter();
                          exporter.setClassName(CamundaExporter.class.getName());
                          final Map<String, Object> args =
                              Map.of("connect", Map.of("url", esUrl, "indexPrefix", testPrefix));
                          exporter.setArgs(args);

                          return exporter;
                        });
              })

          // unified configuration: type
          .withEnv("CAMUNDA_DATABASE_TYPE", SecondaryStorageType.elasticsearch.name())
          .withEnv(UNPROTECTED_API_ENV_VAR, "true")
          .withEnv("CAMUNDA_LOG_LEVEL", "DEBUG");

  @Container
  private final CamundaContainer operate =
      new WebAppContainer(ZeebeTestContainerDefaults.defaultTestImage(), WebApp.OPERATE)
          .withNetworkAliases("operate")
          .withNetwork(NETWORK)
          .withCopyToContainer(
              MountableFile.forHostPath(CERTIFICATE.certificate().toPath(), 0777),
              "/tmp/certificate.pem")
          .withCopyToContainer(
              MountableFile.forHostPath(CERTIFICATE.privateKey().toPath(), 0777), "/tmp/key.pem")
          .withUnifiedConfig(
              cfg -> {
                final var transportCluster =
                    cfg.getSecurity().getTransportLayerSecurity().getCluster();
                transportCluster.setEnabled(true);
                transportCluster.setCertificateChainPath(new File("/tmp/certificate.pem"));
                transportCluster.setCertificatePrivateKeyPath(new File("/tmp/key.pem"));

                cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.elasticsearch);
                cfg.getData().getSecondaryStorage().getElasticsearch().setIndexPrefix(testPrefix);
                cfg.getData().getSecondaryStorage().getElasticsearch().setUrl(esUrl);

                cfg.getCluster().setInitialContactPoints(List.of("zeebe:26502"));
                cfg.getCluster().getNetwork().getInternalApi().setAdvertisedHost("operate");
                cfg.getCluster().setGatewayId("operate");
              })
          .withEnv(
              "CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS",
              zeebe.getInternalAddress(CamundaPort.GATEWAY_GRPC.getPort()))
          .withEnv("CAMUNDA_LOG_LEVEL", "DEBUG")
          .withExposedPorts(8080, 9600, 26502)
          .waitingFor(
              new HttpWaitStrategy()
                  .forPath("/actuator/health/readiness")
                  .forPort(9600)
                  .withStartupTimeout(Duration.ofSeconds(60)))
          .dependsOn(zeebe);

  @Container
  private final CamundaContainer tasklist =
      new WebAppContainer(ZeebeTestContainerDefaults.defaultTestImage(), WebApp.TASKLIST)
          .withNetworkAliases("tasklist")
          .withNetwork(NETWORK)
          .withCopyToContainer(
              MountableFile.forHostPath(CERTIFICATE.certificate().toPath(), 0777),
              "/tmp/certificate.pem")
          .withCopyToContainer(
              MountableFile.forHostPath(CERTIFICATE.privateKey().toPath(), 0777), "/tmp/key.pem")
          .withUnifiedConfig(
              cfg -> {
                final var transportCluster =
                    cfg.getSecurity().getTransportLayerSecurity().getCluster();
                transportCluster.setEnabled(true);
                transportCluster.setCertificateChainPath(new File("/tmp/certificate.pem"));
                transportCluster.setCertificatePrivateKeyPath(new File("/tmp/key.pem"));

                cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.elasticsearch);
                cfg.getData().getSecondaryStorage().getElasticsearch().setIndexPrefix(testPrefix);
                cfg.getData().getSecondaryStorage().getElasticsearch().setUrl(esUrl);

                cfg.getCluster().setInitialContactPoints(List.of("zeebe:26502"));
                cfg.getCluster().getNetwork().getInternalApi().setAdvertisedHost("tasklist");
                cfg.getCluster().setGatewayId("tasklist");
              })
          .withEnv(
              "CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS",
              zeebe.getInternalAddress(CamundaPort.GATEWAY_GRPC.getPort()))
          .withEnv(
              "CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS",
              zeebe.getInternalAddress(CamundaPort.GATEWAY_REST.getPort()))
          .withEnv("CAMUNDA_LOG_LEVEL", "DEBUG")
          .withExposedPorts(8080, 9600, 26502)
          .waitingFor(
              new HttpWaitStrategy()
                  .forPath("/actuator/health/readiness")
                  .forPort(9600)
                  .withStartupTimeout(Duration.ofSeconds(60)))
          .dependsOn(zeebe);

  @Test
  void shouldFormAClusterWithTlsWithCertChain() {
    // given - a cluster with Zeebe, Operate, and Tasklist

    // when - note the client is using plaintext since we only care about inter-cluster TLS
    final Topology topology;
    try (final var client =
        CamundaClient.newClientBuilder()
            .restAddress(URI.create("http://" + zeebe.getHost() + ":" + zeebe.getMappedPort(8080)))
            .grpcAddress(URI.create("http://" + zeebe.getHost() + ":" + zeebe.getMappedPort(26500)))
            .build()) {
      topology = client.newTopologyRequest().send().join(15, TimeUnit.SECONDS);
    }

    // then
    assertThat(topology).isComplete(1, 1, 1);
    assertInternalPortIsSecured(zeebe);
    assertInternalPortIsSecured(operate);
    assertInternalPortIsSecured(tasklist);
  }

  /** Verifies that both the command and internal APIs of the broker are correctly secured. */
  private void assertInternalPortIsSecured(final GenericContainer<?> container) {
    final var internalApiAddress =
        new InetSocketAddress(
            container.getContainerIpAddress(),
            container.getMappedPort(CamundaPort.INTERNAL.getPort()));

    assertAddressIsSecured(container.getNetworkAliases(), internalApiAddress);
  }

  private void assertAddressIsSecured(final Object nodeId, final SocketAddress address) {
    SslAssert.assertThat(address)
        .as("node %s is not secured correctly at address %s", nodeId, address)
        .isSecuredBy(CERTIFICATE);
  }

  private static SelfSignedCertificate newCertificate() {
    try {
      return new SelfSignedCertificate();
    } catch (final CertificateException e) {
      throw new IllegalStateException("Failed to create self-signed certificate", e);
    }
  }
}
