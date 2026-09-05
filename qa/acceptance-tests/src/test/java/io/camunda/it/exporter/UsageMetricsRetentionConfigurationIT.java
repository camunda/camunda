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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbConfigurator;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.generic.Requests;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Acceptance test for #58237: proves that the unified-configuration usage-metrics retention
 * properties ({@code usage-metrics-policy-name} / {@code usage-metrics-minimum-age}) reach the ILM
 * (Elasticsearch) / ISM (OpenSearch) lifecycle policy that {@code SchemaManager} creates in the
 * search engine, instead of being silently ignored in favor of the hardcoded defaults
 * ("camunda-usage-metrics-retention-policy" / "730d").
 *
 * <p>Intentionally not a {@link io.camunda.qa.util.multidb.MultiDbTest}, for the same reason
 * documented on {@link MultiExporterIT}: this test always needs a real Elasticsearch/OpenSearch
 * cluster to assert against the lifecycle policy actually created there, and has no RDBMS
 * equivalent to parameterize over.
 */
@Testcontainers
@ZeebeIntegration
public class UsageMetricsRetentionConfigurationIT {

  private static final String INDEX_PREFIX = "retention-config-test";
  private static final String USAGE_METRICS_POLICY_NAME = INDEX_PREFIX + "-usage-metrics-ilm";
  private static final String USAGE_METRICS_MINIMUM_AGE = "0s";

  @TestZeebe(autoStart = false)
  protected TestStandaloneApplication<?> testStandaloneApplication;

  protected CamundaClient camundaClient;
  private GenericContainer<?> searchContainer;

  @AfterEach
  public void tearDown() {
    CloseHelper.quietCloseAll(camundaClient);
    testStandaloneApplication.stop();
    if (searchContainer != null) {
      searchContainer.stop();
    }
  }

  @ParameterizedTest
  @CsvSource({"ELASTICSEARCH", "OPENSEARCH"})
  void shouldApplyUnifiedConfigUsageMetricsRetentionToCreatedLifecyclePolicy(
      final SearchEngineType searchEngineType) throws Exception {
    // given - unified config sets the usage-metrics policy name/minimum-age to values that are
    // distinct from the hardcoded production defaults (see MultiDbConfigurator)
    final String containerUrl = setup(searchEngineType);

    // then - the real cluster's usage-metrics lifecycle policy reflects those configured values,
    // not the hardcoded defaults, proving they are no longer silently ignored
    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              if (searchEngineType == SearchEngineType.ELASTICSEARCH) {
                assertElasticsearchUsageMetricsPolicy(containerUrl);
              } else {
                assertOpenSearchUsageMetricsPolicy(containerUrl);
              }
            });
  }

  private void assertElasticsearchUsageMetricsPolicy(final String containerUrl) throws Exception {
    final var config = new ConnectConfiguration();
    config.setUrl(containerUrl);
    final ElasticsearchClient client = new ElasticsearchConnector(config).createClient();
    try {
      final var policy = client.ilm().getLifecycle(req -> req.name(USAGE_METRICS_POLICY_NAME));

      assertThat(policy.result()).containsKey(USAGE_METRICS_POLICY_NAME);
      assertThat(
              policy
                  .result()
                  .get(USAGE_METRICS_POLICY_NAME)
                  .policy()
                  .phases()
                  .delete()
                  .minAge()
                  .time())
          .isEqualTo(USAGE_METRICS_MINIMUM_AGE);
    } finally {
      CloseHelper.quietClose(client._transport());
    }
  }

  private void assertOpenSearchUsageMetricsPolicy(final String containerUrl) throws Exception {
    final var config = new ConnectConfiguration();
    config.setUrl(containerUrl);
    config.setUsername("admin");
    config.setPassword("admin");
    final OpenSearchClient client = new OpensearchConnector(config).createClient();
    try {
      final ObjectMapper objectMapper =
          ((JacksonJsonpMapper) client._transport().jsonpMapper()).objectMapper();

      final var request =
          Requests.builder()
              .method("GET")
              .endpoint("/_plugins/_ism/policies/" + USAGE_METRICS_POLICY_NAME)
              .build();

      try (final var response = client.generic().execute(request)) {
        assertThat(response.getStatus()).isEqualTo(200);
        final var policyJson = objectMapper.readTree(response.getBody().get().bodyAsString());
        final var minIndexAge =
            policyJson
                .path("policy")
                .path("states")
                .path(0)
                .path("transitions")
                .path(0)
                .path("conditions")
                .path("min_index_age")
                .asText();
        assertThat(minIndexAge).isEqualTo(USAGE_METRICS_MINIMUM_AGE);
      }
    } finally {
      CloseHelper.quietClose(client._transport());
    }
  }

  private String setup(final SearchEngineType searchEngineType) throws Exception {
    testStandaloneApplication =
        new TestCamundaApplication()
            .withAuthenticationMethod(AuthenticationMethod.BASIC)
            .withUnauthenticatedAccess();
    final var configurator = new MultiDbConfigurator(testStandaloneApplication);

    final String containerUrl;
    switch (searchEngineType) {
      case ELASTICSEARCH -> {
        searchContainer =
            TestSearchContainers.createDefaultElasticsearchContainer()
                .withStartupTimeout(Duration.ofMinutes(5))
                .withEnv("path.repo", "~/");
        searchContainer.start();
        containerUrl =
            String.format(
                "http://%s:%d", searchContainer.getHost(), searchContainer.getMappedPort(9200));
        configurator.configureElasticsearchSupport(containerUrl, INDEX_PREFIX, true);
      }
      case OPENSEARCH -> {
        searchContainer =
            TestSearchContainers.createDefaultOpensearchContainer()
                .withStartupTimeout(Duration.ofMinutes(5));
        searchContainer.start();
        containerUrl =
            String.format(
                "http://%s:%d", searchContainer.getHost(), searchContainer.getMappedPort(9200));
        configurator.configureOpenSearchSupport(
            containerUrl, INDEX_PREFIX, "admin", "admin", true, false);
      }
      default ->
          throw new IllegalArgumentException("Unsupported search engine type: " + searchEngineType);
    }

    testStandaloneApplication.start().awaitCompleteTopology();

    camundaClient =
        testStandaloneApplication
            .newClientBuilder()
            .defaultRequestTimeout(Duration.ofSeconds(30))
            .build();

    return containerUrl;
  }

  public enum SearchEngineType {
    ELASTICSEARCH,
    OPENSEARCH
  }
}
