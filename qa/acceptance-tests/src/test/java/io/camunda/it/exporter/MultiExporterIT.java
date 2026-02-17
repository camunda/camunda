/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbConfigurator;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.CloseHelper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for verifying that process instances are correctly exported to RDBMS and
 * Elasticsearch.
 *
 * <p>This is intentionally no {@link io.camunda.qa.util.multidb.MultiDbTest}, as it always tests
 * the combination of RDBMS as secondary storage and Elasticsearch as exporter, and does not need to
 * be run with different database combinations.
 *
 * <p>This test sets up a Camunda process engine, using RDBMS as secondary storage and an
 * Elasticsearch Testcontainer, deploys a process, starts an instance, and verifies that the process
 * instance is both visible in the RDBMS and indexed in Elasticsearch.
 */
@Testcontainers
@ZeebeIntegration
public class MultiExporterIT {
  private static final String PROCESS_ID = "PROCESS_WITH_JOB_BASED_USERTASK";

  @Container
  private static final GenericContainer<?> SEARCH_CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withStartupTimeout(Duration.ofMinutes(5))
          .withEnv("path.repo", "~/");

  protected CamundaClient camundaClient;

  @TestZeebe(autoStart = false)
  protected TestStandaloneApplication<?> testStandaloneApplication;

  protected TestEsClient documentClient;

  @AfterEach
  public void tearDown() {
    CloseHelper.quietCloseAll(documentClient, camundaClient);
  }

  @Test
  void name() throws Exception {
    setup();

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

    // Wait until all process instances are indexed in Elasticsearch
    await()
        .alias("Wait until all process instances are visible in Elasticsearch")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              final var esKeys = fetchProcessInstanceKeysFromElasticsearch();
              assertThat(esKeys).hasSize(5);
              assertThat(esKeys).containsExactlyInAnyOrderElementsOf(expectedProcessInstanceKeys);
            });
  }

  /**
   * Fetches all processInstanceKey values from the first process-instance index in Elasticsearch.
   *
   * @return Set of processInstanceKey values (as Longs if possible)
   */
  private Set<Object> fetchProcessInstanceKeysFromElasticsearch() throws IOException {
    final String indexName = findProcessInstanceIndexName();
    if (indexName == null) {
      return Set.of();
    }

    // Increase max_result_window to ensure we can fetch all documents in one query (for testing
    // purposes)
    documentClient
        .esClient
        .indices()
        .putSettings(b -> b.index(indexName).settings(s -> s.maxResultWindow(10000)));

    return documentClient
        .esClient
        .search(b -> b.index(indexName).size(10000), Map.class)
        .hits()
        .hits()
        .stream()
        .map(Hit::source)
        .map(source -> source.get("value"))
        .map(value -> value instanceof Map ? ((Map<?, ?>) value).get("processInstanceKey") : null)
        .collect(Collectors.toSet());
  }

  /**
   * Finds the first Elasticsearch index containing process-instance documents.
   *
   * @return Index name or null if not found
   */
  private String findProcessInstanceIndexName() {
    try {
      return documentClient.esClient.cat().indices().valueBody().stream()
          .map(IndicesRecord::index)
          .filter(name -> name != null && name.contains("process-instance"))
          .findFirst()
          .orElse(null);
    } catch (final IOException e) {
      return null;
    }
  }

  private void setup() throws Exception {
    testStandaloneApplication =
        new TestCamundaApplication()
            .withAuthenticationMethod(AuthenticationMethod.BASIC)
            .withUnauthenticatedAccess();

    final MultiDbConfigurator configurator = new MultiDbConfigurator(testStandaloneApplication);

    // configure the app to use RDBMS as secondary storage, with an in-memory H2 database
    configurator.configureRDBMSSupport(false, "jdbc:h2:mem:camunda", "sa", "", "org.h2.Driver");

    final String elasticsearchUrl =
        String.format(
            "http://%s:%d", SEARCH_CONTAINER.getHost(), SEARCH_CONTAINER.getMappedPort(9200));

    // configure the app to use the Elasticsearch exporter, pointing to the Testcontainer
    testStandaloneApplication.withExporter(
        ElasticsearchExporter.class.getSimpleName().toLowerCase(),
        cfg -> {
          cfg.setClassName(ElasticsearchExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "url", elasticsearchUrl,
                  "index", Map.of("prefix", "zeebe-index"),
                  "bulk", Map.of("size", 1)));
        });

    testStandaloneApplication.start().awaitCompleteTopology();

    camundaClient =
        testStandaloneApplication
            .newClientBuilder()
            .defaultRequestTimeout(Duration.ofSeconds(1))
            .build();

    documentClient = new TestEsClient(elasticsearchUrl);

    // Ensure Elasticsearch is clean before starting the test
    documentClient.deleteAllIndices();
  }

  public static class TestEsClient implements AutoCloseable {

    final RestClient restClient;
    final ElasticsearchClient esClient;

    public TestEsClient(final String url) {
      restClient = RestClient.builder(HttpHost.create(url)).build();
      esClient =
          new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    public void deleteAllIndices() throws IOException {
      esClient.indices().delete(DeleteIndexRequest.of(b -> b.index("*")));
    }

    @Override
    public void close() throws Exception {
      restClient.close();
    }
  }
}
