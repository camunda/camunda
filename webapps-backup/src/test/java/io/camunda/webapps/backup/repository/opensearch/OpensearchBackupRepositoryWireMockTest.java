/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup.repository.opensearch;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.BackupRepositoryPropsRecord;
import io.camunda.webapps.backup.repository.WebappsSnapshotNameProvider;
import java.io.IOException;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

@WireMockTest
class OpensearchBackupRepositoryWireMockTest {

  private static final String REPOSITORY_NAME = "camunda-backup";
  private static final String VALIDATION_ERROR_MESSAGE =
      String.format(
          "Exception occurred when validating existence of repository with name [%s].",
          REPOSITORY_NAME);

  private ApacheHttpClient5Transport transport;
  private OpensearchBackupRepository repository;

  @BeforeEach
  void setup(final WireMockRuntimeInfo wmRuntimeInfo) {
    final var host = new HttpHost("localhost", wmRuntimeInfo.getHttpPort());
    transport =
        ApacheHttpClient5TransportBuilder.builder(host)
            .setMapper(new JacksonJsonpMapper())
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder)
            .build();

    final var syncClient = new OpenSearchClient(transport);
    final var asyncClient = new OpenSearchAsyncClient(transport);
    final var backupProps =
        new BackupRepositoryPropsRecord(
            "1.0",
            REPOSITORY_NAME,
            0,
            BackupRepositoryProps.defaultIncompleteCheckTimeoutInSeconds());

    repository =
        new OpensearchBackupRepository(
            syncClient, asyncClient, backupProps, new WebappsSnapshotNameProvider());
  }

  @AfterEach
  void teardown() throws IOException {
    if (transport != null) {
      transport.close();
    }
  }

  @Test
  void validateRepositoryExistsWorksWhenLocationIsMissing() {
    // Simulates AWS OpenSearch S3 repo response — no 'location' field
    stubFor(
        get(urlEqualTo(String.format("/_snapshot/%s", REPOSITORY_NAME)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        String.format(
                            """
                        {
                          "%s": {
                            "type": "s3",
                            "settings": {
                              "bucket": "my-bucket",
                              "region": "us-east-1"
                            }
                          }
                        }
                        """,
                            REPOSITORY_NAME))));

    // This should return without errors
    assertThatNoException().isThrownBy(() -> repository.validateRepositoryExists("camunda-backup"));
  }

  @Test
  void validateRepositoryExistsFailsWhenTypeIsMissing() {
    stubFor(
        get(urlEqualTo(String.format("/_snapshot/%s", REPOSITORY_NAME)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    // field "location" present,
                    // field "type" missing
                    .withBody(
                        String.format(
                            """
                        {
                          "%s": {
                            "settings": {
                              "bucket": "my-bucket",
                              "region": "us-east-1",
                              "location": "somelocation"
                            }
                          }
                        }
                        """,
                            REPOSITORY_NAME))));

    // This should return without errors
    assertThatException()
        .isThrownBy(() -> repository.validateRepositoryExists(REPOSITORY_NAME))
        .withMessage(VALIDATION_ERROR_MESSAGE);
  }
}
