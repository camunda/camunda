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
import static java.util.Arrays.asList;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.AfterAllCallback;
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
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class CamundaExporterITInvocationProvider
    implements TestTemplateInvocationContextProvider, AfterAllCallback, BeforeAllCallback {

  public static final String CONFIG_PREFIX = "camunda-record";
  private final ElasticsearchContainer elsContainer =
      TestSupport.createDefeaultElasticsearchContainer();
  private final OpensearchContainer<?> osContainer = TestSupport.createDefaultOpensearchContainer();
  private ElasticsearchClient elsClient;
  private OpenSearchClient osClient;
  private SearchClientAdapter elsClientAdapter;
  private SearchClientAdapter osClientAdapter;

  private final List<AutoCloseable> closeables = new ArrayList<>();

  private ExporterConfiguration getConfigWithConnectionDetails(
      final ConnectionTypes connectionType) {
    final var config = new ExporterConfiguration();
    config.getIndex().setPrefix(CONFIG_PREFIX);
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
  public void beforeAll(final ExtensionContext context) {
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
  public boolean supportsTestTemplate(final ExtensionContext extensionContext) {
    // we only want to template tests in the class which ask for configuration and the client
    // adapter.
    final var testParams = extensionContext.getTestMethod().orElseThrow().getParameters();
    if (testParams.length != 2) {
      return false;
    }

    return (testParams[0].getType().equals(ExporterConfiguration.class)
        && testParams[1].getType().equals(SearchClientAdapter.class));
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      final ExtensionContext extensionContext) {

    try {
      elsClient.indices().delete(req -> req.index("*"));
      elsClient.indices().deleteIndexTemplate(req -> req.name("*"));
      osClient.indices().delete(req -> req.index("*"));
      osClient.indices().deleteIndexTemplate(req -> req.name("*"));
    } catch (final Exception e) {
      throw new RuntimeException("Failed to reset container data", e);
    }
    return Stream.of(
        invocationContext(getConfigWithConnectionDetails(OPENSEARCH), osClientAdapter),
        invocationContext(getConfigWithConnectionDetails(ELASTICSEARCH), elsClientAdapter));
  }

  private TestTemplateInvocationContext invocationContext(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter) {
    return new TestTemplateInvocationContext() {

      @Override
      public String getDisplayName(final int invocationIndex) {
        return config.getConnect().getType();
      }

      @Override
      public List<Extension> getAdditionalExtensions() {
        return asList(
            new ParameterResolver() {

              @Override
              public boolean supportsParameter(
                  final ParameterContext parameterCtx, final ExtensionContext extensionCtx) {
                return parameterCtx.getParameter().getType().equals(ExporterConfiguration.class);
              }

              @Override
              public Object resolveParameter(
                  final ParameterContext parameterCtx, final ExtensionContext extensionCtx) {
                return config;
              }
            },
            new ParameterResolver() {

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
            });
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
