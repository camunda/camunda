/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.search.test.utils.SearchDBExtension.ENGINE_CLIENT_TEST_MARKERS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs against a secured cluster, which the default containers of {@link
 * ElasticsearchEngineClientIT} are not, to produce a real 403 from the cluster health API.
 */
@Testcontainers
public class ElasticsearchEngineClientPermissionIT {

  private static final String ELASTIC_USER = "elastic";
  private static final String ELASTIC_PASSWORD = "PASSWORD";
  private static final String ROLE_WITHOUT_MONITOR = "no_cluster_monitor";
  private static final String USER_WITHOUT_MONITOR = "no_cluster_monitor_user";
  private static final String USER_WITHOUT_MONITOR_PASSWORD = "Restricted_Pass123!";

  /** See {@code CamundaExporterAuthenticationIT} for why the wait strategy is authenticated. */
  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefaultElasticsearchContainer()
          .withPassword(ELASTIC_PASSWORD)
          .withEnv("xpack.security.enabled", "true")
          .waitingFor(
              TestSearchContainers.waitForClusterHealth()
                  .withBasicCredentials(ELASTIC_USER, ELASTIC_PASSWORD));

  @Test
  void shouldFailAsNonRetryableWhenClusterMonitorPrivilegeIsMissing() throws IOException {
    // given an existing index and a user who can access indices but lacks the cluster privilege
    // required by the cluster health API
    final var indexName = "await_forbidden-" + ENGINE_CLIENT_TEST_MARKERS;
    try (final var adminClient = createConnector(ELASTIC_USER, ELASTIC_PASSWORD).createClient()) {
      adminClient.indices().create(req -> req.index(indexName));
      adminClient
          .security()
          .putRole(
              role ->
                  role.name(ROLE_WITHOUT_MONITOR)
                      .indices(indices -> indices.names("*").privileges("all")));
      adminClient
          .security()
          .putUser(
              user ->
                  user.username(USER_WITHOUT_MONITOR)
                      .password(USER_WITHOUT_MONITOR_PASSWORD)
                      .roles(ROLE_WITHOUT_MONITOR));
    }

    final var restrictedConnector =
        createConnector(USER_WITHOUT_MONITOR, USER_WITHOUT_MONITOR_PASSWORD);
    try (final var restrictedEngineClient =
        new ElasticsearchEngineClient(
            restrictedConnector.createClient(), restrictedConnector.objectMapper())) {

      // when / then
      assertThatThrownBy(() -> restrictedEngineClient.awaitActivePrimaries(List.of(indexName)))
          .isInstanceOf(SearchEngineHealthCheckPermissionException.class);
      assertThatThrownBy(restrictedEngineClient::isHealthy)
          .isInstanceOf(SearchEngineHealthCheckPermissionException.class);
    }
  }

  private static ElasticsearchConnector createConnector(
      final String username, final String password) {
    final var config = new ConnectConfiguration();
    config.setUrl(CONTAINER.getHttpHostAddress());
    config.setUsername(username);
    config.setPassword(password);
    return new ElasticsearchConnector(config);
  }
}
