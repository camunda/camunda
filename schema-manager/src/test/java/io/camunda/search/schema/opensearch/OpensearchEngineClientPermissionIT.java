/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.opensearch;

import static io.camunda.search.test.utils.SearchDBExtension.ENGINE_CLIENT_TEST_MARKERS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.SearchEngineHealthCheckPermissionException;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opensearch.client.opensearch.security.OpenSearchSecurityClient;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs against a secured cluster, which the default containers of {@link OpensearchEngineClientIT}
 * are not, to produce a real 403 from the cluster health API.
 */
@Testcontainers
public class OpensearchEngineClientPermissionIT {

  private static final String SERVER_CERTIFICATE = "/usr/share/opensearch/config/esnode.pem";
  private static final String ADMIN_PASSWORD = "P@a$5w0rd";
  private static final String ROLE_WITHOUT_MONITOR = "no_cluster_monitor";
  private static final String USER_WITHOUT_MONITOR = "no_cluster_monitor_user";
  private static final String USER_WITHOUT_MONITOR_PASSWORD = "Restricted_Pass123!";

  @Container
  private static final OpenSearchContainer<?> CONTAINER =
      TestSearchContainers.createDefaultOpensearchContainer()
          .withSecurityEnabled()
          .withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", ADMIN_PASSWORD);

  @Test
  void shouldFailAsNonRetryableWhenClusterMonitorPrivilegeIsMissing(@TempDir final Path tempDir)
      throws IOException {
    // given an existing index and a user who can access indices but lacks the cluster permission
    // required by the cluster health API
    final var indexName = "await_forbidden-" + ENGINE_CLIENT_TEST_MARKERS;
    // the connector only trusts a self-signed certificate that is provided explicitly
    final var certificate = tempDir.resolve("esnode.pem");
    CONTAINER.copyFileFromContainer(SERVER_CERTIFICATE, certificate.toString());
    final var adminClient =
        createConnector(CONTAINER.getUsername(), ADMIN_PASSWORD, certificate).createClient();
    // OpenSearchClient is not closeable itself, closing its transport releases the connections
    try (final var adminTransport = adminClient._transport()) {
      adminClient.indices().create(req -> req.index(indexName));

      final var security = new OpenSearchSecurityClient(adminTransport);
      security.createRole(
          role ->
              role.role(ROLE_WITHOUT_MONITOR)
                  .indexPermissions(
                      permission -> permission.indexPatterns("*").allowedActions("*")));
      security.createUser(
          user ->
              user.username(USER_WITHOUT_MONITOR)
                  .password(USER_WITHOUT_MONITOR_PASSWORD)
                  .opendistroSecurityRoles(ROLE_WITHOUT_MONITOR));
    }

    final var restrictedConnector =
        createConnector(USER_WITHOUT_MONITOR, USER_WITHOUT_MONITOR_PASSWORD, certificate);
    try (final var restrictedEngineClient =
        new OpensearchEngineClient(
            restrictedConnector.createClient(), restrictedConnector.objectMapper())) {

      // when / then
      assertThatThrownBy(() -> restrictedEngineClient.awaitActivePrimaries(List.of(indexName)))
          .isInstanceOf(SearchEngineHealthCheckPermissionException.class);
      assertThatThrownBy(restrictedEngineClient::isHealthy)
          .isInstanceOf(SearchEngineHealthCheckPermissionException.class);
    }
  }

  private static OpensearchConnector createConnector(
      final String username, final String password, final Path certificate) {
    final var config = new ConnectConfiguration();
    config.setUrl(CONTAINER.getHttpHostAddress());
    config.setUsername(username);
    config.setPassword(password);
    config.getSecurity().setEnabled(true);
    config.getSecurity().setCertificatePath(certificate.toString());
    return new OpensearchConnector(config);
  }
}
