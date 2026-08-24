/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.search.connect.tenant.SearchClients;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.schema.SearchEngineHealthCheckPermissionException;
import io.camunda.search.schema.config.SchemaManagerConfiguration;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

class SearchEngineStatusHealthIndicatorTest {

  @Test
  void shouldReportUpWhenTheProviderReportsAHealthyCluster() {
    // given
    final var client = mock(SearchEngineClient.class);
    when(client.isHealthy()).thenReturn(true);

    // when
    final var health = new SearchEngineStatusHealthIndicator(client).health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldReportDownWhenTheProviderReportsAnUnhealthyCluster() {
    // given
    final var client = mock(SearchEngineClient.class);
    when(client.isHealthy()).thenReturn(false);

    // when
    final var health = new SearchEngineStatusHealthIndicator(client).health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void shouldReportDownWhenTheHealthCheckIsNotPermitted() {
    // given - the service account lacks the 'monitor' cluster privilege
    final var client = mock(SearchEngineClient.class);
    when(client.isHealthy())
        .thenThrow(new SearchEngineHealthCheckPermissionException("403", new RuntimeException()));

    // when
    final var health = new SearchEngineStatusHealthIndicator(client).health();

    // then - reported on the endpoint, not propagated to whoever called it
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsKey("error");
  }

  @Test
  void shouldReportOnePhysicalTenantFlat() {
    // given
    final var searchClients =
        new SearchClients(Map.of("<default>", mock(ElasticsearchClient.class)), Map.of(), Map.of());

    // when
    final var contributor =
        SearchEngineStatusHealthIndicator.forPhysicalTenants(
            searchClients, Map.of("<default>", configuration(true)));

    // then - a single physical tenant keeps the shape a single-tenant installation always had
    assertThat(contributor).isInstanceOf(SearchEngineStatusHealthIndicator.class);
  }

  @Test
  void shouldReportEachPhysicalTenantUnderItsOwnId() {
    // given
    final var searchClients =
        new SearchClients(
            Map.of("tenant-a", mock(ElasticsearchClient.class)),
            Map.of("tenant-b", mock(OpenSearchClient.class)),
            Map.of());

    // when
    final var contributor =
        SearchEngineStatusHealthIndicator.forPhysicalTenants(
            searchClients,
            Map.of("tenant-a", configuration(true), "tenant-b", configuration(true)));

    // then
    assertThat(contributor).isInstanceOf(CompositeHealthContributor.class);
    final var composite = (CompositeHealthContributor) contributor;
    assertThat(composite.getContributor("tenant-a")).isNotNull();
    assertThat(composite.getContributor("tenant-b")).isNotNull();
  }

  @Test
  void shouldReportUpWithoutCallingTheClusterWhenTheTenantOptedOut() {
    // given
    final var client = mock(ElasticsearchClient.class);
    final var searchClients = new SearchClients(Map.of("<default>", client), Map.of(), Map.of());

    // when
    final var contributor =
        SearchEngineStatusHealthIndicator.forPhysicalTenants(
            searchClients, Map.of("<default>", configuration(false)));

    // then - the opt-out for a service account without the 'monitor' cluster privilege must not
    // leave a permanently failing component on the endpoint
    final var health = ((HealthIndicator) contributor).health();
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("clusterHealthCheck", "disabled");
    verify(client, never()).cluster();
  }

  private static SearchEngineConfiguration configuration(final boolean healthCheckEnabled) {
    final var schemaManager = new SchemaManagerConfiguration();
    schemaManager.setHealthCheckEnabled(healthCheckEnabled);
    return SearchEngineConfiguration.of(b -> b.schemaManager(schemaManager));
  }
}
