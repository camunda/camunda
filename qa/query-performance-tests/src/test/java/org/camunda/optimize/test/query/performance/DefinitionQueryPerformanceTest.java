/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.query.performance;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionKeyResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.util.configuration.engine.EngineAuthenticationConfiguration;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

@Slf4j
public class DefinitionQueryPerformanceTest extends AbstractQueryPerformanceTest {

  private static final String SECOND_ENGINE_ALIAS = "second-engine-alias";
  private static final List<String> TENANT_IDS = Lists.newArrayList(null, "tenant1", "tenant2", "tenant3");

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testQueryPerformance_getDefinitions(final DefinitionType definitionType) {
    final int definitionCount = getNumberOfDefinitions();
    addTenantsToElasticsearch();
    final Map<String, Object> definitionMap = generateDefinitions(
      definitionType, definitionCount, DEFAULT_ENGINE_ALIAS, null
    );
    addProcessDefinitionsToElasticsearch(definitionType, definitionMap);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when & then
    assertThatListEndpointMaxAllowedQueryTimeIsMet(
      definitionCount,
      () -> embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetDefinitions()
        .executeAndReturnList(DefinitionResponseDto.class, Response.Status.OK.getStatusCode())
    );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testQueryPerformance_multiEngine_getDefinitions(final DefinitionType definitionType) {
    // given
    final int definitionCountForEachEngine = (getNumberOfDefinitions() + 1) / 2;
    addTenantsToElasticsearch();
    final Map<String, Object> definitionMap = generateDefinitions(
      definitionType, definitionCountForEachEngine, DEFAULT_ENGINE_ALIAS, null
    );
    addProcessDefinitionsToElasticsearch(definitionType, definitionMap);

    Map<String, Object> secondEngineDefinitions = generateDefinitions(
      definitionType, definitionCountForEachEngine, SECOND_ENGINE_ALIAS, null
    );
    addProcessDefinitionsToElasticsearch(definitionType, secondEngineDefinitions);
    // adding second engine alias as last given action to avoid a race condition between async refreshes running in
    // AbstractCachingAuthorizationService after EmbeddedOptimizeExtension#setupOptimize (beforeEach test) has been run
    // and the EmbeddedOptimizeExtension#reloadConfiguration call happening after the engine has been added
    addSecondEngineAliasToConfiguration();

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when & then
    assertThatListEndpointMaxAllowedQueryTimeIsMet(
      definitionCountForEachEngine * 2,
      () -> embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetDefinitions()
        .executeAndReturnList(DefinitionResponseDto.class, Response.Status.OK.getStatusCode())
    );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testQueryPerformance_getDefinitionsGroupedByTenant(final DefinitionType definitionType) {
    final int definitionCount = getNumberOfDefinitions();

    addTenantsToElasticsearch();

    final Map<String, Object> definitionMap = generateDefinitions(
      definitionType, definitionCount, DEFAULT_ENGINE_ALIAS, null
    );
    addProcessDefinitionsToElasticsearch(definitionType, definitionMap);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when & then
    assertThatListEndpointMaxAllowedQueryTimeIsMet(
      TENANT_IDS.size(),
      () -> embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetDefinitionsGroupedByTenant()
        .executeAndReturnList(TenantWithDefinitionsResponseDto.class, Response.Status.OK.getStatusCode()),
      elements -> assertThat(elements)
        .extracting(TenantWithDefinitionsResponseDto::getDefinitions)
        .allSatisfy(definitions -> {
          assertThat(definitions).hasSize(definitionCount);
        }), getMaxAllowedQueryTime()
    );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testQueryPerformance_multiEngine_getDefinitionsGroupedByTenant(final DefinitionType definitionType) {
    // given
    final int definitionCountForEachEngine = (getNumberOfDefinitions() + 1) / 2;
    addTenantsToElasticsearch();

    final Map<String, Object> defaultEngineDefinitions = generateDefinitions(
      definitionType, definitionCountForEachEngine, DEFAULT_ENGINE_ALIAS, null
    );
    addProcessDefinitionsToElasticsearch(definitionType, defaultEngineDefinitions);

    Map<String, Object> secondEngineDefinitions = generateDefinitions(
      definitionType, definitionCountForEachEngine, SECOND_ENGINE_ALIAS, null
    );
    addProcessDefinitionsToElasticsearch(definitionType, secondEngineDefinitions);
    // adding second engine alias as last given action to avoid a race condition between async refreshes running in
    // AbstractCachingAuthorizationService after EmbeddedOptimizeExtension#setupOptimize (beforeEach test) has been run
    // and the EmbeddedOptimizeExtension#reloadConfiguration call happening after the engine has been added
    addSecondEngineAliasToConfiguration();

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when & then
    assertThatListEndpointMaxAllowedQueryTimeIsMet(
      TENANT_IDS.size(),
      () -> embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetDefinitionsGroupedByTenant()
        .executeAndReturnList(TenantWithDefinitionsResponseDto.class, Response.Status.OK.getStatusCode()),
      elements -> assertThat(elements).hasSameSizeAs(TENANT_IDS)
        .allSatisfy(element -> {
          if (element.getId() == null) {
            // the null tenant has definitions from both engines
            assertThat(element.getDefinitions()).hasSize(definitionCountForEachEngine * 2);
          } else {
            assertThat(element.getDefinitions()).hasSize(definitionCountForEachEngine);
          }
        }), getMaxAllowedQueryTime()
    );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testQueryPerformance_getDefinitionKeys(final DefinitionType definitionType) {
    final int definitionCount = getNumberOfDefinitions();

    addTenantsToElasticsearch();
    final Map<String, Object> definitionMap = generateDefinitions(
      definitionType, definitionCount, DEFAULT_ENGINE_ALIAS, null
    );
    addProcessDefinitionsToElasticsearch(definitionType, definitionMap);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when & then
    assertThatListEndpointMaxAllowedQueryTimeIsMet(
      definitionCount,
      () -> embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetDefinitionKeysByType(definitionType.getId())
        .executeAndReturnList(DefinitionKeyResponseDto.class, Response.Status.OK.getStatusCode())
    );
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testQueryPerformance_multiEngine_getDefinitionKeys(final DefinitionType definitionType) {
    // given
    final int definitionCountForEachEngine = (getNumberOfDefinitions() + 1) / 2;
    addTenantsToElasticsearch();
    Map<String, Object> defaultEngineDefinitions = generateDefinitions(
      definitionType, definitionCountForEachEngine, DEFAULT_ENGINE_ALIAS, null
    );
    addProcessDefinitionsToElasticsearch(definitionType, defaultEngineDefinitions);

    Map<String, Object> secondEngineDefinitions = generateDefinitions(
      definitionType, definitionCountForEachEngine, SECOND_ENGINE_ALIAS, null
    );
    addProcessDefinitionsToElasticsearch(definitionType, secondEngineDefinitions);
    // adding second engine alias as last given action to avoid a race condition between async refreshes running in
    // AbstractCachingAuthorizationService after EmbeddedOptimizeExtension#setupOptimize (beforeEach test) has been run
    // and the EmbeddedOptimizeExtension#reloadConfiguration call happening after the engine has been added
    addSecondEngineAliasToConfiguration();

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when & then
    assertThatListEndpointMaxAllowedQueryTimeIsMet(
      2 * definitionCountForEachEngine,
      () -> embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetDefinitionKeysByType(definitionType.getId())
        .executeAndReturnList(DefinitionKeyResponseDto.class, Response.Status.OK.getStatusCode())
    );
  }

  private void addSecondEngineAliasToConfiguration() {
    // we just use the same real engine as we only want to make Optimize call two engines and don't need two real ones
    final EngineConfiguration defaultEngineConfiguration = embeddedOptimizeExtension.getDefaultEngineConfiguration();
    final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
      // using same engine name as we just point to the same engine again with another alias
      .name(defaultEngineConfiguration.getName())
      .rest(defaultEngineConfiguration.getRest())
      .importEnabled(false)
      .authentication(EngineAuthenticationConfiguration.builder().enabled(false).build())
      .build();

    embeddedOptimizeExtension.getConfigurationService().getConfiguredEngines()
      .put(SECOND_ENGINE_ALIAS, engineConfiguration);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  private Map<String, Object> generateDefinitions(final DefinitionType definitionType,
                                                  final int definitionCount,
                                                  final String engineAlias,
                                                  final String tenantId) {
    Map<String, Object> definitionMap = new HashMap<>();
    IntStream
      .range(0, definitionCount)
      .mapToObj(String::valueOf)
      .forEach(i -> {
        final DefinitionOptimizeResponseDto def = createDefinition(
          definitionType, "key" + i + engineAlias, "1", tenantId, "Definition " + i, engineAlias
        );
        definitionMap.put(def.getId(), def);
      });
    return definitionMap;
  }

  private DefinitionOptimizeResponseDto createDefinition(final DefinitionType definitionType,
                                                         final String key,
                                                         final String version,
                                                         final String tenantId,
                                                         final String name, final String engineAlias) {
    switch (definitionType) {
      case PROCESS:
        return createProcessDefinition(key, version, tenantId, name, engineAlias);
      case DECISION:
        return createDecisionDefinition(key, version, tenantId, name, engineAlias);
      default:
        throw new OptimizeIntegrationTestException("Unsupported definition type: " + definitionType);
    }
  }

  private void addProcessDefinitionsToElasticsearch(final DefinitionType definitionType,
                                                    final Map<String, Object> definitions) {
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(
      DefinitionType.PROCESS.equals(definitionType) ? PROCESS_DEFINITION_INDEX_NAME : DECISION_DEFINITION_INDEX_NAME,
      definitions
    );
  }

  private void addTenantsToElasticsearch() {
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(
      TENANT_INDEX_NAME,
      TENANT_IDS.stream()
        .filter(Objects::nonNull)
        .map(id -> new TenantDto(id, id, DEFAULT_ENGINE_ALIAS))
        .collect(toMap(TenantDto::getId, Function.identity()))
    );
  }

}
