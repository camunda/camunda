/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.jetty.OptimizeResourceConstants.ACTUATOR_PORT_PROPERTY_KEY;
import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.CONTEXT_PATH;
import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.HTTPS_PORT_KEY;
import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.HTTP_PORT_KEY;
import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.INTEGRATION_TESTS;

import io.camunda.optimize.jetty.OptimizeResourceConstants;
import io.camunda.optimize.test.it.extension.DatabaseIntegrationTestExtension;
import io.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import io.camunda.optimize.test.optimize.AlertClient;
import io.camunda.optimize.test.optimize.AnalysisClient;
import io.camunda.optimize.test.optimize.AssigneesClient;
import io.camunda.optimize.test.optimize.CollectionClient;
import io.camunda.optimize.test.optimize.DashboardClient;
import io.camunda.optimize.test.optimize.DefinitionClient;
import io.camunda.optimize.test.optimize.EntitiesClient;
import io.camunda.optimize.test.optimize.EventProcessClient;
import io.camunda.optimize.test.optimize.ExportClient;
import io.camunda.optimize.test.optimize.FlowNodeNamesClient;
import io.camunda.optimize.test.optimize.IdentityClient;
import io.camunda.optimize.test.optimize.ImportClient;
import io.camunda.optimize.test.optimize.IngestionClient;
import io.camunda.optimize.test.optimize.LocalizationClient;
import io.camunda.optimize.test.optimize.ProcessOverviewClient;
import io.camunda.optimize.test.optimize.PublicApiClient;
import io.camunda.optimize.test.optimize.ReportClient;
import io.camunda.optimize.test.optimize.SharingClient;
import io.camunda.optimize.test.optimize.UiConfigurationClient;
import io.camunda.optimize.test.optimize.VariablesClient;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {INTEGRATION_TESTS + "=true"})
@Configuration
public abstract class AbstractIT {

  // All tests marked with this tag are passing with OpenSearch
  public static final String OPENSEARCH_PASSING = "openSearchPassing";
  // Tests marked with this tag are known to not be working with OpenSearch and are not expected to
  // be working yet
  public static final String OPENSEARCH_SINGLE_TEST_FAIL_OK = "openSearchSingleTestFailOK";
  // Tests marked with this tag are tests that should be working with OpenSearch, but are failing
  // due to a bug. They
  // are ignored by the 'OpenSearch passing' CI pipeline, but need to be addressed soon
  public static final String OPENSEARCH_SHOULD_BE_PASSING = "openSearchShouldBePassing";

  @RegisterExtension
  @Order(1)
  public static DatabaseIntegrationTestExtension databaseIntegrationTestExtension =
      new DatabaseIntegrationTestExtension();

  @RegisterExtension
  @Order(3)
  public static EmbeddedOptimizeExtension embeddedOptimizeExtension =
      new EmbeddedOptimizeExtension();

  private final Supplier<OptimizeRequestExecutor> optimizeRequestExecutorSupplier =
      () -> embeddedOptimizeExtension.getRequestExecutor();
  // optimize test helpers
  protected CollectionClient collectionClient =
      new CollectionClient(optimizeRequestExecutorSupplier);
  protected ReportClient reportClient = new ReportClient(optimizeRequestExecutorSupplier);
  protected AlertClient alertClient = new AlertClient(optimizeRequestExecutorSupplier);
  protected DashboardClient dashboardClient = new DashboardClient(optimizeRequestExecutorSupplier);
  protected EventProcessClient eventProcessClient =
      new EventProcessClient(optimizeRequestExecutorSupplier);
  protected SharingClient sharingClient = new SharingClient(optimizeRequestExecutorSupplier);
  protected AnalysisClient analysisClient = new AnalysisClient(optimizeRequestExecutorSupplier);
  protected UiConfigurationClient uiConfigurationClient =
      new UiConfigurationClient(optimizeRequestExecutorSupplier);
  protected EntitiesClient entitiesClient = new EntitiesClient(optimizeRequestExecutorSupplier);
  protected ExportClient exportClient = new ExportClient(optimizeRequestExecutorSupplier);
  protected ImportClient importClient = new ImportClient(optimizeRequestExecutorSupplier);
  protected PublicApiClient publicApiClient = new PublicApiClient(optimizeRequestExecutorSupplier);
  protected DefinitionClient definitionClient =
      new DefinitionClient(optimizeRequestExecutorSupplier);
  protected VariablesClient variablesClient = new VariablesClient(optimizeRequestExecutorSupplier);
  protected AssigneesClient assigneesClient = new AssigneesClient(optimizeRequestExecutorSupplier);
  protected FlowNodeNamesClient flowNodeNamesClient =
      new FlowNodeNamesClient(optimizeRequestExecutorSupplier);
  protected LocalizationClient localizationClient =
      new LocalizationClient(optimizeRequestExecutorSupplier);
  protected IdentityClient identityClient = new IdentityClient(optimizeRequestExecutorSupplier);
  protected IngestionClient ingestionClient =
      new IngestionClient(
          optimizeRequestExecutorSupplier,
          () ->
              embeddedOptimizeExtension
                  .getConfigurationService()
                  .getOptimizeApiConfiguration()
                  .getAccessToken());
  protected ProcessOverviewClient processOverviewClient =
      new ProcessOverviewClient(optimizeRequestExecutorSupplier);

  protected abstract void startAndUseNewOptimizeInstance();

  protected void startAndUseNewOptimizeInstance(Map<String, String> argMap, String activeProfile) {
    String[] arguments = prepareArgs(argMap);

    // run after-test cleanups with the old context
    embeddedOptimizeExtension.afterTest();
    // in case it's not the first *additional* instance, we terminate the first one
    if (embeddedOptimizeExtension.isCloseContextAfterTest()) {
      ((ConfigurableApplicationContext) embeddedOptimizeExtension.getApplicationContext()).close();
    }

    final ConfigurableApplicationContext context =
        new SpringApplicationBuilder(Main.class).profiles(activeProfile).build().run(arguments);

    embeddedOptimizeExtension.setApplicationContext(context);
    embeddedOptimizeExtension.setCloseContextAfterTest(true);
    embeddedOptimizeExtension.setResetImportOnStart(false);
    embeddedOptimizeExtension.setupOptimize();
  }

  private String[] prepareArgs(final Map<String, String> argMap) {
    final String httpsPort = getPortArg(HTTPS_PORT_KEY);
    final String httpPort = getPortArg(HTTP_PORT_KEY);
    final String actuatorPort =
        getArg(
            ACTUATOR_PORT_PROPERTY_KEY,
            String.valueOf(OptimizeResourceConstants.ACTUATOR_PORT + 100));
    final String contextPath =
        embeddedOptimizeExtension
            .getConfigurationService()
            .getContextPath()
            .map(contextPathFromConfig -> getArg(CONTEXT_PATH, contextPathFromConfig))
            .orElse("");

    final List<String> argList =
        argMap.entrySet().stream()
            .map(e -> getArg(e.getKey(), e.getValue()))
            .collect(Collectors.toList());

    Collections.addAll(argList, httpsPort, httpPort, actuatorPort, contextPath);

    return argList.toArray(String[]::new);
  }

  private String getPortArg(String portKey) {
    return getArg(
        portKey,
        String.valueOf(
            embeddedOptimizeExtension.getBean(JettyConfig.class).getPort(portKey) + 100));
  }

  private String getArg(String key, String value) {
    return String.format("--%s=%s", key, value);
  }
}
