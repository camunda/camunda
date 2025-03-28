/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.test.utils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
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

    final var config = new ConnectConfiguration();
    config.setUrl(elasticsearchContainer.getHttpHostAddress());
    final var esConnector = new ElasticsearchConnector(config);
    esObjectMapper = esConnector.objectMapper();
    elsClient = esConnector.createClient();

    final var osConfig = new ConnectConfiguration();
    osConfig.setType(DatabaseConfig.OPENSEARCH);
    osConfig.setUrl(opensearchContainer.getHttpHostAddress());
    final var osConnector = new OpensearchConnector(osConfig);
    osObjectMapper = osConnector.objectMapper();
    osClient = osConnector.createClient();
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
  public void afterAll(final ExtensionContext context) throws Exception {
    elsClient.close();
    osClient._transport().close();
  }
}
