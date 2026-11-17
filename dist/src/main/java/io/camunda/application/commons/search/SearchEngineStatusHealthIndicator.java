/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.jackson.JacksonConfiguration;
import io.camunda.search.connect.tenant.SearchClients;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports the cluster health of one physical tenant's search engine. Purely informational: it is
 * deliberately kept out of the liveness, readiness and startup groups, so that a search engine that
 * turns unhealthy after startup is visible on {@code /actuator/health} without taking the node out
 * of rotation or restarting it. Readiness is answered by {@link SchemaReadinessCheck} instead.
 *
 * <p>What counts as healthy is not decided here: it is {@link SearchEngineClient#isHealthy()}, the
 * provider's own check, which is also the one the schema initializer applies at startup. Anything
 * Elasticsearch-specific or OpenSearch-specific therefore stays behind that interface, and the
 * endpoint cannot answer differently from the startup gate.
 */
public final class SearchEngineStatusHealthIndicator extends AbstractHealthIndicator {

  private final SearchEngineClient client;

  SearchEngineStatusHealthIndicator(final SearchEngineClient client) {
    super("Search engine cluster health check failed");
    this.client = client;
  }

  /**
   * One contributor per physical tenant, each reading through the client that tenant already owns
   * rather than opening a connection pool of its own. A single physical tenant keeps the flat
   * shape; more than one is reported as a composite keyed by physical tenant id.
   */
  public static HealthContributor forPhysicalTenants(
      final SearchClients searchClients,
      final Map<String, SearchEngineConfiguration> configurationsByTenant) {
    final var indicators = new LinkedHashMap<String, HealthIndicator>();
    searchClients
        .esClients()
        .forEach(
            (tenantId, client) ->
                indicators.put(
                    tenantId,
                    indicatorFor(
                        configurationsByTenant.get(tenantId),
                        objectMapper -> new ElasticsearchEngineClient(client, objectMapper))));
    searchClients
        .osClients()
        .forEach(
            (tenantId, client) ->
                indicators.put(
                    tenantId,
                    indicatorFor(
                        configurationsByTenant.get(tenantId),
                        objectMapper -> new OpensearchEngineClient(client, objectMapper))));
    if (indicators.size() == 1) {
      return indicators.values().iterator().next();
    }
    return CompositeHealthContributor.fromMap(indicators);
  }

  /**
   * The engine client is built over the tenant's existing client, and is never closed here: closing
   * it would close a client this indicator borrows rather than owns. Its object mapper serves the
   * schema requests the same client also answers; the health request does not use it.
   */
  private static HealthIndicator indicatorFor(
      final SearchEngineConfiguration configuration,
      final Function<ObjectMapper, SearchEngineClient> engineClient) {
    if (!configuration.schemaManager().isHealthCheckEnabled()) {
      return disabled();
    }
    return new SearchEngineStatusHealthIndicator(
        engineClient.apply(new JacksonConfiguration(configuration.connect()).createObjectMapper()));
  }

  /**
   * A tenant that opted out of the cluster health check - the escape hatch for a service account
   * without the {@code monitor} cluster privilege - reports UP without calling an endpoint that is
   * known to answer 403, naming the reason in its details.
   */
  static HealthIndicator disabled() {
    return () -> Health.up().withDetail("clusterHealthCheck", "disabled").build();
  }

  @Override
  protected void doHealthCheck(final Health.Builder builder) {
    if (client.isHealthy()) {
      builder.up();
    } else {
      builder.down();
    }
  }
}
