/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.container.volume.CamundaVolume;
import io.camunda.webapps.schema.SupportedVersions;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Full lifecycle upgrade test that validates the ES exporter works correctly after upgrading from
 * Camunda previous to current version with an export backlog. If strict mapping or other export
 * issues exist, this test will fail because backlog records won't be exported and jobs won't
 * complete.
 *
 * <p>Data is shared between the previous Docker container and the current in-JVM broker via a
 * Docker volume ({@link CamundaVolume}), which is extracted to the host filesystem using tar. This
 * approach works reliably in Docker-in-Docker (CI) environments.
 */
class ElasticsearchExporterMigrationIT {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchExporterMigrationIT.class);
  private static final String HTTP_PREFIX = "http://";
  private static final String ES_NETWORK_ALIAS = "test-elasticsearch";

  private static Network network;
  private static ElasticsearchContainer esContainer;
  private static RestClientTransport transport;
  private static ElasticsearchClient esClient;
  private static RestClient restClient;
  private static ExporterMigrationTestHelper testHelper;

  @TempDir private static Path dataDir;

  @BeforeEach
  void setUp() {
    network = Network.newNetwork();

    esContainer =
        new ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                    .withTag(SupportedVersions.SUPPORTED_ELASTICSEARCH_VERSION))
            .withNetwork(network)
            .withNetworkAliases(ES_NETWORK_ALIAS)
            .withStartupTimeout(Duration.ofMinutes(5))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.watcher.enabled", "false")
            .withEnv("xpack.ml.enabled", "false")
            .withEnv("action.auto_create_index", "true")
            .withEnv("action.destructive_requires_name", "false");
    esContainer.start();

    restClient =
        RestClient.builder(HttpHost.create(HTTP_PREFIX + esContainer.getHttpHostAddress())).build();
    transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
    esClient = new ElasticsearchClient(transport);

    final String containerAddress = HTTP_PREFIX + esContainer.getHttpHostAddress();
    testHelper =
        new ExporterMigrationTestHelper(
            esClient, ES_NETWORK_ALIAS, network, containerAddress, dataDir, LOG);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (restClient != null) {
      restClient.close();
    }
    if (esContainer != null) {
      esContainer.stop();
    }
    if (network != null) {
      network.close();
    }
  }

  @ParameterizedTest(name = "Migrate from {0} to current version")
  @MethodSource(
      "io.camunda.it.schema.ExporterMigrationTestHelper#fetchLatestPatchFromPreviousMinor")
  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  void shouldCompleteUpgradeWithBacklogAndExportAllRecordsAgainstLatestReleasePatch(
      final String version) throws Exception {
    testHelper.shouldCompleteUpgradeWithBacklogAndExportAllRecords(version);
  }

  @ParameterizedTest(name = "Migrate from {0} to current version")
  @MethodSource("io.camunda.it.schema.ExporterMigrationTestHelper#fetchAllPatchesFromPreviousMinor")
  @Tag("dl-nightly")
  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  void shouldCompleteUpgradeWithBacklogAndExportAllRecordsAgainstReleasePatches(
      final String version) throws Exception {
    testHelper.shouldCompleteUpgradeWithBacklogAndExportAllRecords(version);
  }
}
