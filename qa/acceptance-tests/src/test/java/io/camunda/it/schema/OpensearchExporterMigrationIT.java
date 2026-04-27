/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema;

import io.camunda.webapps.schema.SupportedVersions;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

public class OpensearchExporterMigrationIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchExporterMigrationIT.class);
  private static final String OS_NETWORK_ALIAS = "test-opensearch";
  private static final String HTTP_PREFIX = "http://";

  private static Network network;
  private static OpenSearchContainer osContainer;
  private static ApacheHttpClient5Transport transport;
  private static OpenSearchClient osClient;
  private static ExporterMigrationTestHelper testHelper;

  @TempDir private static Path dataDir;

  @BeforeAll
  static void setUp() {
    network = Network.newNetwork();

    osContainer =
        new OpenSearchContainer<>(
                DockerImageName.parse("opensearchproject/opensearch")
                    .withTag(SupportedVersions.SUPPORTED_OPENSEARCH_VERSION))
            .withNetwork(network)
            .withNetworkAliases(OS_NETWORK_ALIAS)
            .withStartupTimeout(Duration.ofMinutes(5))
            .withEnv("discovery.type", "single-node")
            .withEnv("action.auto_create_index", "true")
            .withEnv("action.destructive_requires_name", "false")

            // NOTE: Even though the security is disabled, opensearch still requires a strong
            //  password to be set, to start successfully.
            .withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", "Strong-Initial-Password123!");

    osContainer.start();

    try {
      final String hostAndAddress = osContainer.getHttpHostAddress();
      final var uri = URI.create(hostAndAddress);

      transport =
          ApacheHttpClient5TransportBuilder.builder(
                  new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort()))
              .build();

      osClient = new OpenSearchClient(transport);
    } catch (final Exception e) {
      LOGGER.error("Failed to create OpenSearch client", e);
      throw new RuntimeException(e);
    }

    testHelper =
        new ExporterMigrationTestHelper(
            osClient, OS_NETWORK_ALIAS, network, osContainer.getHttpHostAddress(), dataDir, LOGGER);
  }

  @AfterAll
  static void tearDown() throws Exception {
    if (transport != null) {
      transport.close();
    }
    if (osContainer != null) {
      osContainer.stop();
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
  @Tag("nightly")
  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  void shouldCompleteUpgradeWithBacklogAndExportAllRecordsAgainstReleasePatches(
      final String version) throws Exception {
    testHelper.shouldCompleteUpgradeWithBacklogAndExportAllRecords(version);
  }
}
