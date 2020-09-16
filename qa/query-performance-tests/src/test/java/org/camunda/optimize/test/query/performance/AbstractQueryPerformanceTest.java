/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.query.performance;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.util.PropertyUtil;
import org.camunda.optimize.util.BpmnModels;
import org.camunda.optimize.util.DmnModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;

public abstract class AbstractQueryPerformanceTest {

  protected static final String DEFAULT_USER = "demo";

  private static final String PROPERTY_LOCATION = "query-performance.properties";
  private static final Properties PROPERTIES = PropertyUtil.loadProperties(PROPERTY_LOCATION);
  private static String testDisplayName;

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtension elasticSearchIntegrationTestExtension =
    new ElasticSearchIntegrationTestExtension();

  @RegisterExtension
  @Order(2)
  public EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension();

  @BeforeEach
  public void init(TestInfo testInfo) {
    testDisplayName = testInfo.getTestMethod().map(Method::getName).orElseGet(testInfo::getDisplayName);
  }

  protected long getMaxAllowedQueryTime() {
    String maxQueryTimeString = PROPERTIES.getProperty("camunda.optimize.test.query.max.time.in.ms");
    return Long.parseLong(maxQueryTimeString);
  }

  protected int getNumberOfEntities() {
    String entityCountString = PROPERTIES.getProperty("camunda.optimize.test.query.entity.count");
    return Integer.parseInt(entityCountString);
  }

  protected int getNumberOfEvents() {
    String eventCountString = PROPERTIES.getProperty("camunda.optimize.test.query.event.count");
    return Integer.parseInt(eventCountString);
  }

  protected int getNumberOfDefinitions() {
    String definitionCountString = PROPERTIES.getProperty("camunda.optimize.test.query.definition.count");
    return Integer.parseInt(definitionCountString);
  }

  protected int getNumberOfDefinitionVersions() {
    String definitionVersionCountString =
      PROPERTIES.getProperty("camunda.optimize.test.query.definition.version.count");
    return Integer.parseInt(definitionVersionCountString);
  }

  protected static long getImportTimeout() {
    String timeoutString =
      PROPERTIES.getProperty("camunda.optimize.test.import.timeout.in.hours");
    return Long.parseLong(timeoutString);
  }

  protected static String getTestDisplayName() {
    return testDisplayName;
  }

  protected static ProcessDefinitionOptimizeDto createProcessDefinition(final String key,
                                                                        final String version,
                                                                        final String tenantId,
                                                                        final String name,
                                                                        final String engineAlias) {
    return ProcessDefinitionOptimizeDto.builder()
      .id(key + "-" + version + "-" + tenantId + "-" + engineAlias + "-" + version)
      .key(key)
      .name(name)
      .version(version)
      .versionTag("aVersionTag")
      .tenantId(tenantId)
      .engine(engineAlias)
      .bpmn20Xml(Bpmn.convertToString(BpmnModels.getSingleUserTaskDiagram()))
      .build();
  }

  protected static DecisionDefinitionOptimizeDto createDecisionDefinition(final String key, final String version,
                                                                          final String tenantId, final String name,
                                                                          final String engineAlias) {
    return DecisionDefinitionOptimizeDto.builder()
      .id(key + "-" + version + "-" + tenantId + "-" + engineAlias)
      .key(key)
      .version(version)
      .versionTag("aVersionTag")
      .tenantId(tenantId)
      .engine(engineAlias)
      .name(name)
      .dmn10Xml(Dmn.convertToString(DmnModels.createDefaultDmnModel()))
      .build();
  }

  protected void addToElasticsearch(final String indexName, final Map<String, Object> docsById) {
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(indexName, docsById);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

}
