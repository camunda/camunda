/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.schema;

import org.apache.http.client.methods.HttpGet;
import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.service.db.es.schema.ElasticSearchIndexSettingsBuilder;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.db.es.schema.index.ProcessDefinitionIndexES;

import org.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import org.camunda.optimize.service.db.es.schema.index.report.SingleDecisionReportIndexES;
import org.camunda.optimize.service.schema.type.MyUpdatedEventIndex;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.camunda.optimize.util.BpmnModels;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.indices.GetFieldMappingsRequest;
import org.elasticsearch.client.indices.GetFieldMappingsResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.xcontent.DeprecationHandler;
import org.elasticsearch.xcontent.NamedXContentRegistry;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xcontent.json.JsonXContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static jakarta.ws.rs.HttpMethod.HEAD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.service.db.DatabaseConstants.MAX_NGRAM_DIFF;
import static org.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager.INDEX_EXIST_BATCH_SIZE;
import static org.camunda.optimize.service.db.DatabaseConstants.MAPPING_NESTED_OBJECTS_LIMIT;
import static org.camunda.optimize.service.db.DatabaseConstants.METADATA_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_REPLICAS_SETTING;
import static org.camunda.optimize.service.db.DatabaseConstants.REFRESH_INTERVAL_SETTING;
import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.exactly;

public class SchemaManagerIT extends AbstractPlatformIT {

  private OptimizeElasticsearchClient prefixAwareRestHighLevelClient;
  private OptimizeIndexNameService indexNameService;

  @BeforeEach
  public void setUp() {
    // given
    databaseIntegrationTestExtension.cleanAndVerify();
    prefixAwareRestHighLevelClient = embeddedOptimizeExtension.getOptimizeElasticClient();
    indexNameService = prefixAwareRestHighLevelClient.getIndexNameService();
  }

  @Test
  public void schemaIsNotInitializedTwice() {
    // when I initialize schema twice
    initializeSchema();
    initializeSchema();

    // then throws no errors
    assertThatNoException();
  }

  @Test
  public void optimizeIndexExistsAfterSchemaInitialization() {
    // when
    initializeSchema();

    // then
    assertThat(getSchemaManager().schemaExists(prefixAwareRestHighLevelClient)).isTrue();
  }

  @Test
  public void doNotFailIfSomeIndexesAlreadyExist() {
    // given
    initializeSchema();
    embeddedOptimizeExtension.getOptimizeElasticClient().deleteIndex(new SingleDecisionReportIndexES());

    // when
    initializeSchema();

    // then
    assertThat(getSchemaManager().schemaExists(prefixAwareRestHighLevelClient)).isTrue();
  }

  @Test
  public void allTypesExistsAfterSchemaInitialization() throws IOException {
    // when
    initializeSchema();

    // then
    final List<IndexMappingCreator<?>> mappings = getSchemaManager().getMappings();
    assertThat(mappings).hasSize(29);
    for (IndexMappingCreator mapping : mappings) {
      assertIndexExists(mapping.getIndexName());
    }
    final GetSettingsResponse getSettingsResponse = getIndexSettingsFor(mappings);
    assertMappingSettings(mappings, getSettingsResponse);
  }

  @Test
  public void mappingsAreUpdated() throws IOException {
    // given schema is created
    initializeSchema();

    // when there is a new mapping and I update the mapping
    MyUpdatedEventIndex myUpdatedEventIndex = new MyUpdatedEventIndex();
    try {
      getSchemaManager().addMapping(myUpdatedEventIndex);
      initializeSchema();

      // then the mapping contains the new fields
      assertThatNewFieldExists();
    } finally {
      getSchemaManager().getMappings().remove(myUpdatedEventIndex);
    }
  }

  @Test
  public void dynamicSettingsAreUpdated() throws IOException {
    // given schema exists
    initializeSchema();

    // with a different dynamic setting than default
    final List<IndexMappingCreator<?>> mappings = getSchemaManager().getMappings();
    modifyDynamicIndexSetting(mappings);

    // when
    initializeSchema();

    // then the settings contain values from configuration
    final GetSettingsResponse getSettingsResponse = getIndexSettingsFor(mappings);
    assertMappingSettings(mappings, getSettingsResponse);
  }

  @Test
  public void indexExistCheckIsPerformedInBatches() {
    // given
    final int expectedExistQueryBatchExecutionCount =
      (int) Math.ceil((double) getSchemaManager().getMappings().size() / INDEX_EXIST_BATCH_SIZE);
    assertThat(expectedExistQueryBatchExecutionCount).isGreaterThan(1);
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();

    // when
    embeddedOptimizeExtension.getElasticSearchSchemaManager()
      .schemaExists(embeddedOptimizeExtension.getOptimizeElasticClient());

    // then the index exist check was performed in batches
    esMockServer.verify(
      request().withPath(String.format(
        "/(%s.*){2,%s}",
        embeddedOptimizeExtension.getOptimizeElasticClient().getIndexNameService().getIndexPrefix(),
        INDEX_EXIST_BATCH_SIZE
      )).withMethod(HEAD),
      exactly(expectedExistQueryBatchExecutionCount)
    );
  }

  @Test
  public void dynamicSettingsAreAppliedToStaticIndices() throws IOException {
    final String oldRefreshInterval = embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().getRefreshInterval();
    final int oldReplicaCount = embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().getNumberOfReplicas();
    final int oldNestedDocumentLimit = embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().getNestedDocumentsLimit();

    // given schema exists
    embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().setRefreshInterval("100s");
    embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().setNumberOfReplicas(2);
    embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().setNestedDocumentsLimit(10);

    // when
    initializeSchema();

    // then the settings contain the updated dynamic values
    final GetSettingsResponse getSettingsResponse =
      getIndexSettingsFor(Collections.singletonList(new ProcessDefinitionIndexES()));
    final String indexName =
      indexNameService.getOptimizeIndexNameWithVersion(new ProcessDefinitionIndexES());
    final Settings settings = getSettingsResponse.getIndexToSettings().get(indexName);
    assertThat(settings.get("index." + REFRESH_INTERVAL_SETTING)).isEqualTo("100s");
    assertThat(settings.getAsInt("index." + NUMBER_OF_REPLICAS_SETTING, 111)).isEqualTo(2);
    assertThat(settings.getAsInt("index." + MAPPING_NESTED_OBJECTS_LIMIT, 111)).isEqualTo(10);

    // cleanup
    embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().setRefreshInterval(oldRefreshInterval);
    embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().setNumberOfReplicas(oldReplicaCount);
    embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().setNestedDocumentsLimit(oldNestedDocumentLimit);
    initializeSchema();
  }

  @Test
  public void dynamicSettingsAreAppliedToExistingDynamicIndices() throws IOException {
    final String oldRefreshInterval = embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().getRefreshInterval();
    final int oldReplicaCount = embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().getNumberOfReplicas();
    final int oldNestedDocumentLimit = embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().getNestedDocumentsLimit();

    // given a dynamic index is created by the import of process instance data
    final ProcessInstanceEngineDto processInstanceEngineDto = engineIntegrationExtension.deployAndStartProcess(
      BpmnModels.getSimpleBpmnDiagram());
    importAllEngineEntitiesFromScratch();
    // then the dynamic index settings are changed
    embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().setRefreshInterval("100s");
    embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().setNumberOfReplicas(2);
    embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().setNestedDocumentsLimit(10);

    // when
    initializeSchema();

    // then the settings contain the updated dynamic values
    final ProcessInstanceIndex dynamicIndex =
      new ProcessInstanceIndexES(processInstanceEngineDto.getProcessDefinitionKey());
    final GetSettingsResponse getSettingsResponse =
      getIndexSettingsFor(Collections.singletonList(dynamicIndex));
    final String indexName = indexNameService.getOptimizeIndexNameWithVersion(dynamicIndex);
    final Settings settings = getSettingsResponse.getIndexToSettings().get(indexName);
    assertThat(settings.get("index." + REFRESH_INTERVAL_SETTING)).isEqualTo("100s");
    assertThat(settings.getAsInt("index." + NUMBER_OF_REPLICAS_SETTING, 111)).isEqualTo(2);
    assertThat(settings.getAsInt("index." + MAPPING_NESTED_OBJECTS_LIMIT, 111)).isEqualTo(10);

    // cleanup
    embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().setRefreshInterval(oldRefreshInterval);
    embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().setNumberOfReplicas(oldReplicaCount);
    embeddedOptimizeExtension.getConfigurationService().getElasticSearchConfiguration().setNestedDocumentsLimit(oldNestedDocumentLimit);
    initializeSchema();
  }

  @Test
  public void dynamicSettingsAreUpdatedForExistingIndexesWhenNewIndexesAreCreated() throws IOException {
    // given schema exists
    initializeSchema();

    // with a different dynamic setting than default
    final List<IndexMappingCreator<?>> mappings = getSchemaManager().getMappings();
    modifyDynamicIndexSetting(mappings);

    // one index is missing so recreating of indexes is triggered
    embeddedOptimizeExtension.getOptimizeElasticClient().deleteIndex(new SingleDecisionReportIndexES());

    // when
    initializeSchema();

    // then the settings contain values from configuration
    final GetSettingsResponse getSettingsResponse = getIndexSettingsFor(mappings);

    assertMappingSettings(mappings, getSettingsResponse);
  }

  @Test
  public void onlyAcceptDocumentsThatComplyWithTheSchema() {
    // given schema is created
    initializeSchema();

    // then an exception is thrown when we add an event with an undefined type in schema
    ExtendedFlowNodeEventDto extendedEventDto = new ExtendedFlowNodeEventDto();
    assertThatThrownBy(() -> databaseIntegrationTestExtension.addEntryToDatabase(
      DatabaseConstants.METADATA_INDEX_NAME,
      "12312412",
      extendedEventDto
    )).isInstanceOf(ElasticsearchStatusException.class);
  }

  private ElasticSearchSchemaManager getSchemaManager() {
    return embeddedOptimizeExtension.getElasticSearchSchemaManager();
  }

  private void assertMappingSettings(final List<IndexMappingCreator<?>> mappings,
                                     final GetSettingsResponse getSettingsResponse) throws IOException {
    for (IndexMappingCreator<?> mapping : mappings) {
      Settings dynamicSettings = ElasticSearchIndexSettingsBuilder.buildDynamicSettings(
        embeddedOptimizeExtension.getConfigurationService());
      dynamicSettings.names().forEach(
        settingName -> {
          final String setting = getSettingsResponse.getSetting(
            indexNameService.getOptimizeIndexNameWithVersion(mapping),
            "index." + settingName
          );
          assertThat(setting)
            .as("Dynamic setting %s of index %s", settingName, mapping.getIndexName())
            .isEqualTo(dynamicSettings.get(settingName));
        });
      Settings staticSettings =
        buildStaticSettings(mapping, embeddedOptimizeExtension.getConfigurationService());
      staticSettings.keySet().forEach(
        settingName -> {
          final String setting = getSettingsResponse.getSetting(
            indexNameService.getOptimizeIndexNameWithVersion(mapping),
            "index." + settingName
          );
          assertThat(setting)
            .as("Static setting %s of index %s", settingName, mapping.getIndexName())
            .isEqualTo(staticSettings.get(settingName));
        });
    }
  }

  private static Settings buildStaticSettings(IndexMappingCreator<?> indexMappingCreator,
                                              ConfigurationService configurationService) throws IOException {
    XContentBuilder builder = jsonBuilder();
    IndexMappingCreator<XContentBuilder> castedIndexMappingCreator =
      (IndexMappingCreator<XContentBuilder>) indexMappingCreator; // TODO solve this more elegantly
    // @formatter:off
    builder
      .startObject();
        castedIndexMappingCreator.getStaticSettings(builder, configurationService)
      .endObject();
    // @formatter:on
    return Settings.builder().loadFromSource(Strings.toString(builder), XContentType.JSON).build();
  }

  private void modifyDynamicIndexSetting(final List<IndexMappingCreator<?>> mappings) throws IOException {
    for (IndexMappingCreator mapping : mappings) {
      final String indexName = indexNameService.getOptimizeIndexNameWithVersion(mapping);
      final UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest(indexName);
      updateSettingsRequest.settings(Settings.builder().put(MAX_NGRAM_DIFF, "10").build());
      prefixAwareRestHighLevelClient.getHighLevelClient()
        .indices().putSettings(updateSettingsRequest, prefixAwareRestHighLevelClient.requestOptions());
    }
  }

  private GetSettingsResponse getIndexSettingsFor(final List<IndexMappingCreator<?>> mappings) throws IOException {
    final String indices = mappings.stream()
      .map(indexNameService::getOptimizeIndexNameWithVersion)
      .collect(Collectors.joining(","));

    Response response = prefixAwareRestHighLevelClient.getLowLevelClient().performRequest(
      new Request(HttpGet.METHOD_NAME, "/" + indices + "/_settings")
    );
    return GetSettingsResponse.fromXContent(JsonXContent.jsonXContent.createParser(
      NamedXContentRegistry.EMPTY,
      DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
      response.getEntity().getContent()
    ));
  }

  private void assertIndexExists(String indexName) throws IOException {
    OptimizeElasticsearchClient esClient = databaseIntegrationTestExtension.getOptimizeElasticsearchClient();
    GetIndexRequest request = new GetIndexRequest(indexName);
    final boolean indexExists = esClient.exists(request);

    assertThat(indexExists).isTrue();
  }

  private void assertThatNewFieldExists() throws IOException {
    final String aliasForIndex = indexNameService.getOptimizeIndexAliasForIndex(METADATA_INDEX_NAME);

    GetFieldMappingsRequest request = new GetFieldMappingsRequest()
      .indices(aliasForIndex)
      .fields(MyUpdatedEventIndex.MY_NEW_FIELD);
    GetFieldMappingsResponse response =
      prefixAwareRestHighLevelClient.getHighLevelClient()
        .indices()
        .getFieldMapping(request, prefixAwareRestHighLevelClient.requestOptions());

    final MyUpdatedEventIndex updatedEventType = new MyUpdatedEventIndex();
    final GetFieldMappingsResponse.FieldMappingMetadata fieldEntry =
      response.fieldMappings(
        indexNameService.getOptimizeIndexNameWithVersion(updatedEventType),
        MyUpdatedEventIndex.MY_NEW_FIELD
      );

    assertThat(fieldEntry).isNotNull();
  }

  private void initializeSchema() {
    getSchemaManager().initializeSchema(prefixAwareRestHighLevelClient);
  }
}
