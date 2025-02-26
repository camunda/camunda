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
import static io.camunda.exporter.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static java.util.Arrays.asList;

import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

public class CamundaExporterITTemplateExtension
    implements TestTemplateInvocationContextProvider, BeforeAllCallback {

  protected SearchClientAdapter elsClientAdapter;
  protected SearchClientAdapter osClientAdapter;
  private final SearchDBExtension extension;

  public CamundaExporterITTemplateExtension(final SearchDBExtension extension) {
    this.extension = extension;
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
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      return Stream.of(
          invocationContext(getConfigWithConnectionDetails(OPENSEARCH), osClientAdapter),
          invocationContext(getConfigWithConnectionDetails(ELASTICSEARCH), elsClientAdapter));
    } else {
      return Stream.of(
          invocationContext(getConfigWithConnectionDetails(OPENSEARCH), osClientAdapter));
    }
  }

  protected ParameterResolver exporterConfigResolver(final ExporterConfiguration config) {
    return new ParameterResolver() {

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
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter) {
    return new TestTemplateInvocationContext() {

      @Override
      public String getDisplayName(final int invocationIndex) {
        return config.getConnect().getType();
      }

      @Override
      public List<Extension> getAdditionalExtensions() {
        return asList(exporterConfigResolver(config), clientAdapterResolver(clientAdapter));
      }
    };
  }

  protected ExporterConfiguration getConfigWithConnectionDetails(
      final ConnectionTypes connectionType) {
    final var config = new ExporterConfiguration();
    config.getIndex().setPrefix(SearchDBExtension.CUSTOM_PREFIX);
    config.getBulk().setSize(1); // force flushing on the first record
    if (connectionType == ELASTICSEARCH) {
      config.getConnect().setUrl(extension.esUrl());
    } else if (connectionType == OPENSEARCH) {
      config.getConnect().setUrl(extension.osUrl());
    }
    config.getConnect().setClusterName(connectionType.name());
    config.getConnect().setType(connectionType.getType());
    return config;
  }

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    if (Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).isEmpty()) {
      elsClientAdapter = new SearchClientAdapter(extension.esClient(), extension.objectMapper());
    }
    osClientAdapter = new SearchClientAdapter(extension.osClient(), extension.objectMapper());
  }
}
