/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.db.se.config.IndexSettings;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.exporter.schema.opensearch.OpensearchEngineClient;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class ContainerizedSearchDBExtension extends SearchDBExtension {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ContainerizedSearchDBExtension.class);

  private static ElasticsearchContainer elasticsearchContainer;
  private static OpensearchContainer opensearchContainer;

  private static ElasticsearchClient elsClient;
  private static OpenSearchClient osClient;
  private ObjectMapper osObjectMapper;
  private ObjectMapper esObjectMapper;

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    elasticsearchContainer = TestSearchContainers.createDefeaultElasticsearchContainer();
    opensearchContainer = TestSearchContainers.createDefaultOpensearchContainer();

    elasticsearchContainer.start();
    opensearchContainer.start();

    final var config = new ExporterConfiguration();
    config.getConnect().setUrl(elasticsearchContainer.getHttpHostAddress());
    final var esConnector = new ElasticsearchConnector(config.getConnect());
    esObjectMapper = esConnector.objectMapper();
    elsClient = esConnector.createClient();

    final var osConfig = new ExporterConfiguration();
    osConfig.getConnect().setType("opensearch");
    osConfig.getConnect().setUrl(opensearchContainer.getHttpHostAddress());
    final var osConnector = new OpensearchConnector(osConfig.getConnect());
    osObjectMapper = osConnector.objectMapper();
    osClient = osConnector.createClient();
  }

  @Override
  public void beforeEach(final ExtensionContext context) throws Exception {
    maybeCreateIndexEs(PROCESS_INDEX);
    maybeCreateIndexEs(FORM_INDEX);
    maybeCreateIndexOs(PROCESS_INDEX);
    maybeCreateIndexOs(FORM_INDEX);
  }

  private void maybeCreateIndexEs(final IndexDescriptor descriptor) {
    try {
      new ElasticsearchEngineClient(elsClient, esObjectMapper)
          .createIndex(descriptor, new IndexSettings());
    } catch (final Exception e) {
      LOGGER.warn("Failed to create index {}", descriptor.getIndexName(), e);
    }
  }

  private void maybeCreateIndexOs(final IndexDescriptor descriptor) {
    try {
      new OpensearchEngineClient(osClient, osObjectMapper)
          .createIndex(descriptor, new IndexSettings());
    } catch (final Exception e) {
      LOGGER.warn("Failed to create index {}", descriptor.getIndexName(), e);
    }
  }

  @Override
  public void afterEach(final ExtensionContext context) throws Exception {
    maybeDeleteIndexEs(PROCESS_INDEX.getFullQualifiedName());
    maybeDeleteIndexEs(FORM_INDEX.getFullQualifiedName());
    maybeDeleteIndexOs(PROCESS_INDEX.getFullQualifiedName());
    maybeDeleteIndexOs(FORM_INDEX.getFullQualifiedName());
  }

  private void maybeDeleteIndexEs(final String indexName) {
    try {
      elsClient.indices().delete(req -> req.index(indexName));
    } catch (final Exception e) {
      LOGGER.warn("Failed to delete index {}", indexName, e);
    }
  }

  private void maybeDeleteIndexOs(final String indexName) {
    try {
      osClient.indices().delete(req -> req.index(indexName));
    } catch (final Exception e) {
      LOGGER.warn("Failed to delete index {}", indexName, e);
    }
  }

  @Override
  public ObjectMapper objectMapper() {
    // which one to return?
    return osObjectMapper;
  }

  @Override
  public ElasticsearchClient esClient() {
    return elsClient;
  }

  @Override
  public OpenSearchClient osClient() {
    return osClient;
  }

  @Override
  public String esUrl() {
    return elasticsearchContainer.getHttpHostAddress();
  }

  @Override
  public String osUrl() {
    return opensearchContainer.getHttpHostAddress();
  }

  @Override
  public void afterAll(final ExtensionContext context) throws Exception {}
}
