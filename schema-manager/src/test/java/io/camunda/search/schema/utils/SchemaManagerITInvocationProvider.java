/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.utils;

import static java.util.Arrays.asList;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class SchemaManagerITInvocationProvider
    implements TestTemplateInvocationContextProvider,
        AfterAllCallback,
        BeforeAllCallback,
        AfterEachCallback {

  public static final String CONFIG_PREFIX = "custom-prefix";
  public static final String OPENSEARCH_NETWORK_ALIAS = "test-opensearch";
  public static final String ELASTICSEARCH_NETWORK_ALIAS = "test-elasticsearch";
  protected SearchClientAdapter elsClientAdapter;
  protected SearchClientAdapter osClientAdapter;
  private final ElasticsearchContainer elsContainer =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withNetwork(Network.SHARED)
          .withNetworkAliases(ELASTICSEARCH_NETWORK_ALIAS);
  private final OpensearchContainer<?> osContainer =
      TestSearchContainers.createDefaultOpensearchContainer()
          .withNetwork(Network.SHARED)
          .withNetworkAliases(OPENSEARCH_NETWORK_ALIAS);
  private ElasticsearchClient elsClient;
  private OpenSearchClient osClient;
  private final List<AutoCloseable> closeables = new ArrayList<>();

  protected SearchEngineConfiguration getConfigWithConnectionDetails(
      final DatabaseType connectionType) {
    final var config = SearchEngineConfiguration.of(b -> b);
    config.connect().setIndexPrefix(CONFIG_PREFIX);
    if (connectionType == DatabaseType.ELASTICSEARCH) {
      config.connect().setUrl(elsContainer.getHttpHostAddress());

    } else if (connectionType == DatabaseType.OPENSEARCH) {
      config.connect().setUrl(osContainer.getHttpHostAddress());
    }
    config.connect().setClusterName(connectionType.name());
    config.connect().setType(connectionType.toString());
    return config;
  }

  @Override
  public void afterEach(final ExtensionContext context) throws IOException {
    if (context.getDisplayName().equals(DatabaseType.ELASTICSEARCH.toString())) {
      elsClient.indices().delete(req -> req.index("*"));
      elsClient.indices().deleteIndexTemplate(req -> req.name("*"));
    } else if (context.getDisplayName().equals(DatabaseType.OPENSEARCH.toString())) {
      osClient.indices().delete(req -> req.index("*"));
      osClient.indices().deleteIndexTemplate(req -> req.name("*"));
    }
  }

  @Override
  public void beforeAll(final ExtensionContext context) {
    elsContainer.start();
    osContainer.start();

    final var osConfig = getConfigWithConnectionDetails(DatabaseType.OPENSEARCH);
    final var osConnector = new OpensearchConnector(osConfig.connect());
    osClient = osConnector.createClient();
    osClientAdapter = new SearchClientAdapter(osClient, osConnector.objectMapper());

    final var elsConfig = getConfigWithConnectionDetails(DatabaseType.ELASTICSEARCH);
    final var esConnector = new ElasticsearchConnector(elsConfig.connect());
    elsClient = esConnector.createClient();
    elsClientAdapter = new SearchClientAdapter(elsClient, esConnector.objectMapper());

    closeables.add(elsContainer);
    closeables.add(osContainer);
  }

  @Override
  public boolean supportsTestTemplate(final ExtensionContext extensionContext) {
    // we only want to template tests in the class which ask for configuration and the client
    // adapter.
    final var testParams = extensionContext.getTestMethod().orElseThrow().getParameters();
    if (testParams.length != 2) {
      return false;
    }

    return (testParams[0].getType().equals(SearchEngineConfiguration.class)
        && testParams[1].getType().equals(SearchClientAdapter.class));
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      final ExtensionContext extensionContext) {

    return Stream.of(
        invocationContext(getConfigWithConnectionDetails(DatabaseType.OPENSEARCH), osClientAdapter),
        invocationContext(
            getConfigWithConnectionDetails(DatabaseType.ELASTICSEARCH), elsClientAdapter));
  }

  protected ParameterResolver searchEngineConfigResolver(final SearchEngineConfiguration config) {
    return new ParameterResolver() {

      @Override
      public boolean supportsParameter(
          final ParameterContext parameterCtx, final ExtensionContext extensionCtx) {
        return parameterCtx.getParameter().getType().equals(SearchEngineConfiguration.class);
      }

      @Override
      public Object resolveParameter(
          final ParameterContext parameterCtx, final ExtensionContext extensionCtx) {
        return config;
      }
    };
  }

  protected ParameterResolver clientAdapterResolver(final SearchClientAdapter clientAdapter) {
    return new ParameterResolver() {

      @Override
      public boolean supportsParameter(
          final ParameterContext parameterContext, final ExtensionContext extensionContext)
          throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(SearchClientAdapter.class);
      }

      @Override
      public Object resolveParameter(
          final ParameterContext parameterContext, final ExtensionContext extensionContext)
          throws ParameterResolutionException {
        return clientAdapter;
      }
    };
  }

  private TestTemplateInvocationContext invocationContext(
      final SearchEngineConfiguration config, final SearchClientAdapter clientAdapter) {
    return new TestTemplateInvocationContext() {

      @Override
      public String getDisplayName(final int invocationIndex) {
        return config.connect().getType();
      }

      @Override
      public List<Extension> getAdditionalExtensions() {
        return asList(searchEngineConfigResolver(config), clientAdapterResolver(clientAdapter));
      }
    };
  }

  @Override
  public void afterAll(final ExtensionContext context) {
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
}
