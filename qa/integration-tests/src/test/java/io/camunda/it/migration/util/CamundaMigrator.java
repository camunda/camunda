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
import io.camunda.qa.util.cluster.TestRestOperateClient;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbConfigurator;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import io.camunda.zeebe.it.util.SearchClientsUtil;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.VersionUtil;
import io.zeebe.containers.ZeebeContainer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.agrona.CloseHelper;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

public class CamundaMigrator extends ApiCallable {
  private static final String OS_USER = "admin";
  private static final String OS_PASSWORD = "yourStrongPassword123!";
  private static final String URL = "http://%s:%d";
  private static final String RPC_URL = "http://%s:%d";
  private static final String PREVIOUS_VERSION = VersionUtil.getPreviousVersion();
  private String databaseUrl;
  private final Network network;
  private final String indexPrefix;
  private final CamundaVolume volume;
  private final Path zeebeDataPath;
  private CamundaClient camundaClient;
  private ZeebeContainer camundaContainer;
  private TestSimpleCamundaApplication broker;
  private TestRestTasklistClient tasklistClient;
  private TestRestOperateClient operateClient;
  private DocumentBasedSearchClient searchClients;

  public CamundaMigrator(final Network network, final String indexPrefix, final Path tempDir) {
    this.network = network;
    this.indexPrefix = indexPrefix;
    volume = CamundaVolume.newCamundaVolume();
    zeebeDataPath = tempDir.resolve(volume.getName());
  }

  public CamundaMigrator initialize(
      final DatabaseType databaseType,
      final String databaseUrl,
      final Map<String, String> envOverrides) {
    this.databaseUrl = databaseUrl;
    final DockerImageName image =
        DockerImageName.parse("camunda/camunda").withTag(PREVIOUS_VERSION);

    camundaContainer =
        new ZeebeContainer(image)
            .withAdditionalExposedPort(8080)
            .withNetwork(network)
            .withNetworkAliases("camunda");

    final Map<String, String> env =
        databaseType.equals(DatabaseType.ELASTICSEARCH)
            ? elasticsearchConfiguration87()
            : opensearchConfiguration87();
    if (envOverrides != null) {
      env.putAll(envOverrides);
    }
    env.forEach(camundaContainer::withEnv);

    camundaContainer.withCreateContainerCmdModifier(
        cmd ->
            cmd.withUser("1001:0")
                .getHostConfig()
                /* On camunda/camunda image the default Zeebe dataDir is /usr/local/camunda/data */
                .withBinds(volume.asBind("/usr/local/camunda/data")));

    camundaContainer.start();
    url =
        URL.formatted(
            camundaContainer.getHost(), camundaContainer.getMappedPort(TestZeebePort.REST.port()));
    camundaClient =
        CamundaClient.newClientBuilder()
            .grpcAddress(
                URI.create(
                    RPC_URL.formatted(
                        camundaContainer.getHost(),
                        camundaContainer.getMappedPort(TestZeebePort.GATEWAY.port()))))
            .restAddress(URI.create(url))
            .usePlaintext()
            .build();

    try {
      login();
    } catch (final IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }

    return this;
  }

  public CamundaMigrator update(
      final DatabaseType databaseType, final Map<String, String> envOverrides) {
    /* Trigger snapshot of Zeebe's data to force flush ExporterMetadata */
    PartitionsActuator.of(camundaContainer).takeSnapshot();

    camundaContainer.close();
    extractVolume();
    broker =
        new TestSimpleCamundaApplication()
            .withBasicAuth()
            .withAuthenticatedAccess()
            .withAdditionalProfile(Profile.PROCESS_MIGRATION)
            .withBrokerConfig(
                cfg -> {
                  cfg.getExperimental().setVersionCheckRestrictionEnabled(false);
                  cfg.getGateway().setEnable(true);
                })
            .withWorkingDirectory(zeebeDataPath.resolve("usr/local/zeebe"));

    final var multiDbConfigurator = new MultiDbConfigurator(broker);
    if (databaseType.equals(DatabaseType.ELASTICSEARCH)) {
      multiDbConfigurator.configureElasticsearchSupport(databaseUrl, indexPrefix);
    } else {
      multiDbConfigurator.configureOpenSearchSupport(
          databaseUrl, indexPrefix, OS_USER, OS_PASSWORD);
    }
    final Map<String, String> env = new HashMap<>();
    env.put("camunda.migration.process.importerFinishedTimeout", "PT2S");
    // Reduce importer intervals to speed up tests
    env.put("camunda.operate.importer.importPositionUpdateInterval", "200");
    env.put("camunda.operate.importer.readerBackoff", "200");
    env.put("camunda.tasklist.importer.importPositionUpdateInterval", "200");
    env.put("camunda.tasklist.importer.readerBackoff", "200");

    env.putAll(envOverrides);
    env.forEach(broker::withProperty);

    broker.start();
    broker.awaitCompleteTopology();
    camundaClient = broker.newClientBuilder().build();
    url = URL.formatted(broker.host(), broker.mappedPort(TestZeebePort.REST));
    final var uri = URI.create(url + "/");
    tasklistClient = new TestRestTasklistClient(uri, databaseUrl);
    operateClient = new TestRestOperateClient(uri, "demo", "demo");
    searchClients =
        databaseType.isElasticSearch()
            ? SearchClientsUtil.createLowLevelElasticsearchSearchClient(
                getConnectConfiguration(databaseType))
            : SearchClientsUtil.createLowLevelOpensearchSearchClient(
                getConnectConfiguration(databaseType));
    return this;
  }

  public void close() {
    CloseHelper.quietCloseAll(camundaContainer, broker, tasklistClient, operateClient);
  }

  public void cleanup() throws IOException {
    if (Files.exists(zeebeDataPath)) {
      FileUtil.deleteFolder(zeebeDataPath);
    }
  }

  public String getWebappsUrl() {
    return url + "/v1";
  }

  public CamundaClient getCamundaClient() {
    return camundaClient;
  }

  /// Returns the tasklist client. This is only usable on 8.8 version
  public TestRestTasklistClient getTasklistClient() {
    return tasklistClient;
  }

  /// Returns the operate client. This is only usable on 8.8 version
  public TestRestOperateClient getOperateClient() {
    return operateClient;
  }

  /// Returns the search client. This is only usable on 8.8 version
  public DocumentBasedSearchClient getSearchClient() {
    return searchClients;
  }

  public IndexDescriptor indexFor(final Class<? extends IndexDescriptor> clazz) {
    return new IndexDescriptors(indexPrefix, true).get(clazz);
  }

  private Map<String, String> elasticsearchConfiguration87() {
    return new HashMap<>() {
      {
        put("SPRING_PROFILES_ACTIVE", "tasklist,broker,auth,operate");
        put("ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT", "1");
        put(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
            ElasticsearchExporter.class.getName());
        put("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", "http://elasticsearch:9200");
        put("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULKSIZE", "1");
        put("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX", indexPrefix);
        put("CAMUNDA_TASKLIST_ZEEBE_COMPATIBILITYMODE", "true");
        put("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", "http://elasticsearch:9200");
        put("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", "http://elasticsearch:9200");
        put("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", "camunda:26500");
        put("CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS", "http://camunda:8080");
        put("CAMUNDA_TASKLIST_ELASTICSEARCH_INDEXPREFIX", indexPrefix + "-tasklist");
        put("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_PREFIX", indexPrefix);
        put("CAMUNDA_OPERATE_ELASTICSEARCH_URL", "http://elasticsearch:9200");
        put("CAMUNDA_OPERATE_ELASTICSEARCH_INDEXPREFIX", indexPrefix + "-operate");
        put("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_PREFIX", indexPrefix);
        put("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", "http://elasticsearch:9200");
        put("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", "camunda:26500");
        put("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
        put("CAMUNDA_DATABASE_URL", "http://elasticsearch:9200");
      }
    };
  }

  private Map<String, String> opensearchConfiguration87() {
    return new HashMap<>() {
      {
        put("SPRING_PROFILES_ACTIVE", "tasklist,broker,auth,operate");
        put("ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT", "1");
        put("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_CLASSNAME", OpensearchExporter.class.getName());
        put("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_URL", "http://opensearch:9200");
        put("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_BULKSIZE", "1");
        put("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_INDEX_PREFIX", indexPrefix);
        put("CAMUNDA_TASKLIST_ZEEBE_COMPATIBILITYMODE", "true");
        put("CAMUNDA_TASKLIST_DATABASE", "opensearch");
        put("CAMUNDA_TASKLIST_OPENSEARCH_URL", "http://opensearch:9200");
        put("CAMUNDA_TASKLIST_OPENSEARCH_INDEXPREFIX", indexPrefix + "-tasklist");
        put("CAMUNDA_TASKLIST_ZEEBEOPENSEARCH_PREFIX", indexPrefix);
        put("CAMUNDA_TASKLIST_ZEEBEOPENSEARCH_URL", "http://opensearch:9200");
        put("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", "camunda:26500");
        put("CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS", "http://camunda:8080");
        put("CAMUNDA_OPERATE_OPENSEARCH_URL", "http://opensearch:9200");
        put("CAMUNDA_OPERATE_DATABASE", "opensearch");
        put("CAMUNDA_OPERATE_ZEEBEOPENSEARCH_URL", "http://opensearch:9200");
        put("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", "camunda:26500");
        put("CAMUNDA_OPERATE_OPENSEARCH_INDEXPREFIX", indexPrefix + "-operate");
        put("CAMUNDA_OPERATE_ZEEBEOPENSEARCH_PREFIX", indexPrefix);
        put("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
        put("CAMUNDA_DATABASE_URL", "http://opensearch:9200");
        // Reduce importer intervals to speed up tests
        put("CAMUNDA_OPERATE_IMPORTER_IMPORTPOSITIONUPDATEINTERVAL", "200");
        put("CAMUNDA_OPERATE_IMPORTER_READERBACKOFF", "200");
        put("CAMUNDA_TASKLIST_IMPORTER_IMPORTPOSITIONUPDATEINTERVAL", "200");
        put("CAMUNDA_TASKLIST_IMPORTER_READERBACKOFF", "200");
      }
    };
  }

  private void extractVolume() {
    try {
      volume.extract(zeebeDataPath);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private ConnectConfiguration getConnectConfiguration(final DatabaseType databaseType) {
    final ConnectConfiguration connectConfiguration = new ConnectConfiguration();
    connectConfiguration.setType(databaseType.name());
    connectConfiguration.setClusterName("elasticsearch");
    connectConfiguration.setUrl(databaseUrl);
    connectConfiguration.setIndexPrefix(indexPrefix);
    if (databaseType.isOpenSearch()) {
      connectConfiguration.setUsername(OS_USER);
      connectConfiguration.setPassword(OS_PASSWORD);
    }
    return connectConfiguration;
  }
}
