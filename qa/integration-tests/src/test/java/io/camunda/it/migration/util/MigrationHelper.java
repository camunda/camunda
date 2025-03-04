/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import io.camunda.client.CamundaClient;
import io.camunda.it.utils.MultiDbConfigurator;
import io.camunda.qa.util.cluster.TestRestOperateClient;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.util.FileUtil;
import io.zeebe.containers.ZeebeContainer;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

public class MigrationHelper implements ApiCallable {

  private static final String CAMUNDA_OLD_VERSION = "8.7.0-SNAPSHOT";
  private static final String OS_USER = "admin";
  private static final String OS_PASSWORD = "yourStrongPassword123!";
  private static final String URL = "http://%s:%d";
  private static final String RPC_URL = "http://%s:%d";
  private String cookie;
  private String csrfToken;
  private String url;
  private String databaseUrl;
  private final Network network;
  private final String indexPrefix;
  private final CamundaVolume volume;
  private final Path zeebeDataPath;
  private CamundaClient camundaClient;
  private ZeebeContainer camundaContainer;
  private TestSimpleCamundaApplication broker;
  private MultiDbConfigurator multiDbConfigurator;
  private TestRestTasklistClient tasklistClient;
  private TestRestOperateClient operateClient;

  public MigrationHelper(final Network network, final String indexPrefix) {
    this.network = network;
    this.indexPrefix = indexPrefix;
    volume = CamundaVolume.newCamundaVolume();
    zeebeDataPath = Path.of(System.getProperty("user.dir") + "/zeebe-data" + volume.getName());
  }

  public MigrationHelper initialize(final DatabaseType databaseType, final String databaseUrl) {
    return initialize(databaseType, databaseUrl, new HashMap<>());
  }

  public MigrationHelper initialize(
      final DatabaseType databaseType,
      final String databaseUrl,
      final Map<String, String> envOverrides) {
    final String image = "camunda/camunda:" + CAMUNDA_OLD_VERSION;
    this.databaseUrl = databaseUrl;
    camundaContainer =
        new ZeebeContainer(DockerImageName.parse(image))
            .withExposedPorts(26500, 9600, 8080)
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

  public MigrationHelper update(final DatabaseType databaseType) {
    return update(databaseType, new HashMap<>());
  }

  public MigrationHelper update(
      final DatabaseType databaseType, final Map<String, String> envOverrides) {
    /* Trigger snapshot of Zeebe's data to force flush ExporterMetadata */
    PartitionsActuator.of(camundaContainer).takeSnapshot();

    camundaContainer.close();
    extractVolume();
    broker =
        new TestSimpleCamundaApplication()
            .withAuthenticationMethod(AuthenticationMethod.BASIC)
            /*.withAdditionalProfile(Profile.PROCESS_MIGRATION)*/
            .withBrokerConfig(
                cfg -> {
                  cfg.getExperimental().setVersionCheckRestrictionEnabled(false);
                  cfg.getGateway().setEnable(true);
                })
            .withWorkingDirectory(zeebeDataPath.resolve("usr/local/zeebe"));

    multiDbConfigurator = new MultiDbConfigurator(broker);
    if (databaseType.equals(DatabaseType.ELASTICSEARCH)) {
      multiDbConfigurator.configureElasticsearchSupport(databaseUrl, indexPrefix);
    } else {
      multiDbConfigurator.configureOpenSearchSupport(
          databaseUrl, indexPrefix, OS_USER, OS_PASSWORD);
    }
    final Map<String, String> env = new HashMap<>();
    env.put("camunda.migration.process.importerFinishedTimeout", "PT5S");

    env.putAll(envOverrides);
    env.forEach(broker::withProperty);

    broker.start();
    broker.awaitCompleteTopology();
    camundaClient = broker.newClientBuilder().build();
    url = URL.formatted(broker.host(), broker.mappedPort(TestZeebePort.REST));
    tasklistClient = new TestRestTasklistClient(URI.create(url + "/"), databaseUrl);
    operateClient = new TestRestOperateClient(URI.create(url + "/"), "demo", "demo");
    return this;
  }

  @Override
  public String getCookie() {
    return cookie;
  }

  @Override
  public void setCookie(final String cookie) {
    this.cookie = cookie;
  }

  @Override
  public String getUrl() {
    return url;
  }

  @Override
  public String getCsrfToken() {
    return csrfToken;
  }

  @Override
  public void setCsrfToken(final String csrfToken) {
    this.csrfToken = csrfToken;
  }

  public void close() throws Exception {
    if (camundaContainer != null) {
      camundaContainer.stop();
      camundaContainer = null;
    }
    if (broker != null) {
      broker.stop();
      broker = null;
    }
    if (tasklistClient != null) {
      tasklistClient.close();
      tasklistClient = null;
    }
    if (operateClient != null) {
      operateClient.close();
      operateClient = null;
    }
  }

  public void cleanup() throws IOException {
    if (Files.exists(zeebeDataPath)) {
      FileUtil.deleteFolder(zeebeDataPath);
    }
  }

  public String operateUrl() {
    return url + "/v1";
  }

  public String tasklistUrl() {
    return url + "/v1";
  }

  /** Returns the tasklist client. This is only usable on 8.8 version */
  public TestRestTasklistClient getTasklistClient() {
    return tasklistClient;
  }

  /** Returns the operate client. This is only usable on 8.8 version */
  public TestRestOperateClient getOperateClient() {
    return operateClient;
  }

  public CamundaClient getCamundaClient() {
    return camundaClient;
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
      }
    };
  }

  private void extractVolume() {
    try {
      volume.extract(zeebeDataPath);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
