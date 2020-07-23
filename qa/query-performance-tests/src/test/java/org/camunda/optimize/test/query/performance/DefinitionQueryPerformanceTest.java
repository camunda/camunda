/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.query.performance;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionKeyDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.util.configuration.engine.EngineAuthenticationConfiguration;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
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

    // when
    final Instant start = Instant.now();
    final List<DefinitionWithTenantsDto> definitionWithTenantsDtos = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());
    final Instant finish = Instant.now();
    long responseTimeMs = Duration.between(start, finish).toMillis();

    // then
    assertThat(definitionWithTenantsDtos).hasSize(definitionCount);
    log.info("{} query response time: {}", getTestDisplayName(), responseTimeMs);
    assertThat(responseTimeMs).isLessThanOrEqualTo(getMaxAllowedQueryTime());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testQueryPerformance_multiEngine_getDefinitions(final DefinitionType definitionType) {
    // given
    addSecondEngineToConfiguration();
    final int definitionCount = getNumberOfDefinitions();

    addTenantsToElasticsearch();
    final Map<String, Object> definitionMap = generateDefinitions(
      definitionType, definitionCount, DEFAULT_ENGINE_ALIAS, null
    );
    addProcessDefinitionsToElasticsearch(definitionType, definitionMap);

    Map<String, Object> secondEngineDefinitions = generateDefinitions(
      definitionType, definitionCount, SECOND_ENGINE_ALIAS, null
    );
    addProcessDefinitionsToElasticsearch(definitionType, secondEngineDefinitions);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final Instant start = Instant.now();
    final List<DefinitionWithTenantsDto> definitionWithTenantsDtos = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());
    final Instant finish = Instant.now();
    long responseTimeMs = Duration.between(start, finish).toMillis();

    // then
    assertThat(definitionWithTenantsDtos).hasSize(definitionCount);
    log.info("{} query response time: {}", getTestDisplayName(), responseTimeMs);
    assertThat(responseTimeMs).isLessThanOrEqualTo(getMaxAllowedQueryTime());
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

    // when
    final Instant start = Instant.now();
    final List<TenantWithDefinitionsDto> definitionWithTenantsDtos = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(TenantWithDefinitionsDto.class, Response.Status.OK.getStatusCode());
    final Instant finish = Instant.now();
    long responseTimeMs = Duration.between(start, finish).toMillis();

    // then
    assertThat(definitionWithTenantsDtos)
      .hasSize(TENANT_IDS.size())
      .extracting(TenantWithDefinitionsDto::getDefinitions)
      .allSatisfy(definitions -> {
        assertThat(definitions).hasSize(definitionCount);
      });
    log.info("{} query response time: {}", getTestDisplayName(), responseTimeMs);
    assertThat(responseTimeMs).isLessThanOrEqualTo(getMaxAllowedQueryTime());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testQueryPerformance_multiEngine_getDefinitionsGroupedByTenant(final DefinitionType definitionType) {
    // given
    addSecondEngineToConfiguration();
    final int definitionCount = getNumberOfDefinitions();

    addTenantsToElasticsearch();

    final Map<String, Object> defaultEngineDefinitions = generateDefinitions(
      definitionType, definitionCount, DEFAULT_ENGINE_ALIAS, null
    );
    addProcessDefinitionsToElasticsearch(definitionType, defaultEngineDefinitions);

    Map<String, Object> secondEngineDefinitions = generateDefinitions(
      definitionType, definitionCount, SECOND_ENGINE_ALIAS, null
    );
    addProcessDefinitionsToElasticsearch(definitionType, secondEngineDefinitions);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final Instant start = Instant.now();
    final List<TenantWithDefinitionsDto> definitionWithTenantsDtos = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(TenantWithDefinitionsDto.class, Response.Status.OK.getStatusCode());
    final Instant finish = Instant.now();
    long responseTimeMs = Duration.between(start, finish).toMillis();

    // then
    assertThat(definitionWithTenantsDtos)
      .hasSize(TENANT_IDS.size())
      .extracting(TenantWithDefinitionsDto::getDefinitions)
      .allSatisfy(definitions -> {
        assertThat(definitions).hasSize(definitionCount);
      });
    log.info("{} query response time: {}", getTestDisplayName(), responseTimeMs);
    assertThat(responseTimeMs).isLessThanOrEqualTo(getMaxAllowedQueryTime());
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

    // when
    final Instant start = Instant.now();
    final List<DefinitionKeyDto> definitionsKeys = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionKeysByType(definitionType.getId())
      .executeAndReturnList(DefinitionKeyDto.class, Response.Status.OK.getStatusCode());
    final Instant finish = Instant.now();
    long responseTimeMs = Duration.between(start, finish).toMillis();

    // then
    assertThat(definitionsKeys).hasSize(definitionCount);
    log.info("{} query response time: {}", getTestDisplayName(), responseTimeMs);
    assertThat(responseTimeMs).isLessThanOrEqualTo(getMaxAllowedQueryTime());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testQueryPerformance_multiEngine_getDefinitionKeys(final DefinitionType definitionType) {
    // given
    addSecondEngineToConfiguration();
    final int definitionCount = getNumberOfDefinitions();

    addTenantsToElasticsearch();
    Map<String, Object> defaultEngineDefinitions = generateDefinitions(
      definitionType, definitionCount, DEFAULT_ENGINE_ALIAS, null
    );
    addProcessDefinitionsToElasticsearch(definitionType, defaultEngineDefinitions);

    Map<String, Object> secondEngineDefinitions = generateDefinitions(
      definitionType, definitionCount, SECOND_ENGINE_ALIAS, null
    );
    addProcessDefinitionsToElasticsearch(definitionType, secondEngineDefinitions);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final Instant start = Instant.now();
    final List<DefinitionKeyDto> definitionsKeys = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionKeysByType(definitionType.getId())
      .executeAndReturnList(DefinitionKeyDto.class, Response.Status.OK.getStatusCode());
    final Instant finish = Instant.now();
    long responseTimeMs = Duration.between(start, finish).toMillis();

    // then
    assertThat(definitionsKeys).hasSize(definitionCount);
    log.info("{} query response time: {}", getTestDisplayName(), responseTimeMs);
    assertThat(responseTimeMs).isLessThanOrEqualTo(getMaxAllowedQueryTime());
  }

  private void addSecondEngineToConfiguration() {
    final EngineConfiguration defaultEngineConfiguration = embeddedOptimizeExtension.getDefaultEngineConfiguration();
    final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
      .name(SECOND_ENGINE_ALIAS)
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
        final DefinitionOptimizeDto def = createDefinition(
          definitionType, "key" + i, "1", tenantId, "Definition " + i, engineAlias
        );
        definitionMap.put(def.getId(), def);
      });
    return definitionMap;
  }

  private DefinitionOptimizeDto createDefinition(final DefinitionType definitionType,
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
