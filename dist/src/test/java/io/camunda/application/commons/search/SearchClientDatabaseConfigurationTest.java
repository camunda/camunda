/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.clients.impl.NoopSearchClientsProxy;
import io.camunda.search.connect.configuration.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class SearchClientDatabaseConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner();

  @Test
  public void shouldCreateNoopSearchClientsProxyWhenDatabaseTypeIsNone() {
    runner
        .withUserConfiguration(SearchClientDatabaseConfiguration.class)
        .withPropertyValues(
            "camunda.database.type=" + DatabaseConfig.NONE, "zeebe.gateway.rest.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(SearchClientsProxy.class);
              final var proxy = context.getBean(SearchClientsProxy.class);
              assertThat(proxy).isInstanceOf(NoopSearchClientsProxy.class);
            });
  }

  @Test
  public void shouldNotCreateNoopSearchClientsProxyWhenDatabaseTypeIsElasticsearch() {
    runner
        .withUserConfiguration(SearchClientDatabaseConfiguration.class)
        .withPropertyValues(
            "camunda.database.type=" + DatabaseConfig.ELASTICSEARCH,
            "zeebe.gateway.rest.enabled=true")
        .run(
            context -> {
              // Should not have SearchClientsProxy because DocumentBasedSearchClient bean is
              // missing
              assertThat(context).doesNotHaveBean(SearchClientsProxy.class);
            });
  }

  @Test
  public void shouldNotCreateAnySearchClientsProxyWhenRestGatewayIsDisabled() {
    runner
        .withUserConfiguration(SearchClientDatabaseConfiguration.class)
        .withPropertyValues(
            "camunda.database.type=" + DatabaseConfig.NONE, "zeebe.gateway.rest.enabled=false")
        .run(
            context -> {
              // No SearchClientsProxy should be created because RestGateway is disabled
              assertThat(context).doesNotHaveBean(SearchClientsProxy.class);
            });
  }
}
