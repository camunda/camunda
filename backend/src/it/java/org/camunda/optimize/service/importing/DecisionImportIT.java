/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.importing.engine.fetcher.definition.DecisionDefinitionFetcher;
import org.camunda.optimize.service.importing.engine.service.DecisionInstanceImportService;
import org.camunda.optimize.test.it.extension.ErrorResponseMock;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.event.Level;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex.ES_TYPE_INDEX_REFERS_TO;
import static org.camunda.optimize.service.importing.engine.handler.DecisionDefinitionImportIndexHandler.DECISION_DEFINITION_IMPORT_INDEX_DOC_ID;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getDecisionInstanceIndexAliasName;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.DmnModels.createDecisionDefinitionWithDate;
import static org.camunda.optimize.util.DmnModels.createDefaultDmnModel;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.StringBody.subString;

public class DecisionImportIT extends AbstractImportIT {

  private static final String DMN_DIAGRAM_NO_INPUT_TYPE = "dmn/compatibility/NoInputType-DMN.dmn";
  private static final String DMN_DIAGRAM_NO_OUTPUT_TYPE = "dmn/compatibility//NoOutputType-DMN.dmn";

  private static final Set<String> DECISION_DEFINITION_NULLABLE_FIELDS =
    Collections.singleton(DecisionDefinitionIndex.TENANT_ID);

  @RegisterExtension
  @Order(5)
  protected final LogCapturer importServiceLogCapturer =
    LogCapturer.create().forLevel(Level.DEBUG).captureForType(DecisionInstanceImportService.class);
  @RegisterExtension
  @Order(6)
  protected final LogCapturer definitionFetcherLogCapturer =
    LogCapturer.create().captureForType(DecisionDefinitionFetcher.class);

  @BeforeEach
  public void cleanUpExistingDecisionInstanceIndices() {
    elasticSearchIntegrationTestExtension.deleteAllProcessInstanceIndices();
    elasticSearchIntegrationTestExtension.deleteAllDecisionInstanceIndices();
  }

  @Test
  public void importOfDecisionDataCanBeDisabled() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setImportDmnDataEnabled(false);
    embeddedOptimizeExtension.reloadConfiguration();
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    BpmnModelInstance exampleProcess = getSimpleBpmnDiagram();
    engineIntegrationExtension.deployAndStartProcess(exampleProcess);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    assertAllEntriesInElasticsearchHaveAllDataWithCount(DECISION_DEFINITION_INDEX_NAME, 0L);
    assertThat(elasticSearchIntegrationTestExtension.indexExists(DECISION_INSTANCE_MULTI_ALIAS)).isFalse();
    assertAllEntriesInElasticsearchHaveAllDataWithCount(PROCESS_INSTANCE_MULTI_ALIAS, 1L);
    assertAllEntriesInElasticsearchHaveAllDataWithCount(PROCESS_DEFINITION_INDEX_NAME, 1L);
  }

  @Test
  public void importOfDecisionDefinition_dataIsImportedOnNextSuccessfulAttemptAfterEsFailures() {
    // given
    importAllEngineEntitiesFromScratch();

    // then
    assertAllEntriesInElasticsearchHaveAllDataWithCount(DECISION_DEFINITION_INDEX_NAME, 0L);

    // given failed ES update requests to store new definition
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest definitionImportMatcher = request()
      .withPath("/_bulk")
      .withMethod(POST)
      .withBody(subString("\"_index\":\"" + embeddedOptimizeExtension.getOptimizeElasticClient()
        .getIndexNameService()
        .getIndexPrefix() + "-" + DECISION_DEFINITION_INDEX_NAME + "\""));
    esMockServer
      .when(definitionImportMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    importAllEngineEntitiesFromLastIndex();

    // then the definition will be stored when update next works
    assertAllEntriesInElasticsearchHaveAllDataWithCount(DECISION_DEFINITION_INDEX_NAME, 1L);
    esMockServer.verify(definitionImportMatcher);
  }

  @Test
  public void importOfDecisionInstance_dataIsImportedOnNextSuccessfulAttemptAfterEsFailures() {
    // given
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.indexExists(DECISION_INSTANCE_MULTI_ALIAS)).isFalse();

    // given failed ES update requests to store new instance
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      engineIntegrationExtension.deployAndStartDecisionDefinition();
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest instanceImportMatcher = request()
      .withPath("/_bulk")
      .withMethod(POST)
      .withBody(subString("\"_index\":\"" + embeddedOptimizeExtension.getOptimizeElasticClient()
        .getIndexNameService()
        .getIndexPrefix() + "-" + DECISION_INSTANCE_INDEX_PREFIX));
    esMockServer
      .when(instanceImportMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    importAllEngineEntitiesFromScratch();

    // then the instance will be stored when update next works
    assertAllEntriesInElasticsearchHaveAllDataWithCount(
      getDecisionInstanceIndexAliasName(decisionDefinitionEngineDto.getKey()),
      1L
    );
    esMockServer.verify(instanceImportMatcher);
  }

  @Test
  public void importCreatesDedicatedDecisionInstanceIndicesPerDefinition() {
    // given two new decision definitions
    final String key1 = "decisionKey1";
    final String key2 = "decisionKey2";
    engineIntegrationExtension.deployAndStartDecisionDefinition(createDefaultDmnModel(key1));
    engineIntegrationExtension.deployAndStartDecisionDefinition(createDefaultDmnModel(key2));

    // when
    importAllEngineEntitiesFromScratch();

    // then both instance indices exist
    assertThat(indicesExist(Arrays.asList(
      new DecisionInstanceIndex(key1),
      new DecisionInstanceIndex(key2)
    ))).isTrue();

    // there is one instance in each index
    final SearchResponse idsInIndex1 = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(getDecisionInstanceIndexAliasName(key1));
    final SearchResponse idsInIndex2 = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(getDecisionInstanceIndexAliasName(key2));
    assertThat(idsInIndex1.getHits().getTotalHits().value).isEqualTo(1L);
    assertThat(idsInIndex2.getHits().getTotalHits().value).isEqualTo(1L);

    // both instances can be found via the multi alias
    final SearchResponse idsInIndices = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DECISION_INSTANCE_MULTI_ALIAS);
    assertThat(idsInIndices.getHits().getTotalHits().value).isEqualTo(2L);
  }

  @Test
  public void importInstancesToCorrectIndexWhenIndexAlreadyExists() {
    // given
    final String key = "decisionKey";
    final DecisionDefinitionEngineDto definition =
      engineIntegrationExtension.deployAndStartDecisionDefinition(createDefaultDmnModel(key));
    importAllEngineEntitiesFromScratch();
    engineIntegrationExtension.startDecisionInstance(definition.getId());

    // when
    importAllEngineEntitiesFromScratch();

    // then there are two instances in one decision index
    final SearchResponse idsInIndex = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(getDecisionInstanceIndexAliasName(key));
    assertThat(idsInIndex.getHits().getTotalHits().value).isEqualTo(2L);
  }

  @Test
  public void allDecisionDefinitionFieldDataIsAvailable() {
    // given
    engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.deployDecisionDefinition();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    assertAllEntriesInElasticsearchHaveAllDataWithCount(
      DECISION_DEFINITION_INDEX_NAME,
      2L,
      DECISION_DEFINITION_NULLABLE_FIELDS
    );
  }

  @Test
  public void importsAllDefinitionsEvenIfTotalAmountIsAboveMaxPageSize() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setEngineImportDecisionDefinitionMaxPageSize(1);
    engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.deployDecisionDefinition();

    // when
    importAllEngineEntitiesFromScratch();
    importAllEngineEntitiesFromLastIndex();

    // then
    assertThat(getDecisionDefinitionCount()).isEqualTo(2L);

    // when
    importAllEngineEntitiesFromLastIndex();

    // then
    assertThat(getDecisionDefinitionCount()).isEqualTo(3L);
  }

  @Test
  public void decisionDefinitionTenantIdIsImportedIfPresent() {
    // given
    final String tenantId = "reallyAwesomeTenantId";
    engineIntegrationExtension.deployDecisionDefinitionWithTenant(tenantId);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DECISION_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(DecisionDefinitionIndex.TENANT_ID, tenantId);
  }

  @Test
  public void decisionDefinitionDefaultEngineTenantIdIsApplied() {
    // given
    final String tenantId = "reallyAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(tenantId);
    engineIntegrationExtension.deployDecisionDefinition();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DECISION_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(DecisionDefinitionIndex.TENANT_ID, tenantId);
  }

  @Test
  public void decisionDefinitionEngineTenantIdIsPreferredOverDefaultTenantId() {
    // given
    final String defaultTenantId = "reallyAwesomeTenantId";
    final String expectedTenantId = "evenMoreAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(defaultTenantId);
    engineIntegrationExtension.deployDecisionDefinitionWithTenant(expectedTenantId);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DECISION_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(DecisionDefinitionIndex.TENANT_ID, expectedTenantId);
  }

  private static Stream<String> tenants() {
    return Stream.of("someTenant", DEFAULT_TENANT);
  }

  @ParameterizedTest
  @MethodSource("tenants")
  public void decisionDefinitionMarkedAsDeletedIfNewDefinitionIdButSameKeyVersionTenant(String tenantId) {
    // given
    final DmnModelInstance definitionModel = createDefaultDmnModel();
    final DecisionDefinitionEngineDto originalDefinition =
      engineIntegrationExtension.deployDecisionDefinition(definitionModel, tenantId);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<DecisionDefinitionOptimizeDto> allDecisionDefinitions =
      elasticSearchIntegrationTestExtension.getAllDecisionDefinitions();
    assertThat(allDecisionDefinitions).singleElement()
      .satisfies(definition -> {
        assertThat(definition.getId()).isEqualTo(originalDefinition.getId());
        assertThat(definition.isDeleted()).isFalse();
      });

    // when the original definition is deleted and a new one deployed with the same key, version and tenant
    engineIntegrationExtension.deleteDeploymentById(originalDefinition.getDeploymentId());
    final DecisionDefinitionEngineDto newDefinition =
      engineIntegrationExtension.deployDecisionDefinition(definitionModel, tenantId);
    importAllEngineEntitiesFromLastIndex();

    // then the original definition is marked as deleted
    final List<DecisionDefinitionOptimizeDto> updatedDefinitions =
      elasticSearchIntegrationTestExtension.getAllDecisionDefinitions();
    assertThat(updatedDefinitions).hasSize(2)
      .allSatisfy(definition -> {
        assertThat(definition.getKey()).isEqualTo(originalDefinition.getKey());
        assertThat(definition.getVersion()).isEqualTo(originalDefinition.getVersionAsString());
        assertThat(definition.getTenantId()).isEqualTo(tenantId);
      })
      .extracting(DefinitionOptimizeResponseDto::getId, DefinitionOptimizeResponseDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(originalDefinition.getId(), true),
        tuple(newDefinition.getId(), false)
      );
    // and the definition cache includes the deleted and new definition
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(originalDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isTrue());
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(newDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
  }

  @Test
  public void decisionDefinitionsMarkedAsDeletedIfMultipleNewDeployments() {
    // given
    final DmnModelInstance definitionModel = createDefaultDmnModel();
    final DecisionDefinitionEngineDto firstDeletedDefinition =
      engineIntegrationExtension.deployDecisionDefinition(definitionModel, DEFAULT_TENANT);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<DecisionDefinitionOptimizeDto> firstDefinitionImported =
      elasticSearchIntegrationTestExtension.getAllDecisionDefinitions();
    assertThat(firstDefinitionImported).singleElement()
      .satisfies(definition -> {
        assertThat(definition.getId()).isEqualTo(firstDeletedDefinition.getId());
        assertThat(definition.isDeleted()).isFalse();
      });

    // when the original definition is deleted and a new one deployed with the same key, version and tenant
    engineIntegrationExtension.deleteDeploymentById(firstDeletedDefinition.getDeploymentId());
    final DecisionDefinitionEngineDto secondDeletedDefinition =
      engineIntegrationExtension.deployDecisionDefinition(definitionModel, DEFAULT_TENANT);
    importAllEngineEntitiesFromLastIndex();

    // then the original definition is marked as deleted
    final List<DecisionDefinitionOptimizeDto> firstTwoDefinitionsImported =
      elasticSearchIntegrationTestExtension.getAllDecisionDefinitions();
    assertThat(firstTwoDefinitionsImported).hasSize(2)
      .allSatisfy(definition -> {
        assertThat(definition.getKey()).isEqualTo(firstDeletedDefinition.getKey());
        assertThat(definition.getVersion()).isEqualTo(firstDeletedDefinition.getVersionAsString());
        assertThat(definition.getTenantId()).isEqualTo(DEFAULT_TENANT);
      })
      .extracting(DefinitionOptimizeResponseDto::getId, DefinitionOptimizeResponseDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(firstDeletedDefinition.getId(), true),
        tuple(secondDeletedDefinition.getId(), false)
      );
    // and the definition cache includes the deleted and new definition
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(firstDeletedDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isTrue());
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(secondDeletedDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());

    // when the second definition is deleted and a new one deployed with the same key, version and tenant
    engineIntegrationExtension.deleteDeploymentById(secondDeletedDefinition.getDeploymentId());
    final DecisionDefinitionEngineDto nonDeletedDefinition =
      engineIntegrationExtension.deployDecisionDefinition(definitionModel, DEFAULT_TENANT);
    importAllEngineEntitiesFromLastIndex();

    // then the deleted definitions are marked accordingly
    final List<DecisionDefinitionOptimizeDto> allDefinitionsImported =
      elasticSearchIntegrationTestExtension.getAllDecisionDefinitions();
    assertThat(allDefinitionsImported).hasSize(3)
      .allSatisfy(definition -> {
        assertThat(definition.getKey()).isEqualTo(firstDeletedDefinition.getKey());
        assertThat(definition.getVersion()).isEqualTo(firstDeletedDefinition.getVersionAsString());
        assertThat(definition.getTenantId()).isEqualTo(DEFAULT_TENANT);
      })
      .extracting(DefinitionOptimizeResponseDto::getId, DefinitionOptimizeResponseDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(firstDeletedDefinition.getId(), true),
        tuple(secondDeletedDefinition.getId(), true),
        tuple(nonDeletedDefinition.getId(), false)
      );
    // and the definition cache includes all definitions with correct deletion state
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(firstDeletedDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isTrue());
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(secondDeletedDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isTrue());
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(nonDeletedDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
  }

  @Test
  public void decisionDefinitionIsResolvedAsDeletedWhenImportingInstance() {
    // given
    final DmnModelInstance definitionModel = createDefaultDmnModel();
    final DecisionDefinitionEngineDto definitionEngineDto =
      engineIntegrationExtension.deployAndStartDecisionDefinition(definitionModel, DEFAULT_TENANT);
    engineIntegrationExtension.deleteDeploymentById(definitionEngineDto.getDeploymentId());
    saveDeletedDefinitionToElasticsearch(definitionEngineDto);
    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionInstanceDto> allDecisionInstances =
      elasticSearchIntegrationTestExtension.getAllDecisionInstances();
    final List<DecisionDefinitionOptimizeDto> allDecisionDefinitions =
      elasticSearchIntegrationTestExtension.getAllDecisionDefinitions();

    // then
    assertThat(allDecisionDefinitions).singleElement()
      .satisfies(def -> assertThat(def.isDeleted()).isTrue());
    assertThat(allDecisionInstances).isNotEmpty()
      .extracting(DecisionInstanceDto::getDecisionDefinitionKey)
      .allMatch(key -> key.equals(definitionEngineDto.getKey()));
  }

  @Test
  public void decisionDefinitionWithInputTypeNull() {
    // given
    engineIntegrationExtension.deployDecisionDefinition(DMN_DIAGRAM_NO_INPUT_TYPE, DEFAULT_TENANT);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<DecisionDefinitionOptimizeDto> allDecisionDefinitions =
      elasticSearchIntegrationTestExtension.getAllDecisionDefinitions();

    List<VariableType> variableTypes = allDecisionDefinitions.stream()
      .flatMap(definition -> definition.getInputVariableNames().stream().map(DecisionVariableNameResponseDto::getType))
      .collect(Collectors.toList());
    assertThat(variableTypes).isNotEmpty()
      .allMatch(type -> type.equals(VariableType.STRING));
  }

  @Test
  public void decisionDefinitionWithOutputTypeNull() {
    // given
    engineIntegrationExtension.deployDecisionDefinition(DMN_DIAGRAM_NO_OUTPUT_TYPE, DEFAULT_TENANT);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<DecisionDefinitionOptimizeDto> allDecisionDefinitions =
      elasticSearchIntegrationTestExtension.getAllDecisionDefinitions();

    List<VariableType> variableTypes = allDecisionDefinitions.stream()
      .flatMap(definition -> definition.getOutputVariableNames().stream().map(DecisionVariableNameResponseDto::getType))
      .collect(Collectors.toList());
    assertThat(variableTypes).isNotEmpty()
      .allMatch(type -> type.equals(VariableType.STRING));
  }

  @Test
  public void decisionDefinitionMarkedAsDeletedOtherVersionsNotAffected() {
    // given
    final DmnModelInstance definitionModel = createDefaultDmnModel();
    final DecisionDefinitionEngineDto originalDefinitionV1 =
      engineIntegrationExtension.deployDecisionDefinition(definitionModel);
    final DecisionDefinitionEngineDto originalDefinitionV2 =
      engineIntegrationExtension.deployDecisionDefinition(definitionModel);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<DecisionDefinitionOptimizeDto> allDecisionDefinitions =
      elasticSearchIntegrationTestExtension.getAllDecisionDefinitions();
    assertThat(allDecisionDefinitions).hasSize(2)
      .extracting(DecisionDefinitionOptimizeDto::getId, DecisionDefinitionOptimizeDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(originalDefinitionV1.getId(), false),
        tuple(originalDefinitionV2.getId(), false)
      );

    // when the v2 definition is deleted and a new one deployed with the same key, version and tenant
    engineIntegrationExtension.deleteDeploymentById(originalDefinitionV2.getDeploymentId());
    final DecisionDefinitionEngineDto newDefinitionV1 =
      engineIntegrationExtension.deployDecisionDefinition(definitionModel);
    importAllEngineEntitiesFromLastIndex();

    // then the original v1 definition is unaffected, the new v2 exists and the original v2 is marked as deleted
    final List<DecisionDefinitionOptimizeDto> updatedDefinitions =
      elasticSearchIntegrationTestExtension.getAllDecisionDefinitions();
    assertThat(updatedDefinitions).hasSize(3)
      .extracting(DecisionDefinitionOptimizeDto::getId, DecisionDefinitionOptimizeDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(originalDefinitionV1.getId(), false),
        tuple(originalDefinitionV2.getId(), true),
        tuple(newDefinitionV1.getId(), false)
      );
    // and the definition cache includes all definitions
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(originalDefinitionV1.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(originalDefinitionV2.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isTrue());
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(newDefinitionV1.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
  }

  @Test
  public void decisionDefinitionMarkedAsDeletedOtherTenantsNotAffected() {
    // given
    final DmnModelInstance definitionModel = createDefaultDmnModel();
    final DecisionDefinitionEngineDto originalDefinition =
      engineIntegrationExtension.deployDecisionDefinition(definitionModel, DEFAULT_TENANT);
    final DecisionDefinitionEngineDto originalDefinitionWithTenant =
      engineIntegrationExtension.deployDecisionDefinition(definitionModel, "someTenant");

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<DecisionDefinitionOptimizeDto> allDecisionDefinitions =
      elasticSearchIntegrationTestExtension.getAllDecisionDefinitions();
    assertThat(allDecisionDefinitions).hasSize(2)
      .extracting(DecisionDefinitionOptimizeDto::getId, DecisionDefinitionOptimizeDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(originalDefinition.getId(), false),
        tuple(originalDefinitionWithTenant.getId(), false)
      );

    // when the original definition is deleted and a new one deployed with the same key, version and tenant
    engineIntegrationExtension.deleteDeploymentById(originalDefinition.getDeploymentId());
    final DecisionDefinitionEngineDto newDefinition =
      engineIntegrationExtension.deployDecisionDefinition(definitionModel);
    importAllEngineEntitiesFromLastIndex();

    // then the original definition with tenant is unaffected, the new one exists and the original is marked as deleted
    final List<DecisionDefinitionOptimizeDto> updatedDefinitions =
      elasticSearchIntegrationTestExtension.getAllDecisionDefinitions();
    assertThat(updatedDefinitions).hasSize(3)
      .extracting(DecisionDefinitionOptimizeDto::getId, DecisionDefinitionOptimizeDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(originalDefinition.getId(), true),
        tuple(originalDefinitionWithTenant.getId(), false),
        tuple(newDefinition.getId(), false)
      );
    // and the definition cache includes all definitions
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(originalDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isTrue());
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(originalDefinitionWithTenant.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(newDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
  }

  @Test
  public void decisionDefinitionMarkedAsDeletedOtherDefinitionKeyNotAffected() {
    // given
    final DmnModelInstance definitionModel = createDefaultDmnModel();
    final DecisionDefinitionEngineDto originalDefinition =
      engineIntegrationExtension.deployDecisionDefinition(definitionModel);
    final DecisionDefinitionEngineDto originalDefinitionWithOtherKey =
      engineIntegrationExtension.deployDecisionDefinition(createDecisionDefinitionWithDate(), "someTenant");

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<DecisionDefinitionOptimizeDto> allDecisionDefinitions =
      elasticSearchIntegrationTestExtension.getAllDecisionDefinitions();
    assertThat(allDecisionDefinitions).hasSize(2)
      .extracting(DecisionDefinitionOptimizeDto::getId, DecisionDefinitionOptimizeDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(originalDefinition.getId(), false),
        tuple(originalDefinitionWithOtherKey.getId(), false)
      );

    // when the original definition is deleted and a new one deployed with the same key, version and tenant
    engineIntegrationExtension.deleteDeploymentById(originalDefinition.getDeploymentId());
    final DecisionDefinitionEngineDto newDefinition =
      engineIntegrationExtension.deployDecisionDefinition(definitionModel);
    importAllEngineEntitiesFromLastIndex();

    // then original definition for other key is unaffected, the new one exists and the original is marked as deleted
    final List<DecisionDefinitionOptimizeDto> updatedDefinitions =
      elasticSearchIntegrationTestExtension.getAllDecisionDefinitions();
    assertThat(updatedDefinitions).hasSize(3)
      .extracting(DecisionDefinitionOptimizeDto::getId, DecisionDefinitionOptimizeDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(originalDefinition.getId(), true),
        tuple(originalDefinitionWithOtherKey.getId(), false),
        tuple(newDefinition.getId(), false)
      );
    // and the definition cache includes all definitions
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(originalDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isTrue());
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(originalDefinitionWithOtherKey.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
    assertThat(embeddedOptimizeExtension.getDecisionDefinitionFromResolverService(newDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
  }

  @Test
  public void decisionDefinitionMarkedAsDeletedIfImportedInSameBatchAsNewerDeployment() {
    // given
    final DmnModelInstance definitionModel = createDefaultDmnModel();
    final DecisionDefinitionEngineDto originalDefinition =
      engineIntegrationExtension.deployDecisionDefinition(definitionModel);
    final DecisionDefinitionEngineDto newDefinition =
      engineIntegrationExtension.deployDecisionDefinition(definitionModel);
    engineDatabaseExtension.changeVersionOfDecisionDefinitionWithDeploymentId(
      newDefinition.getVersionAsString(),
      originalDefinition.getDeploymentId()
    );

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<DecisionDefinitionOptimizeDto> allDecisionDefinitions =
      elasticSearchIntegrationTestExtension.getAllDecisionDefinitions();
    assertThat(allDecisionDefinitions).hasSize(2)
      .extracting(DecisionDefinitionOptimizeDto::isDeleted)
      .containsExactlyInAnyOrder(true, false);
  }

  @Test
  public void decisionInstanceFieldDataIsAvailable() {
    // given
    engineIntegrationExtension.deployAndStartDecisionDefinition();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DECISION_INSTANCE_MULTI_ALIAS);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);

    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertDecisionInstanceFieldSetAsExpected(hit);
  }

  @Test
  public void decisionInstanceTenantIdIsImportedIfPresent() {
    // given
    final String tenantId = "reallyAwesomeTenantId";
    final DecisionDefinitionEngineDto decisionDefinitionDto =
      engineIntegrationExtension.deployDecisionDefinitionWithTenant(
        tenantId);
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DECISION_INSTANCE_MULTI_ALIAS);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(DecisionInstanceIndex.TENANT_ID, tenantId);
  }

  @Test
  public void decisionInstanceDefaultEngineTenantIdIsApplied() {
    // given
    final String tenantId = "reallyAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(tenantId);
    final DecisionDefinitionEngineDto decisionDefinitionDto =
      engineIntegrationExtension.deployAndStartDecisionDefinition();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(getDecisionInstanceIndexAliasName(decisionDefinitionDto.getKey()));
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(DecisionInstanceIndex.TENANT_ID, tenantId);
  }

  @Test
  public void decisionInstanceEngineTenantIdIsPreferredOverDefaultTenantId() {
    // given
    final String defaultTenantId = "reallyAwesomeTenantId";
    final String expectedTenantId = "evenMoreAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(defaultTenantId);
    final DecisionDefinitionEngineDto decisionDefinitionDto =
      engineIntegrationExtension.deployDecisionDefinitionWithTenant(
        expectedTenantId);
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(getDecisionInstanceIndexAliasName(decisionDefinitionDto.getKey()));
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(DecisionInstanceIndex.TENANT_ID, expectedTenantId);
  }

  @Test
  public void multipleDecisionInstancesAreImported() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto =
      engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    // when
    importAllEngineEntitiesFromScratch();

    // then
    assertAllEntriesInElasticsearchHaveAllDataWithCount(
      getDecisionInstanceIndexAliasName(decisionDefinitionDto.getKey()),
      2L
    );
  }

  @Test
  public void decisionImportIndexesAreStored() throws IOException {
    // given
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.deployAndStartDecisionDefinition();

    // when
    importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    SearchResponse searchDecisionInstanceTimestampBasedIndexResponse = getDecisionInstanceIndexResponse();
    assertThat(searchDecisionInstanceTimestampBasedIndexResponse.getHits().getTotalHits().value).isEqualTo(1L);
    final TimestampBasedImportIndexDto decisionInstanceDto = parseToDto(
      searchDecisionInstanceTimestampBasedIndexResponse.getHits().getHits()[0], TimestampBasedImportIndexDto.class
    );
    assertThat(decisionInstanceDto.getTimestampOfLastEntity()).isBefore(OffsetDateTime.now());

    SearchResponse searchDecisionDefinitionIndexResponse = getDecisionDefinitionIndexById();
    assertThat(searchDecisionDefinitionIndexResponse.getHits().getTotalHits().value).isEqualTo(1L);
    final TimestampBasedImportIndexDto definitionImportIndex = parseToDto(
      searchDecisionDefinitionIndexResponse.getHits().getHits()[0],
      TimestampBasedImportIndexDto.class
    );
    assertThat(definitionImportIndex.getEsTypeIndexRefersTo()).isEqualTo(DECISION_DEFINITION_IMPORT_INDEX_DOC_ID);
  }

  @Test
  public void importMoreThanOnePage() {
    // given
    int originalMaxPageSize = embeddedOptimizeExtension.getConfigurationService()
      .getEngineImportProcessInstanceMaxPageSize();
    embeddedOptimizeExtension.getConfigurationService().setEngineImportDecisionInstanceMaxPageSize(1);
    final DecisionDefinitionEngineDto decisionDefinition1 =
      engineIntegrationExtension.deployAndStartDecisionDefinition(createDefaultDmnModel("decisionKey1"));
    final DecisionDefinitionEngineDto decisionDefinition2 =
      engineIntegrationExtension.deployAndStartDecisionDefinition(createDefaultDmnModel("decisionKey2"));

    // when
    importAllEngineEntitiesFromScratch();
    importAllEngineEntitiesFromLastIndex();

    // then
    assertAllEntriesInElasticsearchHaveAllDataWithCount(
      getDecisionInstanceIndexAliasName(decisionDefinition1.getKey()),
      1L
    );
    assertAllEntriesInElasticsearchHaveAllDataWithCount(
      getDecisionInstanceIndexAliasName(decisionDefinition2.getKey()),
      1L
    );
    embeddedOptimizeExtension.getConfigurationService().setEngineImportDecisionInstanceMaxPageSize(originalMaxPageSize);
  }

  @ParameterizedTest
  @MethodSource("engineErrors")
  public void definitionImportWorksEvenIfDeploymentRequestFails(ErrorResponseMock mockedResp) {
    // given
    final ClientAndServer engineMockServer = useAndGetEngineMockServer();
    final HttpRequest requestMatcher = request()
      .withPath(engineIntegrationExtension.getEnginePath() + "/deployment/.*")
      .withMethod(GET);
    mockedResp.mock(requestMatcher, Times.once(), engineMockServer);

    // when
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    importAllEngineEntitiesFromScratch();

    // then
    engineMockServer.verify(requestMatcher, VerificationTimes.exactly(2));
    List<DecisionDefinitionOptimizeDto> decisionDefinitions = definitionClient.getAllDecisionDefinitions();
    assertThat(decisionDefinitions).hasSize(1);
  }

  @Test
  public void decisionInstanceImportIsSkippedIfDefinitionCannotBeResolved() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.deleteDeploymentById(decisionDefinitionEngineDto.getDeploymentId());

    // when
    importAllEngineEntitiesFromScratch();

    // then no definition or instances are saved
    assertThat(definitionClient.getAllDecisionDefinitions()).isEmpty();
    assertThat(elasticSearchIntegrationTestExtension.indexExists(DECISION_INSTANCE_MULTI_ALIAS)).isFalse();
    importServiceLogCapturer.assertContains(String.format(
      "Cannot retrieve definition for definition with ID %s.", decisionDefinitionEngineDto.getId()));
  }

  @Test
  public void decisionInstanceImportIsSkippedIfDefinitionCannotBeResolved_otherInstancesInImportNotAffected() {
    // given
    final DecisionDefinitionEngineDto deletedDefinition =
      engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.deleteDeploymentById(deletedDefinition.getDeploymentId());
    final DecisionDefinitionEngineDto otherDefinition =
      engineIntegrationExtension.deployAndStartDecisionDefinition();

    // when
    importAllEngineEntitiesFromScratch();

    // then only the resolvable non-deleted definition and instances are saved
    assertThat(definitionClient.getAllDecisionDefinitions())
      .singleElement()
      .satisfies(savedDefinition -> assertThat(savedDefinition.getId()).isEqualTo(otherDefinition.getId()));
    assertThat(elasticSearchIntegrationTestExtension.getAllDecisionInstances())
      .singleElement()
      .satisfies(savedInstance -> assertThat(savedInstance.getDecisionDefinitionId()).isEqualTo(otherDefinition.getId()));
    importServiceLogCapturer.assertContains(String.format(
      "Cannot retrieve definition for definition with ID %s.", deletedDefinition.getId()));
  }

  @Test
  public void decisionInstanceImportIsNotSkippedForDefinitionAlreadyImportedButSinceDeleted() {
    // given
    final DecisionDefinitionEngineDto decisionDefinition =
      engineIntegrationExtension.deployAndStartDecisionDefinition();

    // when
    importAllEngineEntitiesFromScratch();

    // then the original definition and instance is imported
    final List<DecisionDefinitionOptimizeDto> savedDefinitions = definitionClient.getAllDecisionDefinitions();
    assertThat(savedDefinitions)
      .singleElement()
      .satisfies(savedDefinition -> assertThat(savedDefinition.getId()).isEqualTo(decisionDefinition.getId()));
    assertThat(elasticSearchIntegrationTestExtension.getAllDecisionInstances())
      .singleElement()
      .satisfies(savedInstance -> assertThat(savedInstance.getDecisionDefinitionId()).isEqualTo(decisionDefinition.getId()));

    // when a new instance is started and the definition is deleted
    engineIntegrationExtension.startDecisionInstance(decisionDefinition.getId());
    engineIntegrationExtension.deleteDeploymentById(decisionDefinition.getDeploymentId());
    importAllEngineEntitiesFromLastIndex();

    // then the new instance is also saved
    assertThat(definitionClient.getAllDecisionDefinitions()).isEqualTo(savedDefinitions);
    assertThat(elasticSearchIntegrationTestExtension.getAllDecisionInstances())
      .hasSize(2)
      .allSatisfy(savedInstance -> assertThat(savedInstance.getDecisionDefinitionId()).isEqualTo(decisionDefinition.getId()));
  }

  @ParameterizedTest
  @MethodSource("engineAuthorizationErrors")
  public void definitionImportMissingAuthorizationLogsMessage(final ErrorResponseMock authorizationError) {
    // given
    final ClientAndServer engineMockServer = useAndGetEngineMockServer();
    final HttpRequest requestMatcher = request()
      .withPath(engineIntegrationExtension.getEnginePath() + "/deployment/.*")
      .withMethod(GET);
    authorizationError.mock(requestMatcher, Times.once(), engineMockServer);

    // when
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    importAllEngineEntitiesFromScratch();

    // then the second request will have succeeded
    engineMockServer.verify(requestMatcher, VerificationTimes.exactly(2));
    definitionFetcherLogCapturer.assertContains(String.format(
      "Error during fetching of entities. Please check the connection with [%s]!" +
        " Make sure all required engine authorizations exist", DEFAULT_ENGINE_ALIAS));
  }

  @Test
  public void definitionImportBadAuthorizationLogsMessage() {
    // given
    final ClientAndServer engineMockServer = useAndGetEngineMockServer();
    final HttpRequest requestMatcher = request()
      .withPath(engineIntegrationExtension.getEnginePath() + "/deployment/.*")
      .withMethod(GET);
    engineMockServer.when(requestMatcher, Times.once())
      .respond(HttpResponse.response().withStatusCode(Response.Status.UNAUTHORIZED.getStatusCode()));

    // when
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    importAllEngineEntitiesFromScratch();

    // then the second request will have succeeded
    engineMockServer.verify(requestMatcher, VerificationTimes.exactly(2));
    definitionFetcherLogCapturer.assertContains(String.format(
      "Error during fetching of entities. Please check the connection with [%s]!" +
        " Make sure you have configured an authorized user", DEFAULT_ENGINE_ALIAS));
  }

  private SearchResponse getDecisionDefinitionIndexById() throws IOException {
    final String decisionDefinitionIndexId = DECISION_DEFINITION_IMPORT_INDEX_DOC_ID + "-" + DEFAULT_ENGINE_ALIAS;
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(termsQuery("_id", decisionDefinitionIndexId))
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(TIMESTAMP_BASED_IMPORT_INDEX_NAME)
      .source(searchSourceBuilder);

    return elasticSearchIntegrationTestExtension.getOptimizeElasticClient().search(searchRequest);
  }

  private SearchResponse getDecisionInstanceIndexResponse() throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(termsQuery(ES_TYPE_INDEX_REFERS_TO, DECISION_INSTANCE_MULTI_ALIAS))
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(TIMESTAMP_BASED_IMPORT_INDEX_NAME)
      .source(searchSourceBuilder);

    return elasticSearchIntegrationTestExtension.getOptimizeElasticClient().search(searchRequest);
  }

  private <T> T parseToDto(final SearchHit searchHit, Class<T> dtoClass) {
    try {
      return elasticSearchIntegrationTestExtension.getObjectMapper().readValue(searchHit.getSourceAsString(), dtoClass);
    } catch (IOException e) {
      throw new RuntimeException("Failed parsing dto: " + dtoClass.getSimpleName());
    }
  }

  public void assertAllEntriesInElasticsearchHaveAllDataWithCount(final String elasticsearchIndex,
                                                                  final long count) {
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(elasticsearchIndex);

    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(count);
    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      // in this test suite we only care about decision types, no asserts besides count on others
      if (elasticsearchIndex.contains(DECISION_INSTANCE_INDEX_PREFIX)) {
        assertDecisionInstanceFieldSetAsExpected(searchHit);
      } else if (DECISION_DEFINITION_INDEX_NAME.equals(elasticsearchIndex)) {
        assertAllFieldsSet(DECISION_DEFINITION_NULLABLE_FIELDS, searchHit);
      }
    }
  }

  private long getDecisionDefinitionCount() {
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DECISION_DEFINITION_INDEX_NAME);
    return idsResp.getHits().getTotalHits().value;
  }

  private void assertDecisionInstanceFieldSetAsExpected(final SearchHit hit) {
    final DecisionInstanceDto dto = parseToDto(hit, DecisionInstanceDto.class);
    assertThat(dto.getProcessDefinitionId()).isNull();
    assertThat(dto.getProcessDefinitionKey()).isNull();
    assertThat(dto.getDecisionDefinitionId()).isNotNull();
    assertThat(dto.getDecisionDefinitionKey()).isNotNull();
    assertThat(dto.getDecisionDefinitionVersion()).isNotNull();
    assertThat(dto.getEvaluationDateTime()).isNotNull();
    assertThat(dto.getProcessInstanceId()).isNull();
    assertThat(dto.getRootProcessInstanceId()).isNull();
    assertThat(dto.getActivityId()).isNull();
    assertThat(dto.getCollectResultValue()).isNull();
    assertThat(dto.getRootDecisionInstanceId()).isNull();
    assertThat(dto.getInputs()).hasSize(2);
    dto.getInputs().forEach(inputInstanceDto -> {
      assertThat(inputInstanceDto.getId()).isNotNull();
      assertThat(inputInstanceDto.getClauseId()).isNotNull();
      assertThat(inputInstanceDto.getClauseName()).isNotNull();
      assertThat(inputInstanceDto.getType()).isNotNull();
      assertThat(inputInstanceDto.getValue()).isNotNull();
    });
    assertThat(dto.getOutputs()).hasSize(2);
    dto.getOutputs().forEach(outputInstanceDto -> {
      assertThat(outputInstanceDto.getId()).isNotNull();
      assertThat(outputInstanceDto.getClauseId()).isNotNull();
      assertThat(outputInstanceDto.getClauseName()).isNotNull();
      assertThat(outputInstanceDto.getType()).isNotNull();
      assertThat(outputInstanceDto.getValue()).isNotNull();
      assertThat(outputInstanceDto.getRuleId()).isNotNull();
      assertThat(outputInstanceDto.getRuleOrder()).isNotNull();
    });
    assertThat(dto.getEngine()).isNotNull();
    assertThat(dto.getTenantId()).isNull();
  }

  private void saveDeletedDefinitionToElasticsearch(final DecisionDefinitionEngineDto definitionEngineDto) {
    final DecisionDefinitionOptimizeDto expectedDto = DecisionDefinitionOptimizeDto.builder()
      .id(definitionEngineDto.getId())
      .key(definitionEngineDto.getKey())
      .name(definitionEngineDto.getName())
      .version(definitionEngineDto.getVersionAsString())
      .tenantId(definitionEngineDto.getTenantId().orElse(null))
      .dataSource(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS))
      .deleted(true)
      .dmn10Xml("someXml")
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      DECISION_DEFINITION_INDEX_NAME,
      expectedDto.getId(),
      expectedDto
    );
  }

}
