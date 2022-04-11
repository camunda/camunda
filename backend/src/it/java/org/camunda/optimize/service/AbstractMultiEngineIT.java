/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.engine.DefaultTenant;
import org.camunda.optimize.service.util.configuration.engine.EngineAuthenticationConfiguration;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

public class AbstractMultiEngineIT extends AbstractIT {
  private static final String REST_ENDPOINT = "http://localhost:8080/engine-rest";
  private static final String SECURE_REST_ENDPOINT = "http://localhost:8080/engine-it-plugin/basic-auth";
  protected static final String PROCESS_KEY_1 = "TestProcess1";
  protected static final String PROCESS_KEY_2 = "TestProcess2";
  protected static final String DECISION_KEY_1 = "TestDecision1";
  protected static final String DECISION_KEY_2 = "TestDecision2";
  protected static final String SECOND_ENGINE_ALIAS = "secondTestEngine";
  protected static final String WILDCARD_SUB_PATH = "/.*";

  @RegisterExtension
  @Order(5)
  public EngineIntegrationExtension secondaryEngineIntegrationExtension =
    new EngineIntegrationExtension("anotherEngine");

  protected AuthorizationClient defaultEngineAuthorizationClient = new AuthorizationClient(
    engineIntegrationExtension
  );
  protected AuthorizationClient secondaryEngineAuthorizationClient = new AuthorizationClient(
    secondaryEngineIntegrationExtension
  );

  private ConfigurationService configurationService;

  @BeforeEach
  public void init() {
    configurationService = embeddedOptimizeExtension.getConfigurationService();
  }

  @Override
  public EmbeddedOptimizeExtension getEmbeddedOptimizeExtension() {
    return embeddedOptimizeExtension;
  }

  protected ClientAndServer useAndGetSecondaryEngineMockServer() {
    return useAndGetMockServerForEngine(secondaryEngineIntegrationExtension.getEngineName());
  }

  @SuppressWarnings(UNUSED)
  protected static Stream<Integer> definitionType() {
    return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
  }

  protected void finishAllUserTasksForAllEngines() {
    engineIntegrationExtension.finishAllRunningUserTasks();
    secondaryEngineIntegrationExtension.finishAllRunningUserTasks();
  }

  protected void deployStartAndImportDefinitionForAllEngines(final int definitionResourceType) {
    deployStartAndImportDefinitionForAllEngines(definitionResourceType, null, null);
  }

  protected void deployStartAndImportDefinitionForAllEngines(final int definitionResourceType,
                                                             final String tenantIdEngine1,
                                                             final String tenantIdEngine2) {
    switch (definitionResourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        deployAndStartProcessDefinitionForAllEngines(tenantIdEngine1, tenantIdEngine2);
        break;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        deployAndStartDecisionDefinitionForAllEngines(tenantIdEngine1, tenantIdEngine2);
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported resourceType: " + definitionResourceType);
    }

    importAllEngineEntitiesFromScratch();
  }

  protected void deployAndStartDecisionDefinitionForAllEngines() {
    deployAndStartDecisionDefinitionForAllEngines(null, null);
  }

  private void deployAndStartDecisionDefinitionForAllEngines(final String tenantId1, final String tenantId2) {
    deployAndStartDecisionDefinitionForAllEngines(DECISION_KEY_1, DECISION_KEY_2, tenantId1, tenantId2);
  }

  protected void deployAndStartDecisionDefinitionForAllEngines(final String decisionKey1, final String decisionKey2,
                                                               final String tenantId1, final String tenantId2) {
    deployAndStartDecisionDefinitionOnDefaultEngine(decisionKey1, tenantId1);
    deployAndStartDecisionDefinitionOnSecondEngine(decisionKey2, tenantId2);
  }

  protected void deployAndStartDecisionDefinitionOnSecondEngine(final String decisionKey2, final String tenantId2) {
    secondaryEngineIntegrationExtension.deployAndStartDecisionDefinition(createSimpleDmnModel(decisionKey2), tenantId2);
  }

  protected void deployAndStartDecisionDefinitionOnDefaultEngine(final String decisionKey1, final String tenantId1) {
    engineIntegrationExtension.deployAndStartDecisionDefinition(createSimpleDmnModel(decisionKey1), tenantId1);
  }

  protected void deployAndStartProcessDefinitionForAllEngines() {
    deployAndStartProcessDefinitionForAllEngines(null, null);
  }

  private void deployAndStartProcessDefinitionForAllEngines(final String tenantId1,
                                                            final String tenantId2) {
    deployAndStartProcessDefinitionForAllEngines(PROCESS_KEY_1, PROCESS_KEY_2, tenantId1, tenantId2);
  }

  protected void deployAndStartProcessDefinitionForAllEngines(final String key1,
                                                              final String key2,
                                                              final String tenantId1,
                                                              final String tenantId2) {
    deployAndStartProcessOnDefaultEngine(key1, tenantId1);
    deployAndStartProcessOnSecondEngine(key2, tenantId2);
  }

  protected void deployAndStartProcessOnSecondEngine(final String key2, final String tenantId) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aStringVariable", "foo");
    secondaryEngineIntegrationExtension.deployAndStartProcessWithVariables(
      getSimpleBpmnDiagram(key2),
      variables,
      tenantId
    );
  }

  protected void deployAndStartProcessOnDefaultEngine(final String key1, final String tenantId) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aStringVariable", "foo");
    engineIntegrationExtension.deployAndStartProcessWithVariables(
      getSimpleBpmnDiagram(key1),
      variables,
      tenantId
    );
  }

  protected List<ProcessInstanceEngineDto> deployAndStartUserTaskProcessForAllEngines() {
    final List<ProcessInstanceEngineDto> instances = new ArrayList<>();
    instances.add(
      engineIntegrationExtension.deployAndStartProcess(
        getSingleUserTaskDiagram(PROCESS_KEY_1)
      )
    );
    instances.add(
      secondaryEngineIntegrationExtension.deployAndStartProcess(
        getSingleUserTaskDiagram(PROCESS_KEY_2)
      )
    );
    return instances;
  }

  protected void addSecondEngineToConfiguration() {
    addSecondEngineToConfiguration(true);
  }

  protected void addSecondEngineToConfiguration(final boolean importEnabled) {
    addEngineToConfiguration(secondaryEngineIntegrationExtension.getEngineName(), importEnabled);
  }

  protected void addSecureSecondEngineToConfiguration() {
    addEngineToConfiguration(
      secondaryEngineIntegrationExtension.getEngineName(),
      SECURE_REST_ENDPOINT,
      true,
      "admin",
      "admin",
      true
    );
  }

  private void addEngineToConfiguration(String engineName, final boolean importEnabled) {
    addEngineToConfiguration(engineName, REST_ENDPOINT, false, "", "", importEnabled);
  }

  protected void addNonExistingSecondEngineToConfiguration() {
    addEngineToConfiguration("notExistingEngine", "http://localhost:9999/engine-rest", false, "", "", true);
  }

  private EngineConfiguration addEngineToConfiguration(final String engineName,
                                                       final String restEndpoint,
                                                       final boolean withAuthentication,
                                                       final String username,
                                                       final String password,
                                                       final boolean importEnabled) {
    final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
      .name(engineName)
      .rest(restEndpoint)
      .importEnabled(importEnabled)
      .authentication(
        EngineAuthenticationConfiguration.builder()
          .enabled(withAuthentication)
          .user(username)
          .password(password)
          .build())
      .build();

    configurationService
      .getConfiguredEngines()
      .put(SECOND_ENGINE_ALIAS, engineConfiguration);
    embeddedOptimizeExtension.reloadConfiguration();

    return engineConfiguration;
  }

  protected void removeDefaultEngineConfiguration() {
    configurationService
      .getConfiguredEngines()
      .remove(DEFAULT_ENGINE_ALIAS);
    embeddedOptimizeExtension.reloadConfiguration();
  }


  protected void setDefaultEngineDefaultTenant(final DefaultTenant defaultTenant) {
    embeddedOptimizeExtension.getConfigurationService()
      .getConfiguredEngines()
      .get(DEFAULT_ENGINE_ALIAS)
      .setDefaultTenant(defaultTenant);
  }

  protected void setSecondEngineDefaultTenant(final DefaultTenant defaultTenant) {
    embeddedOptimizeExtension.getConfigurationService()
      .getConfiguredEngines()
      .get(SECOND_ENGINE_ALIAS)
      .setDefaultTenant(defaultTenant);
  }
}
