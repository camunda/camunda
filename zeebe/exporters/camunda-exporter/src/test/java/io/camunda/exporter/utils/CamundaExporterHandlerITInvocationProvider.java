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

import io.camunda.exporter.CamundaExporter;
import io.camunda.exporter.DefaultExporterResourceProvider;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.handlers.operation.OperationFromIncidentHandler;
import io.camunda.exporter.handlers.operation.OperationFromProcessInstanceHandler;
import io.camunda.exporter.handlers.operation.OperationFromVariableDocumentHandler;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

public class CamundaExporterHandlerITInvocationProvider
    extends CamundaExporterITInvocationProvider {
  @Override
  public void afterEach(final ExtensionContext context) {
    //    we don't want to clear indices and templates after each test as each invocation context
    //    now providers the same exporter, limiting the amount of schema startups
  }

  @Override
  public boolean supportsTestTemplate(final ExtensionContext extensionContext) {
    // we only want to template tests in the class which ask for exporter, client
    // adapter and handler.
    final var testParams = extensionContext.getTestMethod().orElseThrow().getParameters();
    if (testParams.length != 3) {
      return false;
    }

    return (testParams[0].getType().equals(CamundaExporter.class)
            && testParams[1].getType().equals(SearchClientAdapter.class))
        && testParams[2].getType().equals(ExportHandler.class);
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      final ExtensionContext extensionContext) {

    final var osConfig = getConfigWithConnectionDetails(OPENSEARCH);
    final var osExporter = new CamundaExporter();
    osExporter.configure(
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>(OPENSEARCH.toString(), osConfig)));
    osExporter.open(new ExporterTestController());

    final var elsConfig = getConfigWithConnectionDetails(ELASTICSEARCH);
    final var elsExporter = new CamundaExporter();
    elsExporter.configure(
        new ExporterTestContext()
            .setConfiguration(
                new ExporterTestConfiguration<>(ELASTICSEARCH.toString(), elsConfig)));
    elsExporter.open(new ExporterTestController());

    // doesn't matter which config we use as handlers are unaware of who they export to
    final var provider = new DefaultExporterResourceProvider();
    provider.init(osConfig, ClientAdapter.of(elsConfig)::getProcessCacheLoader);

    return provider.getExportHandlers().stream()
        .filter(
            handler ->
                !List.of(
                        OperationFromIncidentHandler.class,
                        OperationFromProcessInstanceHandler.class,
                        OperationFromVariableDocumentHandler.class)
                    .contains(handler.getClass()))
        .map(
            handler ->
                List.of(
                    invocationContext(osExporter, osClientAdapter, handler, OPENSEARCH),
                    invocationContext(elsExporter, elsClientAdapter, handler, ELASTICSEARCH)))
        .flatMap(List::stream);
  }

  private ParameterResolver exportHandlerResolver(final ExportHandler<?, ?> handler) {
    return new ParameterResolver() {

      @Override
      public boolean supportsParameter(
          final ParameterContext parameterCtx, final ExtensionContext extensionCtx) {
        return parameterCtx.getParameter().getType().equals(ExportHandler.class);
      }

      @Override
      public Object resolveParameter(
          final ParameterContext parameterCtx, final ExtensionContext extensionCtx) {
        return handler;
      }
    };
  }

  private ParameterResolver exporterResolver(final CamundaExporter exporter) {
    return new ParameterResolver() {

      @Override
      public boolean supportsParameter(
          final ParameterContext parameterCtx, final ExtensionContext extensionCtx) {
        return parameterCtx.getParameter().getType().equals(CamundaExporter.class);
      }

      @Override
      public Object resolveParameter(
          final ParameterContext parameterCtx, final ExtensionContext extensionCtx) {
        return exporter;
      }
    };
  }

  private TestTemplateInvocationContext invocationContext(
      final CamundaExporter exporter,
      final SearchClientAdapter clientAdapter,
      final ExportHandler<?, ?> exportHandler,
      final ConnectionTypes connectionType) {
    return new TestTemplateInvocationContext() {

      @Override
      public String getDisplayName(final int invocationIndex) {
        return String.format(
            "shouldExportWith[%s](%s)", exportHandler.getClass().getSimpleName(), connectionType);
      }

      @Override
      public List<Extension> getAdditionalExtensions() {
        return asList(
            exporterResolver(exporter),
            clientAdapterResolver(clientAdapter),
            exportHandlerResolver(exportHandler));
      }
    };
  }
}
