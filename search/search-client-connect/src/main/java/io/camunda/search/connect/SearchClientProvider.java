/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchClientConnector;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.os.clients.OpensearchSearchClient;
import java.util.function.Function;

public interface SearchClientProvider extends Function<ConnectConfiguration, CamundaSearchClient> {

  public static final class SearchClientProviders {

    private SearchClientProviders() {}

    public static CamundaSearchClient createElasticsearchProvider(
        final ConnectConfiguration configuration) {
      final var connector = new ElasticsearchConnector(configuration);
      final var elasticsearch = connector.createClient();
      return new ElasticsearchSearchClient(elasticsearch);
    }

    public static CamundaSearchClient createOpensearchProvider(
        final ConnectConfiguration configuration) {
      final var connector = new OpensearchClientConnector(configuration);
      final var opensearch = connector.createClient();
      return new OpensearchSearchClient(opensearch);
    }
  }
}
