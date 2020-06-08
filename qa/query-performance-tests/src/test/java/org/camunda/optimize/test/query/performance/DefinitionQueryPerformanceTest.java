/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.query.performance;

import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionKeyDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.util.PropertyUtil;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;

public class DefinitionQueryPerformanceTest {
  private static final String PROPERTY_LOCATION = "query-performance.properties";
  private static final Properties properties = PropertyUtil.loadProperties(PROPERTY_LOCATION);

  @RegisterExtension
  @Order(1)
  public static ElasticSearchIntegrationTestExtension elasticSearchIntegrationTestExtension =
    new ElasticSearchIntegrationTestExtension();
  @RegisterExtension
  @Order(2)
  public static EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension();

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testQueryPerformance_getDefinitions(final DefinitionType definitionType) {
    final Integer definitionCount = 11000;

    Map<String, Object> definitionMap = new HashMap<>();
    IntStream
      .range(0, definitionCount)
      .mapToObj(String::valueOf)
      .forEach(i -> {
        final DefinitionOptimizeDto def = createDefinition(
          definitionType, "key" + i, "1", null, "Definition " + i
        );
        definitionMap.put(def.getId(), def);
      });

    addProcessDefinitionsToElasticsearch(definitionType, definitionMap);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    long startTimeMs = System.currentTimeMillis();
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());
    long responseTimeMs = System.currentTimeMillis() - startTimeMs;

    // then
    assertThat(responseTimeMs).isLessThan(getMaxAllowedQueryTime());
  }

  @Test
  public void testQueryPerformance_getDefinitionVersionsWithTenants() {
    final Integer definitionCount = 11000;

    Map<String, Object> definitionMap = new HashMap<>();
    IntStream
      .range(0, definitionCount)
      .mapToObj(String::valueOf)
      .forEach(i -> {
        final DefinitionOptimizeDto def = createProcessDefinition(
          "key" + i,
          "1",
          null,
          "Definition " + i
        );
        definitionMap.put(def.getId(), def);
      });

    addProcessDefinitionsToElasticsearch(DefinitionType.PROCESS, definitionMap);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    long startTimeMs = System.currentTimeMillis();
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());
    long responseTimeMs = System.currentTimeMillis() - startTimeMs;

    // then
    assertThat(responseTimeMs).isLessThan(getMaxAllowedQueryTime());
  }

  @Test
  public void testQueryPerformance_getDefinitionsGroupedByTenant() {
    final Integer definitionCount = 11000;

    Map<String, Object> definitionMap = new HashMap<>();
    IntStream
      .range(0, definitionCount)
      .mapToObj(String::valueOf)
      .forEach(i -> {
        final DefinitionOptimizeDto def = createProcessDefinition(
          "key" + i,
          "1",
          null,
          "Definition " + i
        );
        definitionMap.put(def.getId(), def);
      });

    addProcessDefinitionsToElasticsearch(DefinitionType.PROCESS, definitionMap);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    long startTimeMs = System.currentTimeMillis();
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());
    long responseTimeMs = System.currentTimeMillis() - startTimeMs;

    // then
    assertThat(responseTimeMs).isLessThan(getMaxAllowedQueryTime());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testQueryPerformance_getDefinitionKeys(final DefinitionType definitionType) {
    final int definitionCount = 11000;

    Map<String, Object> definitionMap = new HashMap<>();
    IntStream
      .range(0, definitionCount)
      .mapToObj(String::valueOf)
      .forEach(i -> {
        final DefinitionOptimizeDto def = createDefinition(
          definitionType, "key" + i, "1", null, "Definition " + i
        );
        definitionMap.put(def.getId(), def);
      });

    addProcessDefinitionsToElasticsearch(definitionType, definitionMap);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    long startTimeMs = System.currentTimeMillis();
    final List<DefinitionKeyDto> definitionsKeys = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionKeysByType(definitionType.getId())
      .executeAndReturnList(DefinitionKeyDto.class, Response.Status.OK.getStatusCode());
    long responseTimeMs = System.currentTimeMillis() - startTimeMs;

    // then
    assertThat(definitionsKeys).hasSize(definitionCount);
    assertThat(responseTimeMs).isLessThan(getMaxAllowedQueryTime());
  }

  private DefinitionOptimizeDto createDefinition(final DefinitionType definitionType,
                                                 final String key,
                                                 final String version,
                                                 final String tenantId,
                                                 final String name) {
    switch (definitionType) {
      case PROCESS:
        return createProcessDefinition(key, version, tenantId, name);
      case DECISION:
        return createDecisionDefinition(key, version, tenantId, name);
      default:
        throw new OptimizeIntegrationTestException("Unsupported definition type: " + definitionType);
    }
  }

  private ProcessDefinitionOptimizeDto createProcessDefinition(final String key,
                                                               final String version,
                                                               final String tenantId,
                                                               final String name) {
    return ProcessDefinitionOptimizeDto.builder()
      .id(key + "-" + version + "-" + tenantId)
      .key(key)
      .name(name)
      .version(version)
      .versionTag("aVersionTag")
      .tenantId(tenantId)
      .engine(DEFAULT_ENGINE_ALIAS)
      .bpmn20Xml(key + version + tenantId)
      .build();
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinition(final String key, final String version,
                                                                 final String tenantId, final String name) {
    return DecisionDefinitionOptimizeDto.builder()
      .id(key + "-" + version + "-" + tenantId)
      .key(key)
      .version(version)
      .versionTag(version)
      .tenantId(tenantId)
      .engine(DEFAULT_ENGINE_ALIAS)
      .name(name)
      .dmn10Xml("id-" + key + "-version-" + version + "-" + tenantId)
      .build();
  }

  private void addProcessDefinitionsToElasticsearch(final DefinitionType definitionType,
                                                    final Map<String, Object> definitions) {
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(
      DefinitionType.PROCESS.equals(definitionType) ? PROCESS_DEFINITION_INDEX_NAME : DECISION_DEFINITION_INDEX_NAME,
      definitions
    );
  }

  private long getMaxAllowedQueryTime() {
    String timeoutAsString =
      properties.getProperty("camunda.optimize.test.import.max.query.time.in.ms", "5000");
    return Long.parseLong(timeoutAsString);
  }
}
