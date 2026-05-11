/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.tenant;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.search.connect.SearchClientConnectException;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import java.util.LinkedHashMap;
import java.util.Map;
import org.agrona.CloseHelper;
import org.opensearch.client.ApiClient;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record SearchClients(
    Map<String, ElasticsearchClient> esClients,
    Map<String, OpenSearchClient> osClients,
    Map<String, OpenSearchAsyncClient> osAsyncClients)
    implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(SearchClients.class);

  public SearchClients {
    esClients = Map.copyOf(esClients);
    osClients = Map.copyOf(osClients);
    osAsyncClients = Map.copyOf(osAsyncClients);
  }

  public static SearchClients from(final Map<String, ConnectConfiguration> tenantConfigs) {
    final var esClients = new LinkedHashMap<String, ElasticsearchClient>();
    final var osClients = new LinkedHashMap<String, OpenSearchClient>();
    final var osAsyncClients = new LinkedHashMap<String, OpenSearchAsyncClient>();

    tenantConfigs.forEach(
        (tenantId, config) -> {
          final var dbType = config.getTypeEnum();

          LOG.debug("Creating search client for physical tenant [{}], (type={})", tenantId, dbType);

          if (dbType.isElasticSearch()) {
            final var connector = new ElasticsearchConnector(config);
            esClients.put(tenantId, connector.createClient());
          } else if (dbType.isOpenSearch()) {
            final var connector = new OpensearchConnector(config);
            osClients.put(tenantId, connector.createClient());
            osAsyncClients.put(tenantId, connector.createAsyncClient());
          } else {
            throw new SearchClientConnectException(
                "Physical tenant secondary storage currently only supports ElasticSearch or OpenSearch");
          }
        });

    return new SearchClients(esClients, osClients, osAsyncClients);
  }

  @Override
  public void close() throws Exception {
    CloseHelper.closeAll(esClients.values().stream().map(ElasticsearchClient::_transport).toList());
    CloseHelper.closeAll(osClients.values().stream().map(ApiClient::_transport).toList());
    CloseHelper.closeAll(osAsyncClients.values().stream().map(ApiClient::_transport).toList());
  }
}
