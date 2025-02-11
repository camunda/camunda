/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import io.camunda.application.Profile;
import io.camunda.client.CamundaClient;
import io.camunda.exporter.CamundaExporter;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeVolume;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

public class ZeebeComponentHelper extends AbstractComponentHelper<ZeebeComponentHelper> {

  private static final String CAMUNDA_OLD_VERSION = "8.7.0-alpha4";
  private final ZeebeVolume volume;
  private ZeebeContainer zeebeContainer;
  private CamundaClient camundaClient;
  private TestStandaloneBroker broker;
  private final Path zeebeDataPath;

  public ZeebeComponentHelper(final Network network, final String indexPrefix) {
    super(network, indexPrefix);
    volume = ZeebeVolume.newVolume();
    zeebeDataPath = Path.of(System.getProperty("user.dir") + "/zeebe-data" + volume.getName());
    zeebeComponentHelper = this;
  }

  @Override
  public ZeebeComponentHelper initial(
      final DatabaseType type, final Map<String, String> envOverrides) {
    start87Broker(envOverrides, type);
    return this;
  }

  @Override
  public ZeebeComponentHelper update(
      final DatabaseType type, final Map<String, String> envOverrides) {
    camundaClient.close();
    zeebeContainer.close();
    start88Broker(envOverrides, type);
    return this;
  }

  @Override
  public void close() {
    if (camundaClient != null) {
      camundaClient.close();
    }
    if (zeebeContainer != null) {
      zeebeContainer.stop();
    }
    if (broker != null) {
      broker.close();
    }
  }

  public String getZeebeGatewayAddress() {
    if (zeebeContainer != null && zeebeContainer.isStarted()) {
      return zeebeContainer.getInternalAddress(TestZeebePort.GATEWAY.port());
    }
    return "host.testcontainers.internal:" + broker.mappedPort(TestZeebePort.GATEWAY);
  }

  public String getZeebeRestAddress() {
    if (zeebeContainer != null && zeebeContainer.isStarted()) {
      return "http://" + zeebeContainer.getInternalAddress(TestZeebePort.REST.port());
    }
    return "http://host.testcontainers.internal:" + broker.mappedPort(TestZeebePort.REST);
  }

  public CamundaClient getCamundaClient() {
    return camundaClient;
  }

  public void cleanup() {
    if (!Files.exists(zeebeDataPath)) {
      return;
    }
    try {
      Files.walk(zeebeDataPath)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.delete(p);
                } catch (final IOException ignored) {
                  // ignore
                }
              });
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private ZeebeContainer start87Broker(
      final Map<String, String> envOverrides, final DatabaseType databaseType) {
    zeebeContainer =
        new ZeebeContainer(DockerImageName.parse("camunda/zeebe:" + CAMUNDA_OLD_VERSION))
            .withExposedPorts(26500, 9600, 8080)
            .withNetwork(network)
            .withNetworkAliases("zeebe");

    final Map<String, String> env =
        databaseType.equals(DatabaseType.ELASTICSEARCH)
            ? zeebe87ElasticsearchDefaultConfig()
            : zeebe87OpensearchDefaultConfig();
    if (envOverrides != null) {
      env.putAll(envOverrides);
    }
    env.forEach(zeebeContainer::withEnv);

    zeebeContainer
        .withCreateContainerCmdModifier(
            cmd -> {
              cmd.withUser("1001:0").getHostConfig().withBinds(volume.asZeebeBind());
            })
        .withCommand(
            "sh", "-c", "chmod -R 777 /usr/local/zeebe/data && /usr/local/bin/start-zeebe");

    zeebeContainer.start();
    final var url =
        "http://" + zeebeContainer.getExternalHost() + ":" + zeebeContainer.getMappedPort(8080);
    camundaClient =
        CamundaClient.newClientBuilder()
            .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
            .restAddress(URI.create(url))
            .usePlaintext()
            .build();

    return zeebeContainer;
  }

  private TestStandaloneBroker start88Broker(
      final Map<String, String> envOverrides, final DatabaseType databaseType) {
    extractVolume();

    broker =
        new TestStandaloneBroker()
            .withAdditionalProfile(Profile.PROCESS_MIGRATION)
            .withRecordingExporter(true)
            .withBrokerConfig(cfg -> cfg.getGateway().setEnable(true))
            .withExporter(
                "CamundaExporter",
                camundaExporterConfig(databaseType, envOverrides.get("camunda.database.url")))
            .withWorkingDirectory(zeebeDataPath.resolve("usr/local/zeebe"));
    final Map<String, String> env =
        databaseType.equals(DatabaseType.ELASTICSEARCH)
            ? zeebe88ElasticsearchDefaultConfig()
            : zeebe88OpensearchDefaultConfig();
    env.putAll(envOverrides);
    env.forEach(broker::withProperty);
    broker.start();
    broker.awaitCompleteTopology();
    org.testcontainers.Testcontainers.exposeHostPorts(
        broker.mappedPort(TestZeebePort.GATEWAY), broker.mappedPort(TestZeebePort.REST));

    camundaClient = broker.newClientBuilder().build();
    return broker;
  }

  private Map<String, String> zeebe87ElasticsearchDefaultConfig() {
    return new HashMap<>() {
      {
        put(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.ElasticsearchExporter");
        put("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", "http://elasticsearch:9200");
        put("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX", indexPrefix);
        put("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1");
        put("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
        put("CAMUNDA_DATABASE_URL", "http://elasticsearch:9200");
        put("CAMUNDA_REST_QUERY_ENABLED", "true");
        put("CAMUNDA_DATABASE_INDEXPREFIX", indexPrefix);
      }
    };
  }

  private Map<String, String> zeebe88ElasticsearchDefaultConfig() {
    return new HashMap<>() {
      {
        put("camunda.rest.query.enabled", "true");
        put("camunda.database.indexPrefix", indexPrefix);
      }
    };
  }

  private Map<String, String> zeebe87OpensearchDefaultConfig() {
    return new HashMap<>() {
      {
        put(
            "ZEEBE_BROKER_EXPORTERS_OPENSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.opensearch.OpensearchExporter");
        put("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_URL", "http://opensearch:9200");
        put("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_BULK_SIZE", "1");
        put("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_INDEX_PREFIX", indexPrefix);
        put("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
        put("CAMUNDA_DATABASE_URL", "http://opensearch:9200");
        put("CAMUNDA_REST_QUERY_ENABLED", "true");
        put("CAMUNDA_DATABASE_INDEXPREFIX", indexPrefix);
      }
    };
  }

  private Map<String, String> zeebe88OpensearchDefaultConfig() {
    return new HashMap<>() {
      {
        put("camunda.rest.query.enabled", "true");
        put("camunda.database.type", "opensearch");
        put("camunda.database.indexPrefix", indexPrefix);
      }
    };
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

  private Consumer<ExporterCfg> camundaExporterConfig(
      final DatabaseType databaseType, final String databaseUrl) {
    final Map<String, String> connect =
        databaseType.equals(DatabaseType.ELASTICSEARCH)
            ? Map.of("url", databaseUrl, "indexPrefix", indexPrefix)
            : Map.of("url", databaseUrl, "type", "opensearch", "indexPrefix", indexPrefix);
    return cfg -> {
      cfg.setClassName(CamundaExporter.class.getName());
      cfg.setArgs(
          Map.of(
              "connect",
              connect,
              "bulk",
              Map.of("size", 1, "delay", 1),
              "index",
              Map.of("shouldWaitForImporters", false, "prefix", indexPrefix)));
    };
  }
}
