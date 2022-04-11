/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

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

import java.util.function.Supplier;

import static org.camunda.optimize.test.it.extension.MockServerUtil.MOCKSERVER_HOST;

public abstract class AbstractIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtension elasticSearchIntegrationTestExtension =
    new ElasticSearchIntegrationTestExtension();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtension engineIntegrationExtension = new EngineIntegrationExtension();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension();
  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtension engineDatabaseExtension =
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
}
