/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import static io.camunda.exporter.config.ConnectionTypes.ELASTICSEARCH;
import static io.camunda.exporter.config.ConnectionTypes.OPENSEARCH;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class ContainerizedSearchDatabaseCallbackDelegate implements SearchDatabaseCallbackDelegate {

  protected SearchClientAdapter elsClientAdapter;
  protected SearchClientAdapter osClientAdapter;

  private ElasticsearchContainer elsContainer;
  private OpensearchContainer<?> osContainer;

  private ElasticsearchClient elsClient;
  private OpenSearchClient osClient;

  private final List<AutoCloseable> closeables = new ArrayList<>();

  @Override
  public void afterAll(final ExtensionContext context) throws IOException {
    closeables.parallelStream()
        .forEach(
            c -> {
              try {
                c.close();
              } catch (final Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Override
  public void afterEach(final ExtensionContext context) throws IOException {
    if (context.getDisplayName().equals(ConnectionTypes.ELASTICSEARCH.getType())) {
      elsClient.indices().delete(req -> req.index("*"));
      elsClient.indices().deleteIndexTemplate(req -> req.name("*"));
    } else if (context.getDisplayName().equals(ConnectionTypes.OPENSEARCH.getType())) {
      osClient.indices().delete(req -> req.index("*"));
      osClient.indices().deleteIndexTemplate(req -> req.name("*"));
    }
  }

  @Override
  public void beforeAll(final ExtensionContext context) throws IOException {
    elsContainer = TestSearchContainers.createDefeaultElasticsearchContainer();
    osContainer = TestSearchContainers.createDefaultOpensearchContainer();

    elsContainer.start();
    osContainer.start();

    final var osConfig = getConfigWithConnectionDetails(OPENSEARCH);
    osClient = new OpensearchConnector(osConfig.getConnect()).createClient();
    osClientAdapter = new SearchClientAdapter(osClient);

    final var elsConfig = getConfigWithConnectionDetails(ELASTICSEARCH);
    elsClient = new ElasticsearchConnector(elsConfig.getConnect()).createClient();
    elsClientAdapter = new SearchClientAdapter(elsClient);

    closeables.add(elsContainer);
    closeables.add(osContainer);
  }

  @Override
  public ExporterConfiguration getConfigWithConnectionDetails(
      final ConnectionTypes connectionType) {
    final var config = new ExporterConfiguration();
    config.getIndex().setPrefix(CamundaExporterITInvocationProvider.CONFIG_PREFIX);
    config.getBulk().setSize(1); // force flushing on the first record
    if (connectionType == ELASTICSEARCH) {
      config.getConnect().setUrl(elsContainer.getHttpHostAddress());
    } else if (connectionType == OPENSEARCH) {
      config.getConnect().setUrl(osContainer.getHttpHostAddress());
    }
    config.getConnect().setType(connectionType.getType());
    return config;
  }

  @Override
  public Map<ConnectionTypes, SearchClientAdapter> contextAdapterRegistration() {
    return Map.of(ELASTICSEARCH, elsClientAdapter, OPENSEARCH, osClientAdapter);
  }
}
