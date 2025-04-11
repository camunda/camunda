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
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestOperateClient;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.qa.util.multidb.MultiDbConfigurator;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.connect.configuration.ConnectConfiguration;
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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.agrona.CloseHelper;
import org.testcontainers.utility.DockerImageName;

public class CamundaMigrator extends ApiCallable implements AutoCloseable {
  private static final String OS_USER = "admin";
  private static final String OS_PASSWORD = "yourStrongPassword123!";
  private static final String URL = "http://%s:%d";
  private static final String RPC_URL = "http://%s:%d";
  private static final String PREVIOUS_VERSION = VersionUtil.getPreviousVersion();
  private final String databaseUrl;
  private final String indexPrefix;
  private final CamundaVolume volume;
  private final Path zeebeDataPath;
  private CamundaClient camundaClient;
  private ZeebeContainer camundaContainer;
  private TestCamundaApplication camunda;
  private TestRestTasklistClient tasklistClient;
  private TestRestOperateClient operateClient;
  private DocumentBasedSearchClient searchClients;
  private final DatabaseType databaseType;

  public CamundaMigrator(
      final String indexPrefix,
      final Path tempDir,
      final DatabaseType databaseType,
      final String databaseUrl) {
    this.indexPrefix = indexPrefix;
    this.databaseType = databaseType;
    this.databaseUrl = databaseUrl;
    volume = CamundaVolume.newCamundaVolume();
    zeebeDataPath = tempDir.resolve(volume.getName());
  }

  public CamundaMigrator initialize(final Map<String, String> envOverrides) {
    final DockerImageName image =
        DockerImageName.parse("camunda/camunda").withTag(PREVIOUS_VERSION);
    camundaContainer =
        new ZeebeContainer(image)
            .withAdditionalExposedPort(8080)
            .withStartupTimeout(Duration.ofMinutes(1))
            .withNetworkAliases("camunda")
            .withExtraHost("internal.host", "host-gateway");

    final String internalUrl =
        databaseUrl.replaceAll("elasticsearch|opensearch|localhost", "internal.host");
    final Map<String, String> env =
        databaseType.equals(DatabaseType.ES) || databaseType.equals(DatabaseType.LOCAL)
            ? elasticsearchConfiguration87(internalUrl)
            : opensearchConfiguration87(internalUrl);
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

  public CamundaMigrator update(final Map<String, String> envOverrides) {
    /* Trigger snapshot of Zeebe's data to force flush ExporterMetadata */
    PartitionsActuator.of(camundaContainer).takeSnapshot();

    camundaContainer.close();
    extractVolume();
    camunda =
        new TestCamundaApplication()
            .withBasicAuth()
            .withAuthenticatedAccess()
            .withAdditionalProfile(Profile.PROCESS_MIGRATION)
            .withBrokerConfig(
                cfg -> {
                  cfg.getExperimental().setVersionCheckRestrictionEnabled(false);
                  cfg.getGateway().setEnable(true);
                })
            .withWorkingDirectory(zeebeDataPath.resolve("usr/local/zeebe"));

    final var multiDbConfigurator = new MultiDbConfigurator(camunda);
    if (databaseType.equals(DatabaseType.ES) || databaseType.equals(DatabaseType.LOCAL)) {
      multiDbConfigurator.configureElasticsearchSupportIncludingOldExporter(
          databaseUrl, indexPrefix);
    } else {
      multiDbConfigurator.configureOpenSearchSupportIncludingOldExporter(
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
    env.forEach(camunda::withProperty);

    camunda.start();
    camunda.awaitCompleteTopology();
    camundaClient = camunda.newClientBuilder().build();
    url = URL.formatted(camunda.host(), camunda.mappedPort(TestZeebePort.REST));
    final var uri = URI.create(url + "/");
    tasklistClient = new TestRestTasklistClient(uri);
    operateClient = new TestRestOperateClient(uri, "demo", "demo");
    searchClients =
        databaseType.equals(DatabaseType.ES)
            ? SearchClientsUtil.createLowLevelElasticsearchSearchClient(
                getConnectConfiguration(databaseType))
            : SearchClientsUtil.createLowLevelOpensearchSearchClient(
                getConnectConfiguration(databaseType));
    return this;
  }

  @Override
  public void close() throws IOException {
    CloseHelper.quietCloseAll(camundaContainer, camunda, tasklistClient, operateClient);
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

  private Map<String, String> elasticsearchConfiguration87(final String internalUrl) {
    return new HashMap<>() {
      {
        put("SPRING_PROFILES_ACTIVE", "tasklist,broker,auth,operate");
        put("ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT", "1");
        put(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
            ElasticsearchExporter.class.getName());
        put("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", internalUrl);
        put("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULKSIZE", "1");
        put("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX", indexPrefix);
        put("CAMUNDA_TASKLIST_ZEEBE_COMPATIBILITYMODE", "true");
        put("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", internalUrl);
        put("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", internalUrl);
        put("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", "camunda:26500");
        put("CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS", "http://camunda:8080");
        put("CAMUNDA_TASKLIST_ELASTICSEARCH_INDEXPREFIX", indexPrefix + "-tasklist");
        put("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_PREFIX", indexPrefix);
        put("CAMUNDA_OPERATE_ELASTICSEARCH_URL", internalUrl);
        put("CAMUNDA_OPERATE_ELASTICSEARCH_INDEXPREFIX", indexPrefix + "-operate");
        put("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_PREFIX", indexPrefix);
        put("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", internalUrl);
        put("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", "camunda:26500");
        put("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
        put("CAMUNDA_DATABASE_URL", internalUrl);
      }
    };
  }

  private Map<String, String> opensearchConfiguration87(final String internalUrl) {
    return new HashMap<>() {
      {
        put("SPRING_PROFILES_ACTIVE", "tasklist,broker,auth,operate");
        put("ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT", "1");
        put("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_CLASSNAME", OpensearchExporter.class.getName());
        put("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_URL", internalUrl);
        put("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_BULKSIZE", "1");
        put("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_INDEX_PREFIX", indexPrefix);
        put("CAMUNDA_TASKLIST_ZEEBE_COMPATIBILITYMODE", "true");
        put("CAMUNDA_TASKLIST_DATABASE", "opensearch");
        put("CAMUNDA_TASKLIST_OPENSEARCH_URL", internalUrl);
        put("CAMUNDA_TASKLIST_OPENSEARCH_INDEXPREFIX", indexPrefix + "-tasklist");
        put("CAMUNDA_TASKLIST_ZEEBEOPENSEARCH_PREFIX", indexPrefix);
        put("CAMUNDA_TASKLIST_ZEEBEOPENSEARCH_URL", internalUrl);
        put("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", "camunda:26500");
        put("CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS", "http://camunda:8080");
        put("CAMUNDA_OPERATE_OPENSEARCH_URL", internalUrl);
        put("CAMUNDA_OPERATE_DATABASE", "opensearch");
        put("CAMUNDA_OPERATE_ZEEBEOPENSEARCH_URL", internalUrl);
        put("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", "camunda:26500");
        put("CAMUNDA_OPERATE_OPENSEARCH_INDEXPREFIX", indexPrefix + "-operate");
        put("CAMUNDA_OPERATE_ZEEBEOPENSEARCH_PREFIX", indexPrefix);
        put("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
        put("CAMUNDA_DATABASE_URL", internalUrl);
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
    if (databaseType.equals(DatabaseType.OS)) {
      connectConfiguration.setUsername(OS_USER);
      connectConfiguration.setPassword(OS_PASSWORD);
    }
    return connectConfiguration;
  }
}
