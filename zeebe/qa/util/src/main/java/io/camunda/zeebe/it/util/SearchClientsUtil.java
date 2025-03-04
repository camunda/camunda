/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.util;

import io.camunda.db.se.config.ConnectConfiguration;
import io.camunda.search.clients.SearchClients;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
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
}
