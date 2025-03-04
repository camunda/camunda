/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.util;

import io.camunda.search.clients.SearchClients;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.os.clients.OpensearchSearchClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;

public class SearchClientsUtil {

  public static ElasticsearchSearchClient createLowLevelSearchClient(
      final String elasticsearchUrl) {
    final var config = new ConnectConfiguration();
    config.setUrl(elasticsearchUrl);
    final var elasticsearchClient = new ElasticsearchConnector(config).createClient();
    return new ElasticsearchSearchClient(elasticsearchClient);
  }

  public static SearchClients createSearchClients(final String elasticsearchUrl) {
    final var lowLevelSearchClient = createLowLevelSearchClient(elasticsearchUrl);
    return new SearchClients(lowLevelSearchClient, new IndexDescriptors("", true));
  }

  public static OpensearchSearchClient createLowLevelOpensearchSearchClient(
      final ConnectConfiguration config) {
    final var opensearchClient = new OpensearchConnector(config).createClient();
    return new OpensearchSearchClient(opensearchClient);
  }

  public static ElasticsearchSearchClient createLowLevelElasticsearchSearchClient(
      final ConnectConfiguration config) {
    final var elasticsearchClient = new ElasticsearchConnector(config).createClient();
    return new ElasticsearchSearchClient(elasticsearchClient);
  }
}
