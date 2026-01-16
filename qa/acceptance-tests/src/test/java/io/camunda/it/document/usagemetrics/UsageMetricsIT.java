/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.document.usagemetrics;

import static io.camunda.webapps.schema.descriptors.ComponentNames.CAMUNDA;
import static io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate.INDEX_NAME;
import static io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate.INDEX_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.it.document.DocumentClient;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbConfigurator;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Usage metrics integration test for both Elasticsearch and OpenSearch. */
@Testcontainers
@ZeebeIntegration
@Execution(ExecutionMode.SAME_THREAD)
public class UsageMetricsIT {

  private static final String REPOSITORY_NAME = "um-test-repository";
  private static final String INDEX_PREFIX = "test";
  private static final String USAGE_METRICS_INDEX =
      INDEX_PREFIX + "-" + CAMUNDA + "-" + INDEX_NAME + "-" + INDEX_VERSION + "_";
  private static final OffsetDateTime NOW = OffsetDateTime.now();
  private static final String TENANT1 = "tenant1";
  @AutoClose private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

  @TestZeebe(autoStart = false)
  protected TestCamundaApplication testCamundaApplication;

  protected DocumentClient webappsDBClient;
  protected CamundaClient camundaClient;

  // cannot be a @Container because it's initialized in setup()
  private GenericContainer<?> searchContainer;

  @AfterEach
  public void tearDown() {
    CloseHelper.quietCloseAll(webappsDBClient, camundaClient, searchContainer);
  }

  private void setup(final UsageMetricsTestConfig config) throws Exception {
    testCamundaApplication =
        new TestCamundaApplication()
            .withAuthenticationMethod(AuthenticationMethod.BASIC)
            .withUnauthenticatedAccess();
    final var configurator = new MultiDbConfigurator(testCamundaApplication);

    final String dbUrl;
    searchContainer =
        switch (config.databaseType) {
          case ELASTICSEARCH -> {
            final var container =
                TestSearchContainers.createDefeaultElasticsearchContainer()
                    .withStartupTimeout(Duration.ofMinutes(5))
                    // location of the repository that will be used for snapshots
                    .withEnv("path.repo", "~/");
            container.start();
            dbUrl = "http://" + container.getHttpHostAddress();

            // configure the app for Elasticsearch
            configurator.configureElasticsearchSupportIncludingOldExporter(dbUrl, INDEX_PREFIX);
            yield container;
          }
          case OPENSEARCH -> {
            final var container =
                TestSearchContainers.createDefaultOpensearchContainer()
                    .withStartupTimeout(Duration.ofMinutes(5))
                    // location of the repository that will be used for snapshots
                    .withEnv("path.repo", "~/");
            container.start();
            dbUrl = container.getHttpHostAddress();

            // configure the app for OpenSearch
            configurator.configureOpenSearchSupportIncludingOldExporter(
                dbUrl, INDEX_PREFIX, "admin", "admin");
            yield container;
          }
          default ->
              throw new IllegalArgumentException(
                  "Unsupported database type: " + config.databaseType);
        };

    testCamundaApplication.start().awaitCompleteTopology();

    camundaClient =
        testCamundaApplication
            .newClientBuilder()
            .defaultRequestTimeout(Duration.ofSeconds(30))
            .build();

    webappsDBClient = DocumentClient.create(dbUrl, config.databaseType, EXECUTOR);
    webappsDBClient.createRepository(REPOSITORY_NAME);
  }

  public static Stream<UsageMetricsTestConfig> sources() {
    final var configs = new ArrayList<UsageMetricsTestConfig>();
    for (final var db : List.of(DatabaseType.ELASTICSEARCH, DatabaseType.OPENSEARCH)) {
      configs.add(new UsageMetricsTestConfig(db));
    }
    return configs.stream();
  }

  @ParameterizedTest
  @MethodSource(value = {"sources"})
  public void shouldFilterLongIntervalMetrics(final UsageMetricsTestConfig config)
      throws Exception {
    // given
    setup(config);
    testCamundaApplication.awaitCompleteTopology();

    final OffsetDateTime nowMinus3Weeks = NOW.minusWeeks(3);

    // create usage metrics with overlapping intervals
    writeMetric(UsageMetricsEventType.RPI, NOW.minusWeeks(5), nowMinus3Weeks, TENANT1, 4L);
    writeMetric(UsageMetricsEventType.RPI, NOW.minusWeeks(4), NOW.minusWeeks(2), TENANT1, 4L);
    writeMetric(UsageMetricsEventType.RPI, NOW.minusWeeks(2), NOW, TENANT1, 4L);
    writeMetric(UsageMetricsEventType.RPI, NOW.minusWeeks(1), NOW, TENANT1, 4L);

    // Wait for data to be indexed
    refreshIndices();

    // when - query through Camunda client API
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final var response =
                  camundaClient
                      .newUsageMetricsRequest(nowMinus3Weeks, NOW.plusMinutes(1))
                      .send()
                      .join();

              // then - verify the aggregated metrics
              assertThat(response.getProcessInstances()).isEqualTo(16L);
              assertThat(response.getAssignees()).isEqualTo(0L);
              assertThat(response.getActiveTenants()).isEqualTo(1L);
            });
  }

  private void writeMetric(
      UsageMetricsEventType eventType,
      OffsetDateTime startTime,
      OffsetDateTime endTime,
      String tenantId,
      long value)
      throws Exception {

    // Create a simple DTO for indexing that Jackson can easily serialize
    final var indexDocument =
        Map.of(
            "id", generateId(),
            "eventType", eventType.name(),
            "startTime", startTime.toString(),
            "endTime", endTime.toString(),
            "tenantId", tenantId,
            "eventValue", value,
            "partitionId", 0);

    // Index the simple map structure instead of the complex entity
    webappsDBClient.indexWithRetry(
        USAGE_METRICS_INDEX, (String) indexDocument.get("id"), indexDocument);
  }

  private void refreshIndices() throws Exception {
    // Refresh the indices to make data available for search
    webappsDBClient.refresh(USAGE_METRICS_INDEX);

    // Give search backend time to process the refresh
    Thread.sleep(100);
  }

  private String generateId() {
    return "metric-" + UUID.randomUUID();
  }

  public record UsageMetricsTestConfig(DatabaseType databaseType) {}
}
