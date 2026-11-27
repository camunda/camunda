/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.es9;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Creates the Elasticsearch client of the Job. The Job runs on Elasticsearch 8.19 while the rest of
 * the cluster is still on 8.10, so the client asks for the version 8 REST API.
 *
 * <p>The connection settings are the ones of the Orchestration Cluster user, read from the same
 * {@code camunda.data.secondary-storage.elasticsearch.*} properties and converted by the same code
 * as in the broker. Binding them directly avoids starting a Spring context.
 */
@NullMarked
final class ElasticsearchClientFactory {

  private ElasticsearchClientFactory() {}

  static ElasticsearchClient create(final Environment environment) {
    return ElasticsearchConnector.forElasticsearch8(connectConfiguration(environment))
        .createClient();
  }

  static ConnectConfiguration connectConfiguration(final Environment environment) {
    // No UnifiedConfigurationHelper is created, so the unified properties ignore legacy ones.
    final var camunda =
        Binder.get(environment).bind(Camunda.PREFIX, Camunda.class).orElseGet(Camunda::new);

    final var type = camunda.getData().getSecondaryStorage().getType();
    if (type != SecondaryStorageType.elasticsearch) {
      throw new IllegalStateException(
          "The ES9 index migrator needs camunda.data.secondary-storage.type=elasticsearch, but it is "
              + type);
    }
    return new SearchEngineConnectPropertiesOverride.Converter(camunda).convert();
  }
}
