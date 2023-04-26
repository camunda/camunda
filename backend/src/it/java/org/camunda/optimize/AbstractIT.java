/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import org.camunda.optimize.jetty.OptimizeResourceConstants;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.engine.IncidentClient;
import org.camunda.optimize.test.engine.OutlierDistributionClient;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.optimize.AlertClient;
import org.camunda.optimize.test.optimize.AnalysisClient;
import org.camunda.optimize.test.optimize.AssigneesClient;
import org.camunda.optimize.test.optimize.CandidateGroupClient;
import org.camunda.optimize.test.optimize.CollectionClient;
import org.camunda.optimize.test.optimize.DashboardClient;
import org.camunda.optimize.test.optimize.DefinitionClient;
import org.camunda.optimize.test.optimize.EntitiesClient;
import org.camunda.optimize.test.optimize.EventProcessClient;
import org.camunda.optimize.test.optimize.ExportClient;
import org.camunda.optimize.test.optimize.FlowNodeNamesClient;
import org.camunda.optimize.test.optimize.IdentityClient;
import org.camunda.optimize.test.optimize.ImportClient;
import org.camunda.optimize.test.optimize.IngestionClient;
import org.camunda.optimize.test.optimize.LocalizationClient;
import org.camunda.optimize.test.optimize.ProcessOverviewClient;
import org.camunda.optimize.test.optimize.PublicApiClient;
import org.camunda.optimize.test.optimize.ReportClient;
import org.camunda.optimize.test.optimize.SharingClient;
import org.camunda.optimize.test.optimize.StatusClient;
import org.camunda.optimize.test.optimize.UiConfigurationClient;
import org.camunda.optimize.test.optimize.VariablesClient;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.ACTUATOR_PORT_PROPERTY_KEY;
import static org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.CONTEXT_PATH;
import static org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.HTTPS_PORT_KEY;
import static org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.HTTP_PORT_KEY;
import static org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.INTEGRATION_TESTS;
import static org.camunda.optimize.test.it.extension.MockServerUtil.MOCKSERVER_HOST;

@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
  properties = {INTEGRATION_TESTS + "=true"}
)
@Configuration
public abstract class AbstractIT {

  @RegisterExtension
  @Order(1)
  public static ElasticSearchIntegrationTestExtension elasticSearchIntegrationTestExtension =
    new ElasticSearchIntegrationTestExtension();
  @RegisterExtension
  @Order(2)
  public static EngineIntegrationExtension engineIntegrationExtension = new EngineIntegrationExtension();
  @RegisterExtension
  @Order(3)
  public static EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension();
  @RegisterExtension
  @Order(4)
  public static EngineDatabaseExtension engineDatabaseExtension =
    new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());

  private final Supplier<OptimizeRequestExecutor> optimizeRequestExecutorSupplier =
    () -> getEmbeddedOptimizeExtension().getRequestExecutor();

  protected ClientAndServer useAndGetElasticsearchMockServer() {
    final ClientAndServer esMockServer = elasticSearchIntegrationTestExtension.useEsMockServer();
    embeddedOptimizeExtension.configureEsHostAndPort(MOCKSERVER_HOST, esMockServer.getLocalPort());
    // clear any requests that might have been recorded during configuration reload
    esMockServer.clear(HttpRequest.request());
    return esMockServer;
  }

  protected ClientAndServer useAndGetEngineMockServer() {
    return useAndGetMockServerForEngine(engineIntegrationExtension.getEngineName());
  }

  protected ClientAndServer useAndGetMockServerForEngine(String engineName) {
    String mockServerUrl = "http://" + MOCKSERVER_HOST + ":" +
      IntegrationTestConfigurationUtil.getEngineMockServerPort() + "/engine-rest";
    embeddedOptimizeExtension.configureEngineRestEndpointForEngineWithName(engineName, mockServerUrl);
    return engineIntegrationExtension.useEngineMockServer();
  }

  protected void importAllEngineEntitiesFromScratch() {
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected void importAllEngineEntitiesFromLastIndex() {
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected EmbeddedOptimizeExtension getEmbeddedOptimizeExtension() {
    return embeddedOptimizeExtension;
  }

  // engine test helpers
  protected AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);
  protected OutlierDistributionClient outlierDistributionClient =
    new OutlierDistributionClient(engineIntegrationExtension);

  public void startAndUseNewOptimizeInstance() {
    startAndUseNewOptimizeInstance(new HashMap<>());
  }

  public void startAndUseNewOptimizeInstance(Map<String, String> argMap) {
    String[] arguments = prepareArgs(argMap);

    // run after-test cleanups with the old context
    embeddedOptimizeExtension.afterTest();
    // in case it's not the first *additional* instance, we terminate the first one
    if (embeddedOptimizeExtension.isCloseContextAfterTest()) {
      ((ConfigurableApplicationContext) embeddedOptimizeExtension.getApplicationContext()).close();
    }

    final ConfigurableApplicationContext context = SpringApplication.run(Main.class, arguments);

    embeddedOptimizeExtension.setApplicationContext(context);
    embeddedOptimizeExtension.setCloseContextAfterTest(true);
    embeddedOptimizeExtension.setResetImportOnStart(false);
    embeddedOptimizeExtension.setupOptimize();
  }

  private String[] prepareArgs(final Map<String, String> argMap) {
    final String httpsPort = getPortArg(HTTPS_PORT_KEY);
    final String httpPort = getPortArg(HTTP_PORT_KEY);
    final String actuatorPort = getArg(ACTUATOR_PORT_PROPERTY_KEY, String.valueOf(OptimizeResourceConstants.ACTUATOR_PORT + 100));
    final String contextPath = embeddedOptimizeExtension.getConfigurationService().getContextPath()
      .map(contextPathFromConfig -> getArg(CONTEXT_PATH, contextPathFromConfig)).orElse("");

    final List<String> argList = argMap.entrySet()
      .stream()
      .map(e -> getArg(e.getKey(), e.getValue()))
      .collect(Collectors.toList());

    Collections.addAll(argList, httpsPort, httpPort, actuatorPort, contextPath);

    return argList.toArray(String[]::new);
  }

  private String getArg(String key, String value) {
    return String.format("--%s=%s", key, value);
  }

  private String getPortArg(String portKey) {
    return getArg(portKey, String.valueOf(embeddedOptimizeExtension.getBean(JettyConfig.class).getPort(portKey) + 100));
  }

  protected IncidentClient incidentClient = new IncidentClient(engineIntegrationExtension, engineDatabaseExtension);
  // optimize test helpers
  protected CollectionClient collectionClient = new CollectionClient(optimizeRequestExecutorSupplier);
  protected ReportClient reportClient = new ReportClient(optimizeRequestExecutorSupplier);
  protected AlertClient alertClient = new AlertClient(optimizeRequestExecutorSupplier);
  protected DashboardClient dashboardClient = new DashboardClient(optimizeRequestExecutorSupplier);
  protected EventProcessClient eventProcessClient = new EventProcessClient(optimizeRequestExecutorSupplier);
  protected SharingClient sharingClient = new SharingClient(optimizeRequestExecutorSupplier);
  protected AnalysisClient analysisClient = new AnalysisClient(optimizeRequestExecutorSupplier);
  protected UiConfigurationClient uiConfigurationClient = new UiConfigurationClient(optimizeRequestExecutorSupplier);
  protected EntitiesClient entitiesClient = new EntitiesClient(optimizeRequestExecutorSupplier);
  protected ExportClient exportClient = new ExportClient(optimizeRequestExecutorSupplier);
  protected ImportClient importClient = new ImportClient(optimizeRequestExecutorSupplier);
  protected PublicApiClient publicApiClient = new PublicApiClient(optimizeRequestExecutorSupplier);
  protected DefinitionClient definitionClient = new DefinitionClient(optimizeRequestExecutorSupplier);
  protected VariablesClient variablesClient = new VariablesClient(optimizeRequestExecutorSupplier);
  protected AssigneesClient assigneesClient = new AssigneesClient(optimizeRequestExecutorSupplier);
  protected CandidateGroupClient candidateGroupClient = new CandidateGroupClient(optimizeRequestExecutorSupplier);
  protected FlowNodeNamesClient flowNodeNamesClient = new FlowNodeNamesClient(optimizeRequestExecutorSupplier);
  protected StatusClient statusClient = new StatusClient(optimizeRequestExecutorSupplier);
  protected LocalizationClient localizationClient = new LocalizationClient(optimizeRequestExecutorSupplier);
  protected IdentityClient identityClient = new IdentityClient(optimizeRequestExecutorSupplier);
  protected IngestionClient ingestionClient = new IngestionClient(
    optimizeRequestExecutorSupplier,
    () -> embeddedOptimizeExtension.getConfigurationService().getOptimizeApiConfiguration().getAccessToken()
  );
  protected ProcessOverviewClient processOverviewClient = new ProcessOverviewClient(optimizeRequestExecutorSupplier);
}
