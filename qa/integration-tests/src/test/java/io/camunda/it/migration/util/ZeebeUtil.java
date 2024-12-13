/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeVolume;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

public class ZeebeUtil {

  private static final String CAMUNDA_OLD_VERSION = "8.6.6";
  private final ZeebeVolume volume;
  private final Network network;
  private ZeebeContainer zeebeContainer;
  private ZeebeClient zeebeClient;
  private TestStandaloneBroker broker;
  private final Path zeebeDataPath;

  public ZeebeUtil(final Network network) {
    volume = ZeebeVolume.newVolume();
    zeebeDataPath = Path.of(System.getProperty("user.dir") + "/zeebe-data" + volume.getName());
    this.network = network;
  }

  public String getZeebeGatewayAddress() {
    if (zeebeContainer != null && zeebeContainer.isStarted()) {
      return zeebeContainer.getInternalAddress(TestZeebePort.GATEWAY.port());
    }
    return "host.testcontainers.internal:" + broker.mappedPort(TestZeebePort.GATEWAY);
  }

  public String getZeebeRestAddress() {
    if (zeebeContainer != null && zeebeContainer.isStarted()) {
      return zeebeContainer.getInternalAddress(TestZeebePort.REST.port());
    }
    return "http://host.testcontainers.internal:" + broker.mappedPort(TestZeebePort.REST);
  }

  public ZeebeContainer start86Broker(final Map<String, String> env) {
    zeebeContainer =
        new ZeebeContainer(DockerImageName.parse("camunda/zeebe:" + CAMUNDA_OLD_VERSION))
            .withExposedPorts(26500, 9600, 8080)
            .withNetwork(network)
            .withNetworkAliases("zeebe");

    env.forEach(zeebeContainer::withEnv);

    zeebeContainer
        .withCreateContainerCmdModifier(
            cmd -> {
              cmd.withUser("1001:0").getHostConfig().withBinds(volume.asZeebeBind());
            })
        .withCommand(
            "sh", "-c", "chmod -R 777 /usr/local/zeebe/data && /usr/local/bin/start-zeebe");

    zeebeContainer.start();

    zeebeClient =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
            .restAddress(
                URI.create(
                    "http://"
                        + zeebeContainer.getExternalHost()
                        + ":"
                        + zeebeContainer.getMappedPort(8080)))
            .usePlaintext()
            .build();

    return zeebeContainer;
  }

  public ZeebeClient getZeebeClient() {
    return zeebeClient;
  }

  private void extractVolume() {
    try {
      volume.extract(zeebeDataPath);
      // Need to remove the .topology.meta to be recreated by new broker
      Files.delete(zeebeDataPath.resolve("usr/local/zeebe/data/.topology.meta"));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public TestStandaloneBroker start87Broker(
      final Consumer<ExporterCfg> exporterConfig, final Map<String, String> env) {
    extractVolume();

    broker =
        new TestStandaloneBroker()
            .withRecordingExporter(true)
            .withBrokerConfig(cfg -> cfg.getGateway().setEnable(true))
            .withExporter("CamundaExporter", exporterConfig)
            .withWorkingDirectory(zeebeDataPath.resolve("usr/local/zeebe"));
    env.forEach(broker::withProperty);
    broker.start();
    broker.awaitCompleteTopology();
    org.testcontainers.Testcontainers.exposeHostPorts(
        broker.mappedPort(TestZeebePort.GATEWAY), broker.mappedPort(TestZeebePort.REST));

    zeebeClient = broker.newClientBuilder().build();
    zeebeClient
        .newUserCreateCommand()
        .name("demo")
        .username("demo")
        .email("dem@demo.com")
        .password("demo")
        .send()
        .join();

    return broker;
  }

  public void stop() {
    if (zeebeClient != null) {
      zeebeClient.close();
    }
    if (zeebeContainer != null) {
      zeebeContainer.stop();
    }
    if (broker != null) {
      broker.close();
      try {
        Files.walk(zeebeDataPath)
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(
                p -> {
                  try {
                    Files.delete(p);
                  } catch (final IOException ignored) {
                    System.out.println("ignored");
                  }
                });
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
