/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize;
//
// import static
// io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.PLATFORM_PROFILE;
// import static io.camunda.optimize.test.it.extension.MockServerUtil.MOCKSERVER_HOST;
//
// import io.camunda.optimize.test.engine.AuthorizationClient;
// import io.camunda.optimize.test.engine.IncidentClient;
// import io.camunda.optimize.test.engine.OutlierDistributionClient;
// import io.camunda.optimize.test.it.extension.EngineDatabaseExtension;
// import io.camunda.optimize.test.it.extension.EngineIntegrationExtension;
// import io.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
// import java.util.HashMap;
// import java.util.Map;
// import org.junit.jupiter.api.Order;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.extension.RegisterExtension;
// import org.mockserver.integration.ClientAndServer;
// import org.mockserver.model.HttpRequest;
// import org.springframework.test.context.ActiveProfiles;
//
// @Tag("platform-test")
// @ActiveProfiles(PLATFORM_PROFILE)
// public abstract class AbstractPlatformIT extends AbstractIT {
//
//  @RegisterExtension
//  @Order(2)
//  public static EngineIntegrationExtension engineIntegrationExtension =
//      new EngineIntegrationExtension();
//
//  @RegisterExtension
//  @Order(4)
//  public static EngineDatabaseExtension engineDatabaseExtension =
//      new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());
//
//  // engine test helpers
//  protected AuthorizationClient authorizationClient =
//      new AuthorizationClient(engineIntegrationExtension);
//  protected OutlierDistributionClient outlierDistributionClient =
//      new OutlierDistributionClient(engineIntegrationExtension);
//  protected IncidentClient incidentClient =
//      new IncidentClient(engineIntegrationExtension, engineDatabaseExtension);
//
//  @Override
//  protected void startAndUseNewOptimizeInstance() {
//    startAndUseNewOptimizeInstance(new HashMap<>());
//  }
//
//  protected void startAndUseNewOptimizeInstance(final Map<String, String> argMap) {
//    startAndUseNewOptimizeInstance(argMap, PLATFORM_PROFILE);
//  }
//
//  protected ClientAndServer useAndGetEngineMockServer() {
//    return useAndGetMockServerForEngine(engineIntegrationExtension.getEngineName());
//  }
//
//  protected ClientAndServer useAndGetMockServerForEngine(final String engineName) {
//    final String mockServerUrl =
//        "http://"
//            + MOCKSERVER_HOST
//            + ":"
//            + IntegrationTestConfigurationUtil.getEngineMockServerPort()
//            + "/engine-rest";
//    embeddedOptimizeExtension.configureEngineRestEndpointForEngineWithName(
//        engineName, mockServerUrl);
//    return engineIntegrationExtension.useEngineMockServer();
//  }
//
//  protected ClientAndServer useAndGetDbMockServer() {
//    final ClientAndServer dbMockServer = databaseIntegrationTestExtension.useDbMockServer();
//    embeddedOptimizeExtension.configureDbHostAndPort(MOCKSERVER_HOST,
// dbMockServer.getLocalPort());
//    // clear any requests that might have been recorded during configuration reload
//    dbMockServer.clear(HttpRequest.request());
//    return dbMockServer;
//  }
//
//  protected void importAllEngineEntitiesFromScratch() {
//    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
//    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//  }
//
//  protected void importAllEngineEntitiesFromLastIndex() {
//    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
//    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//  }
// }
