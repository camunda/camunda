/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.engine.DefaultTenant;
import org.camunda.optimize.service.util.configuration.engine.EngineAuthenticationConfiguration;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.util.configuration.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;

public class AbstractMultiEngineIT extends AbstractIT {
  private static final String REST_ENDPOINT = "http://localhost:8080/engine-rest";
  private static final String SECURE_REST_ENDPOINT = "http://localhost:8080/engine-it-plugin/basic-auth";
  public static final String PROCESS_KEY_1 = "TestProcess1";
  public static final String PROCESS_KEY_2 = "TestProcess2";
  public static final String DECISION_KEY_1 = "TestDecision1";
  public static final String DECISION_KEY_2 = "TestDecision2";
  protected final String SECOND_ENGINE_ALIAS = "secondTestEngine";

  @RegisterExtension
  @Order(3)
  public EngineIntegrationExtension secondaryEngineIntegrationExtension = new EngineIntegrationExtension(
    "anotherEngine");
  @RegisterExtension
  @Order(4)
  public EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension();

  private ConfigurationService configurationService;

  @BeforeEach
  public void init() {
    configurationService = embeddedOptimizeExtension.getConfigurationService();
  }

  @AfterEach
  public void reset() {
    configurationService.getConfiguredEngines().remove(SECOND_ENGINE_ALIAS);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  protected ClientAndServer useAndGetSecondaryEngineMockServer() {
    return useAndGetMockServerForEngine(secondaryEngineIntegrationExtension.getEngineName());
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
        deployAndStartSimpleProcessDefinitionForAllEngines(tenantIdEngine1, tenantIdEngine2);
        break;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        deployAndStartDecisionDefinitionForAllEngines(tenantIdEngine1, tenantIdEngine2);
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported resourceType: " + definitionResourceType);
    }

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected void deployAndStartDecisionDefinitionForAllEngines() {
    deployAndStartDecisionDefinitionForAllEngines(null, null);
  }

  protected void deployAndStartDecisionDefinitionForAllEngines(final String tenantId1, final String tenantId2) {
    engineIntegrationExtension.deployAndStartDecisionDefinition(createSimpleDmnModel(DECISION_KEY_1), tenantId1);
    secondaryEngineIntegrationExtension.deployAndStartDecisionDefinition(
      createSimpleDmnModel(DECISION_KEY_2),
      tenantId2
    );
  }

  protected void deployAndStartSimpleProcessDefinitionForAllEngines() {
    deployAndStartSimpleProcessDefinitionForAllEngines(null, null);
  }

  protected void deployAndStartSimpleProcessDefinitionForAllEngines(final String tenantId1,
                                                                    final String tenantId2) {
    deployAndStartSimpleProcessDefinitionForAllEngines(PROCESS_KEY_1, PROCESS_KEY_2, tenantId1, tenantId2);
  }

  protected void deployAndStartSimpleProcessDefinitionForAllEngines(final String key1,
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
      Bpmn.createExecutableProcess(key2)
        .startEvent()
        .endEvent()
        .done(),
      variables,
      tenantId
    );
  }

  protected void deployAndStartProcessOnDefaultEngine(final String key1, final String tenantId) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aStringVariable", "foo");
    engineIntegrationExtension.deployAndStartProcessWithVariables(
      Bpmn.createExecutableProcess(key1)
        .startEvent()
        .endEvent()
        .done(),
      variables,
      tenantId
    );
  }

  protected List<ProcessInstanceEngineDto> deployAndStartUserTaskProcessForAllEngines() {
    final List<ProcessInstanceEngineDto> instances = new ArrayList<>();
    instances.add(
      engineIntegrationExtension.deployAndStartProcess(
        Bpmn.createExecutableProcess(PROCESS_KEY_1)
          .startEvent()
          .userTask()
          .endEvent()
          .done()
      )
    );
    instances.add(
      secondaryEngineIntegrationExtension.deployAndStartProcess(
        Bpmn.createExecutableProcess(PROCESS_KEY_2)
          .startEvent()
          .userTask()
          .endEvent()
          .done()
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

  protected void addEngineToConfiguration(String engineName, final boolean importEnabled) {
    addEngineToConfiguration(engineName, REST_ENDPOINT, false, "", "", importEnabled);
  }

  protected void addNonExistingSecondEngineToConfiguration() {
    addEngineToConfiguration("notExistingEngine", "http://localhost:9999/engine-rest", false, "", "", true);
  }

  protected EngineConfiguration addEngineToConfiguration(final String engineName,
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
      .remove("1");
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
