/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.exporter.CamundaExporter;
import io.camunda.it.document.DocumentClient;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbConfigurator;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for verifying that process instances are correctly exported to RDBMS and
 * Elasticsearch/Opensearch.
 *
 * <p>This is intentionally no {@link io.camunda.qa.util.multidb.MultiDbTest}, as it always tests
 * the combination of RDBMS as secondary storage and Elasticsearch-/OpensearchExporter as exporter,
 * and does not need to be run with different database combinations.
 *
 * <p>This test sets up a Camunda process engine, using RDBMS as secondary storage and an
 * Elasticsearch/Opensearch Testcontainer, deploys a process, starts an instance, and verifies that
 * the process instance is both visible in the RDBMS and indexed in Elasticsearch/Opensearch.
 */
@Testcontainers
@ZeebeIntegration
public class MultiExporterIT {

  public static final String INDEX_PREFIX = "zeebe-index";
  private static final String PROCESS_ID = "PROCESS_WITH_JOB_BASED_USERTASK";

  @TestZeebe(autoStart = false)
  protected TestStandaloneApplication<?> testStandaloneApplication;

  protected CamundaClient camundaClient;
  protected DocumentClient documentClient;
  private GenericContainer<?> searchContainer;

  @AfterEach
  public void tearDown() {
    CloseHelper.quietCloseAll(documentClient, camundaClient);
    testStandaloneApplication.stop();
    if (searchContainer != null) {
      searchContainer.stop();
    }
  }

  @ParameterizedTest
  @CsvSource({
    "ELASTICSEARCH",
    "OPENSEARCH",
    // currently this fails when enabled, cf. https://github.com/camunda/camunda/issues/45692
    // "CAMUNDA"
  })
  void shouldExportToRdbmsAndDocumentStore(final ExporterType exporterType) throws Exception {
    setup(exporterType);

    // Deploy the process definition
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("process/process_job_based_user_task.bpmn")
        .send()
        .join();

    // Start 5 process instances and collect their keys
    final Set<Long> expectedProcessInstanceKeys =
        java.util.stream.IntStream.range(0, 5)
            .mapToObj(
                i ->
                    camundaClient
                        .newCreateInstanceCommand()
                        .bpmnProcessId(PROCESS_ID)
                        .latestVersion()
                        .send()
                        .join()
                        .getProcessInstanceKey())
            .collect(Collectors.toSet());

    // Wait until all process instances are visible in the RDBMS
    await()
        .alias("Wait until all process instances are visible in RDBMS")
        .untilAsserted(
            () -> {
              final var rdbmsSearchResult =
                  camundaClient.newProcessInstanceSearchRequest().send().join();
              assertThat(rdbmsSearchResult.items()).hasSize(5);
              final var foundKeys =
                  rdbmsSearchResult.items().stream()
                      .map(item -> item.getProcessInstanceKey())
                      .collect(Collectors.toSet());
              assertThat(foundKeys)
                  .containsExactlyInAnyOrderElementsOf(expectedProcessInstanceKeys);
            });

    // Wait until all process instances are indexed in Elasticsearch/Opensearch
    await()
        .alias("Wait until all process instances are visible in Elasticsearch")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              final var keys = fetchProcessInstanceKeysFromDocumentStore();
              assertThat(keys).hasSize(5);
              assertThat(keys).containsExactlyInAnyOrderElementsOf(expectedProcessInstanceKeys);
            });
  }

  /**
   * Fetches all processInstanceKey values from the first process-instance index in Elasticsearch.
   *
   * @return Set of processInstanceKey values (as Longs if possible)
   */
  private Set<Object> fetchProcessInstanceKeysFromDocumentStore() throws IOException {
    final String indexName = findProcessInstanceIndexName();
    if (indexName == null) {
      return Set.of();
    }

    documentClient.setMaxResultWindow(indexName, 10000);
    documentClient.refresh(indexName);

    return documentClient.search(indexName, 10000).stream()
        .map(source -> source.get("value"))
        .map(
            value -> {
              if (value instanceof final Map<?, ?> map) {
                // Try direct key
                return map.get("processInstanceKey");
              } else if (value instanceof final scala.collection.immutable.HashMap scalaMap) {
                // Convert Scala map to Java map and try key
                final var key = scalaMap.get("processInstanceKey");
                if (key != null) {
                  return key.get();
                }
              }
              return null;
            })
        .collect(Collectors.toSet());
  }

  /**
   * Finds the first index containing process-instance documents.
   *
   * @return Index name or null if not found
   */
  private String findProcessInstanceIndexName() {
    try {
      return documentClient.cat(INDEX_PREFIX).stream()
          .filter(name -> name != null && name.contains("process-instance"))
          .findFirst()
          .orElse(null);
    } catch (final IOException e) {
      return null;
    }
  }

  private void setup(final ExporterType exporterType) throws Exception {
    // Start the correct container based on exporterType
    if (Objects.requireNonNull(exporterType) == ExporterType.OPENSEARCH) {
      searchContainer =
          TestSearchContainers.createDefaultOpensearchContainer()
              .withStartupTimeout(Duration.ofMinutes(5));
    } else {
      searchContainer =
          TestSearchContainers.createDefeaultElasticsearchContainer()
              .withStartupTimeout(Duration.ofMinutes(5))
              .withEnv("path.repo", "~/");
    }
    searchContainer.start();

    testStandaloneApplication =
        new TestCamundaApplication()
            .withAuthenticationMethod(AuthenticationMethod.BASIC)
            .withUnauthenticatedAccess();

    final MultiDbConfigurator configurator = new MultiDbConfigurator(testStandaloneApplication);

    // configure the app to use RDBMS as secondary storage, with an in-memory H2 database
    configurator.configureRDBMSSupport(false, "jdbc:h2:mem:camunda", "sa", "", "org.h2.Driver");

    final String containerUrl =
        String.format(
            "http://%s:%d", searchContainer.getHost(), searchContainer.getMappedPort(9200));

    final DatabaseType dbType =
        switch (exporterType) {
          case ELASTICSEARCH -> { // configure the app to use the Elasticsearch exporter
            testStandaloneApplication.withExporter(
                ElasticsearchExporter.class.getSimpleName().toLowerCase(),
                cfg -> {
                  cfg.setClassName(ElasticsearchExporter.class.getName());
                  cfg.setArgs(
                      Map.of(
                          "url", containerUrl,
                          "index", Map.of("prefix", INDEX_PREFIX),
                          "bulk", Map.of("size", 1)));
                });
            yield DatabaseType.ELASTICSEARCH;
          }
          case CAMUNDA -> {
            final Map<String, Object> elasticsearchProperties = new HashMap<>();
            elasticsearchProperties.put("camunda.tasklist.zeebeElasticsearch.prefix", INDEX_PREFIX);
            elasticsearchProperties.put(CREATE_SCHEMA_PROPERTY, true);
            testStandaloneApplication.withAdditionalProperties(elasticsearchProperties);

            testStandaloneApplication.withExporter(
                CamundaExporter.class.getSimpleName().toLowerCase(),
                cfg -> {
                  cfg.setClassName(CamundaExporter.class.getName());
                  cfg.setArgs(
                      Map.of(
                          "createSchema",
                          true,
                          "connect",
                          Map.of(
                              "url",
                              containerUrl,
                              "indexPrefix",
                              INDEX_PREFIX,
                              "type",
                              io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH),
                          "index",
                          Map.of("prefix", INDEX_PREFIX),
                          "history",
                          Map.of(
                              "waitPeriodBeforeArchiving",
                              "1s",
                              "delayBetweenRuns",
                              "1000",
                              "maxDelayBetweenRuns",
                              "1000",
                              "retention",
                              Map.of(
                                  "enabled",
                                  false,
                                  "policyName",
                                  INDEX_PREFIX + "-ilm",
                                  "minimumAge",
                                  "0s",
                                  "usageMetricsPolicyName",
                                  INDEX_PREFIX + "-usage-metrics-ilm",
                                  "usageMetricsMinimumAge",
                                  "0s")),
                          "bulk",
                          Map.of("size", 1)));
                });
            yield DatabaseType.ELASTICSEARCH;
          }
          case OPENSEARCH -> {
            testStandaloneApplication.withExporter(
                OpensearchExporter.class.getSimpleName().toLowerCase(),
                cfg -> {
                  cfg.setClassName(OpensearchExporter.class.getName());
                  cfg.setArgs(
                      Map.of(
                          "url",
                          containerUrl,
                          "index",
                          Map.of("prefix", INDEX_PREFIX),
                          "bulk",
                          Map.of("size", 1),
                          "authentication",
                          Map.of("username", "admin", "password", "admin")));
                });
            yield DatabaseType.OPENSEARCH;
          }
        };

    // Use DocumentClient abstraction
    final Executor executor = Runnable::run; // simple executor for tests
    documentClient = DocumentClient.create(containerUrl, dbType, executor);

    // Ensure indices are clean before starting the test
    documentClient.deleteAllIndices(INDEX_PREFIX);

    testStandaloneApplication.start().awaitCompleteTopology();

    camundaClient =
        testStandaloneApplication
            .newClientBuilder()
            .defaultRequestTimeout(Duration.ofSeconds(1))
            .build();

  }

  public enum ExporterType {
    ELASTICSEARCH,
    CAMUNDA,
    OPENSEARCH
  }
}
