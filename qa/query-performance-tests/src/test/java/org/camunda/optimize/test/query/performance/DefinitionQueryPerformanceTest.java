/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.query.performance;

import org.assertj.core.api.Assertions;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.util.PropertyUtil;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.IntStream;

import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
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

  @Test
  public void testQueryPerformance_getDefinitions() {
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

    addProcessDefinitionsToElasticsearch(definitionMap);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    long startTimeMs = System.currentTimeMillis();
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());
    long responseTimeMs = System.currentTimeMillis() - startTimeMs;

    // then
    Assertions.assertThat(responseTimeMs).isLessThan(getMaxAllowedQueryTime());
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

    addProcessDefinitionsToElasticsearch(definitionMap);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    long startTimeMs = System.currentTimeMillis();
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDecisionDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());
    long responseTimeMs = System.currentTimeMillis() - startTimeMs;

    // then
    Assertions.assertThat(responseTimeMs).isLessThan(getMaxAllowedQueryTime());
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

    addProcessDefinitionsToElasticsearch(definitionMap);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    long startTimeMs = System.currentTimeMillis();
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());
    long responseTimeMs = System.currentTimeMillis() - startTimeMs;

    // then
    Assertions.assertThat(responseTimeMs).isLessThan(getMaxAllowedQueryTime());
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

  private void addProcessDefinitionsToElasticsearch(final Map<String, Object> definitions) {
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(PROCESS_DEFINITION_INDEX_NAME, definitions);
  }

  private long getMaxAllowedQueryTime() {
    String timeoutAsString =
      properties.getProperty("camunda.optimize.test.import.max.query.time.in.ms", "5000");
    return Long.parseLong(timeoutAsString);
  }
}
