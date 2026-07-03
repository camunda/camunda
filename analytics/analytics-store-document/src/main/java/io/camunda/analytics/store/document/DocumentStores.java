/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.analytics.dataset.store.MetadataStore;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.os.clients.OpensearchSearchClient;
import org.opensearch.client.opensearch.OpenSearchClient;

/**
 * Builds the document-backed stores for Elasticsearch or OpenSearch from a {@link
 * ConnectConfiguration}, choosing the backend by {@link ConnectConfiguration#getType()}. This is
 * the only place a concrete backend is named: it wires OC's connector + search client (read/write)
 * with our {@link DocumentSchemaClient} (schema); everything above is backend-neutral.
 */
public final class DocumentStores {

  private DocumentStores() {}

  /** The control-plane metadata store for the configured document backend. */
  public static MetadataStore metadataStore(final ConnectConfiguration configuration) {
    if (DatabaseConfig.OPENSEARCH.equals(configuration.getType())) {
      final OpenSearchClient client = new OpensearchConnector(configuration).createClient();
      final OpensearchSearchClient searchClient = new OpensearchSearchClient(client);
      return new DocumentMetadataStore(
          searchClient, searchClient, new OpensearchSchemaClient(client));
    }
    final ElasticsearchClient client = new ElasticsearchConnector(configuration).createClient();
    final ElasticsearchSearchClient searchClient = new ElasticsearchSearchClient(client);
    return new DocumentMetadataStore(
        searchClient, searchClient, new ElasticsearchSchemaClient(client));
  }
}
