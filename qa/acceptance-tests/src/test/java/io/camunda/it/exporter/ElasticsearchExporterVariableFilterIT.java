/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbConfigurator;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpHost;
import org.awaitility.Awaitility;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

final class ElasticsearchExporterVariableFilterIT {

  public static final String TEST_PREFIX = "test-prefix";
  private static final String EXCLUSION_PROCESS = "exclusion-process";
  private static final String INCLUDED_ALLOWED = "included,allowed";
  private ElasticsearchContainer elasticsearchContainer;
  private TestCamundaApplication testCamundaApplication;
  private String esUrl;
  private String indexPrefix;

  @BeforeEach
  void setUp() {
    elasticsearchContainer =
        TestSearchContainers.createDefeaultElasticsearchContainer()
            .withEnv("action.destructive_requires_name", "false");
    elasticsearchContainer.start();
    esUrl = "http://" + elasticsearchContainer.getHttpHostAddress();
  }

  @AfterEach
  void tearDown() {
    if (testCamundaApplication != null) {
      testCamundaApplication.stop();
    }
    if (elasticsearchContainer != null) {
      elasticsearchContainer.stop();
    }
  }

  @Test
  void shouldNotExportVariableWhenNameDoesNotMatchInclusionFilter() {
    // given
    startApplicationWithVariableInclusion(INCLUDED_ALLOWED);

    try (final CamundaClient client = newCamundaClient()) {
      deployProcess(client);
      client
          .newCreateInstanceCommand()
          .bpmnProcessId(EXCLUSION_PROCESS)
          .latestVersion()
          .variables(
              Map.of(
                  "excludedVariable", "value",
                  "includedVariable", "value",
                  "allowed", "value"))
          .send()
          .join();

      // then
      Awaitility.await()
          .atMost(Duration.ofSeconds(15))
          .untilAsserted(
              () -> {
                assertThat(searchVariableCount("includedVariable")).isOne();
                assertThat(searchVariableCount("allowed")).isOne();
              });

      assertThat(searchVariableCount("excludedVariable")).isZero();
    }
  }

  @Test
  void shouldExportVariableWhenInclusionFilterIsNull() {
    // given
    startApplicationWithVariableInclusion(null);

    try (final CamundaClient client = newCamundaClient()) {
      deployProcess(client);
      client
          .newCreateInstanceCommand()
          .bpmnProcessId(EXCLUSION_PROCESS)
          .latestVersion()
          .variables(Map.of("excludedVariable", "value"))
          .send()
          .join();

      // then
      Awaitility.await()
          .atMost(Duration.ofSeconds(15))
          .untilAsserted(() -> assertThat(searchVariableCount("excludedVariable")).isPositive());
    }
  }

  private void startApplicationWithVariableInclusion(final String inclusion) {
    testCamundaApplication = new TestCamundaApplication();
    final var configurator = new MultiDbConfigurator(testCamundaApplication);
    configurator.configureElasticsearchSupport(esUrl, TEST_PREFIX);
    indexPrefix = configurator.zeebeIndexPrefix();

    final Map<String, Object> indexArgs = new HashMap<>();
    indexArgs.put("prefix", configurator.zeebeIndexPrefix());
    indexArgs.put("variable", true);
    indexArgs.put("variableNameInclusion", inclusion);

    testCamundaApplication.withExporter(
        ElasticsearchExporter.class.getSimpleName().toLowerCase(),
        cfg -> {
          cfg.setClassName(ElasticsearchExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "url", esUrl,
                  "index", indexArgs,
                  "bulk", Map.of("size", 1)));
        });

    testCamundaApplication.start().awaitCompleteTopology();
  }

  private CamundaClient newCamundaClient() {
    return testCamundaApplication.newClientBuilder().build();
  }

  private void deployProcess(final CamundaClient client) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(EXCLUSION_PROCESS).startEvent().endEvent().done();
    client
        .newDeployResourceCommand()
        .addProcessModel(process, EXCLUSION_PROCESS + ".bpmn")
        .send()
        .join();
  }

  private long searchVariableCount(final String variableName) {
    final String indexPattern = indexPrefix + "-variable*";

    try (final RestClient lowLevelClient = RestClient.builder(HttpHost.create(esUrl)).build();
        final RestClientTransport transport =
            new RestClientTransport(lowLevelClient, new JacksonJsonpMapper())) {

      final ElasticsearchClient esClient = new ElasticsearchClient(transport);

      final SearchResponse<Void> response =
          esClient.search(
              s ->
                  s.index(indexPattern)
                      .query(q -> q.match(m -> m.field("value.name").query(variableName))),
              Void.class);

      return response.hits().total() != null ? response.hits().total().value() : -1L;
    } catch (final IOException e) {
      return -1L;
    }
  }
}
