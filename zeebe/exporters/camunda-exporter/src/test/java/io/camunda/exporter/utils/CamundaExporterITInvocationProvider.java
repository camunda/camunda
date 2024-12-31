/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import static io.camunda.exporter.utils.AWSOpenSearchDatabaseCallbackDelegate.IT_OPENSEARCH_AWS_INSTANCE_URL_PROPERTY;
import static java.util.Arrays.asList;

import io.camunda.exporter.config.ExporterConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

public class CamundaExporterITInvocationProvider
    implements TestTemplateInvocationContextProvider,
        AfterAllCallback,
        BeforeAllCallback,
        AfterEachCallback {

  public static final String CONFIG_PREFIX = "custom-prefix";
  public static final String RANDOM_STRING = UUID.randomUUID().toString();
  private final SearchDatabaseCallbackDelegate callbackDelegate;

  public CamundaExporterITInvocationProvider() {
    final var shouldRunAgainstAWSOS =
        Optional.ofNullable(System.getProperty(IT_OPENSEARCH_AWS_INSTANCE_URL_PROPERTY))
            .isPresent();
    if (shouldRunAgainstAWSOS) {
      callbackDelegate = new AWSOpenSearchDatabaseCallbackDelegate();
    } else {
      callbackDelegate = new ContainerizedSearchDatabaseCallbackDelegate();
    }
  }

  @Override
  public void afterEach(final ExtensionContext context) throws Exception {
    callbackDelegate.afterEach(context);
  }

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    callbackDelegate.beforeAll(context);
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
    return callbackDelegate.contextAdapterRegistration().entrySet().stream()
        .map(
            entry ->
                invocationContext(
                    callbackDelegate.getConfigWithConnectionDetails(entry.getKey()),
                    entry.getValue()));
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

  @Override
  public void afterAll(final ExtensionContext context) throws Exception {
    callbackDelegate.afterAll(context);
  }
}
