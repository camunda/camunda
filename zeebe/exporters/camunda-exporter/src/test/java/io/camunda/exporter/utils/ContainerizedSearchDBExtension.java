/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.exporter.schema.opensearch.OpensearchEngineClient;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class ContainerizedSearchDBExtension extends SearchDBExtension {

  private static ElasticsearchContainer elasticsearchContainer;
  private static OpensearchContainer opensearchContainer;

  private static ElasticsearchClient elsClient;
  private static OpenSearchClient osClient;

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    elasticsearchContainer = TestSearchContainers.createDefeaultElasticsearchContainer();
    opensearchContainer = TestSearchContainers.createDefaultOpensearchContainer();

    elasticsearchContainer.start();
    opensearchContainer.start();

    final var config = new ExporterConfiguration();
    config.getConnect().setUrl(elasticsearchContainer.getHttpHostAddress());
    elsClient = new ElasticsearchConnector(config.getConnect()).createClient();

    final var osConfig = new ExporterConfiguration();
    osConfig.getConnect().setType("opensearch");
    osConfig.getConnect().setUrl(opensearchContainer.getHttpHostAddress());
    osClient = new OpensearchConnector(osConfig.getConnect()).createClient();
  }

  @Override
  public void beforeEach(final ExtensionContext context) throws Exception {
    new ElasticsearchEngineClient(elsClient).createIndex(PROCESS_INDEX, new IndexSettings());
    new OpensearchEngineClient(osClient).createIndex(PROCESS_INDEX, new IndexSettings());
  }

  @Override
  public void afterEach(final ExtensionContext context) throws Exception {
    elsClient.indices().delete(req -> req.index(PROCESS_INDEX.getFullQualifiedName()));
    osClient.indices().delete(req -> req.index(PROCESS_INDEX.getFullQualifiedName()));
  }

  @Override
  public ElasticsearchClient esClient() {
    return elsClient;
  }

  @Override
  public OpenSearchClient osClient() {
    return osClient;
  }
}
