/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.engine.DefaultTenant;
import org.camunda.optimize.service.util.configuration.engine.EngineAuthenticationConfiguration;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;

public class AbstractMultiEngineIT {
  private static final String REST_ENDPOINT = "http://localhost:8080/engine-rest";
  private static final String SECURE_REST_ENDPOINT = "http://localhost:8080/engine-it-plugin/basic-auth";
  public static final String PROCESS_KEY_1 = "TestProcess1";
  public static final String PROCESS_KEY_2 = "TestProcess2";
  public static final String DECISION_KEY_1 = "TestDecision1";
  public static final String DECISION_KEY_2 = "TestDecision2";
  protected final String SECOND_ENGINE_ALIAS = "secondTestEngine";

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule defaultEngineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EngineIntegrationExtensionRule secondaryEngineIntegrationExtensionRule = new EngineIntegrationExtensionRule("anotherEngine");
  @RegisterExtension
  @Order(4)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  private ConfigurationService configurationService;

  @BeforeEach
  public void init() {
    configurationService = embeddedOptimizeExtensionRule.getConfigurationService();
  }

  @AfterEach
  public void reset() {
    configurationService.getConfiguredEngines().remove(SECOND_ENGINE_ALIAS);
    embeddedOptimizeExtensionRule.reloadConfiguration();
  }

  protected void finishAllUserTasksForAllEngines() {
    defaultEngineIntegrationExtensionRule.finishAllRunningUserTasks();
    secondaryEngineIntegrationExtensionRule.finishAllRunningUserTasks();
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

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();
  }

  protected void deployAndStartDecisionDefinitionForAllEngines() {
    deployAndStartDecisionDefinitionForAllEngines(null, null);
  }

  protected void deployAndStartDecisionDefinitionForAllEngines(final String tenantId1, final String tenantId2) {
    defaultEngineIntegrationExtensionRule.deployAndStartDecisionDefinition(createSimpleDmnModel(DECISION_KEY_1), tenantId1);
    secondaryEngineIntegrationExtensionRule.deployAndStartDecisionDefinition(createSimpleDmnModel(DECISION_KEY_2), tenantId2);
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
    secondaryEngineIntegrationExtensionRule.deployAndStartProcessWithVariables(
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
    defaultEngineIntegrationExtensionRule.deployAndStartProcessWithVariables(
      Bpmn.createExecutableProcess(key1)
        .startEvent()
        .endEvent()
        .done(),
      variables,
      tenantId
    );
  }

  protected void deployAndStartUserTaskProcessForAllEngines() {
    defaultEngineIntegrationExtensionRule.deployAndStartProcess(
      Bpmn.createExecutableProcess(PROCESS_KEY_1)
        .startEvent()
        .userTask()
        .endEvent()
        .done()
    );
    secondaryEngineIntegrationExtensionRule.deployAndStartProcess(
      Bpmn.createExecutableProcess(PROCESS_KEY_2)
        .startEvent()
        .userTask()
        .endEvent()
        .done()
    );
  }

  protected void addSecondEngineToConfiguration() {
    addEngineToConfiguration(secondaryEngineIntegrationExtensionRule.getEngineName());
    embeddedOptimizeExtensionRule.reloadConfiguration();
  }

  protected void addSecureSecondEngineToConfiguration() {
    addEngineToConfiguration(secondaryEngineIntegrationExtensionRule.getEngineName(), SECURE_REST_ENDPOINT, true, "admin", "admin");
  }

  protected void addEngineToConfiguration(String engineName) {
    addEngineToConfiguration(engineName, REST_ENDPOINT, false, "", "");
  }

  protected void addNonExistingSecondEngineToConfiguration() {
    addEngineToConfiguration("notExistingEngine", "http://localhost:9999/engine-rest", false, "", "");
    embeddedOptimizeExtensionRule.reloadConfiguration();
  }

  protected void addEngineToConfiguration(String engineName, String restEndpoint, boolean withAuthentication,
                                          String username, String password) {
    EngineAuthenticationConfiguration engineAuthenticationConfiguration = constructEngineAuthenticationConfiguration(
      withAuthentication,
      username,
      password
    );

    EngineConfiguration anotherEngineConfig = new EngineConfiguration();
    anotherEngineConfig.setName(engineName);
    anotherEngineConfig.setRest(restEndpoint);
    anotherEngineConfig.setAuthentication(engineAuthenticationConfiguration);
    configurationService
      .getConfiguredEngines()
      .put(SECOND_ENGINE_ALIAS, anotherEngineConfig);
  }

  protected EngineAuthenticationConfiguration constructEngineAuthenticationConfiguration(boolean withAuthentication,
                                                                                         String username,
                                                                                         String password) {
    EngineAuthenticationConfiguration engineAuthenticationConfiguration = new EngineAuthenticationConfiguration();
    engineAuthenticationConfiguration.setEnabled(withAuthentication);
    engineAuthenticationConfiguration.setPassword(password);
    engineAuthenticationConfiguration.setUser(username);
    return engineAuthenticationConfiguration;
  }

  protected void setDefaultEngineDefaultTenant(final DefaultTenant defaultTenant) {
    embeddedOptimizeExtensionRule.getConfigurationService()
      .getConfiguredEngines()
      .get(DEFAULT_ENGINE_ALIAS)
      .setDefaultTenant(defaultTenant);
  }

  protected void setSecondEngineDefaultTenant(final DefaultTenant defaultTenant) {
    embeddedOptimizeExtensionRule.getConfigurationService()
      .getConfiguredEngines()
      .get(SECOND_ENGINE_ALIAS)
      .setDefaultTenant(defaultTenant);
  }
}
