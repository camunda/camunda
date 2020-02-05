/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.schema;

import org.apache.http.client.methods.HttpGet;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.schema.type.MyUpdatedEventIndex;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.indices.GetFieldMappingsRequest;
import org.elasticsearch.client.indices.GetFieldMappingsResponse;
import org.elasticsearch.client.indices.GetFieldMappingsResponse.FieldMappingMetaData;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.service.es.schema.IndexSettingsBuilder.DYNAMIC_SETTING_MAX_NGRAM_DIFF;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.METADATA_INDEX_NAME;

public class SchemaInitializerIT extends AbstractIT {

  private OptimizeElasticsearchClient prefixAwareRestHighLevelClient;
  private OptimizeIndexNameService indexNameService;

  @BeforeEach
  public void setUp() {
    // given
    elasticSearchIntegrationTestExtension.cleanAndVerify();
    prefixAwareRestHighLevelClient = embeddedOptimizeExtension.getOptimizeElasticClient();
    indexNameService = prefixAwareRestHighLevelClient.getIndexNameService();
  }

  @Test
  public void schemaIsNotInitializedTwice() {

    // when I initialize schema twice
    initializeSchema();
    initializeSchema();

    // then throws no errors
  }

  @Test
  public void optimizeIndexExistsAfterSchemaInitialization() {

    // when
    initializeSchema();

    // then
    assertThat(embeddedOptimizeExtension.getElasticSearchSchemaManager().schemaExists(prefixAwareRestHighLevelClient))
      .isTrue();
  }

  @Test
  public void doNotFailIfSomeIndexesAlreadyExist() throws IOException {
    // given
    initializeSchema();
    embeddedOptimizeExtension.getOptimizeElasticClient().getHighLevelClient().indices().delete(
      new DeleteIndexRequest(indexNameService.getVersionedOptimizeIndexNameForIndexMapping(new DecisionInstanceIndex())),
      RequestOptions.DEFAULT
    );

    //when
    initializeSchema();

    // then
    assertThat(embeddedOptimizeExtension.getElasticSearchSchemaManager()
                 .schemaExists(prefixAwareRestHighLevelClient)).isTrue();
  }

  @Test
  public void allTypesExistsAfterSchemaInitialization() throws IOException {
    // when
    initializeSchema();

    // then
    final List<IndexMappingCreator> mappings = embeddedOptimizeExtension.getElasticSearchSchemaManager().getMappings();
    assertThat(mappings.size()).isEqualTo(26);
    for (IndexMappingCreator mapping : mappings) {
      assertIndexExists(mapping.getIndexName());
    }
    final GetSettingsResponse getSettingsResponse = getIndexSettingsFor(mappings);
    assertDynamicSettingsComplyWithDefaultAndCustomSettings(mappings, getSettingsResponse);
  }

  @Test
  public void mappingsAreUpdated() throws IOException {
    // given schema is created
    initializeSchema();

    // when there is a new mapping and I update the mapping
    MyUpdatedEventIndex myUpdatedEventIndex = new MyUpdatedEventIndex();
    try {
      embeddedOptimizeExtension.getElasticSearchSchemaManager().addMapping(myUpdatedEventIndex);
      initializeSchema();

      // then the mapping contains the new fields
      assertThatNewFieldExists();
    } finally {
      embeddedOptimizeExtension.getElasticSearchSchemaManager().getMappings().remove(myUpdatedEventIndex);
    }
  }

  @Test
  public void dynamicSettingsAreUpdated() throws IOException {
    // given schema exists
    initializeSchema();

    // with a different dynamic setting than default
    final List<IndexMappingCreator> mappings = embeddedOptimizeExtension.getElasticSearchSchemaManager().getMappings();
    modifyDynamicIndexSetting(mappings);

    // when
    initializeSchema();

    // then the settings contain the updated value
    final GetSettingsResponse getSettingsResponse = getIndexSettingsFor(mappings);

    assertDynamicSettingsComplyWithDefaultAndCustomSettings(mappings, getSettingsResponse);
  }

  @Test
  public void dynamicSettingsAreUpdatedForExistingIndexesWhenNewIndexesAreCreated() throws IOException {
    // given schema exists
    initializeSchema();

    // with a different dynamic setting than default
    final List<IndexMappingCreator> mappings = embeddedOptimizeExtension.getElasticSearchSchemaManager().getMappings();
    modifyDynamicIndexSetting(mappings);

    // one index is missing so recreating of indexes is triggered
    embeddedOptimizeExtension.getOptimizeElasticClient().getHighLevelClient().indices().delete(
      new DeleteIndexRequest(indexNameService.getVersionedOptimizeIndexNameForIndexMapping(new DecisionInstanceIndex())),
      RequestOptions.DEFAULT
    );

    // when
    initializeSchema();

    // then the settings contain the updated value
    final GetSettingsResponse getSettingsResponse = getIndexSettingsFor(mappings);

    assertDynamicSettingsComplyWithDefaultAndCustomSettings(mappings, getSettingsResponse);
  }

  @Test
  public void newIndexIsNotAddedDynamically() {
    // given schema is created
    initializeSchema();

    // then an exception is thrown when I add a document to an unknown type
    assertThatThrownBy(() -> elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      "myAwesomeNewIndex", "12312412", new ProcessInstanceDto()))
      .isInstanceOf(ElasticsearchStatusException.class);
  }

  @Test
  public void onlyAcceptDocumentsThatComplyWithTheSchema() {
    // given schema is created
    initializeSchema();

    // then an exception is thrown when we add an event with an undefined type in schema
    ExtendedFlowNodeEventDto extendedEventDto = new ExtendedFlowNodeEventDto();
    assertThatThrownBy(() -> elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      ElasticsearchConstants.METADATA_INDEX_NAME,
      "12312412",
      extendedEventDto
    )).isInstanceOf(ElasticsearchStatusException.class);
  }

  private void assertDynamicSettingsComplyWithDefaultAndCustomSettings(final List<IndexMappingCreator> mappings,
                                                                       final GetSettingsResponse getSettingsResponse) throws IOException {
    for (IndexMappingCreator mapping : mappings) {
      Settings dynamicSettings = IndexSettingsBuilder.buildDynamicSettings(
        embeddedOptimizeExtension.getConfigurationService());
      dynamicSettings.names().forEach(
        settingName -> {
          final String setting = getSettingsResponse.getSetting(
            indexNameService.getVersionedOptimizeIndexNameForIndexMapping(mapping),
            "index." + settingName
          );
          assertThat(setting).isEqualTo(dynamicSettings.get(settingName));
        });
      Settings customSettings = IndexSettingsBuilder.buildCustomSettings(mapping);
      customSettings.keySet().forEach(
        settingName -> {
          final String setting = getSettingsResponse.getSetting(
            indexNameService.getVersionedOptimizeIndexNameForIndexMapping(mapping),
            "index." + settingName
          );
          assertThat(setting).isEqualTo(customSettings.get(settingName));
        });
    }
  }

  private void modifyDynamicIndexSetting(final List<IndexMappingCreator> mappings) throws IOException {
    for (IndexMappingCreator mapping : mappings) {
      final String indexName = indexNameService.getVersionedOptimizeIndexNameForIndexMapping(mapping);
      final UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest(indexName);
      updateSettingsRequest.settings(Settings.builder().put(DYNAMIC_SETTING_MAX_NGRAM_DIFF, "10").build());
      prefixAwareRestHighLevelClient.getHighLevelClient()
        .indices().putSettings(updateSettingsRequest, RequestOptions.DEFAULT);
    }
  }

  private GetSettingsResponse getIndexSettingsFor(final List<IndexMappingCreator> mappings) throws IOException {
    final String indices = mappings.stream()
      .map(indexNameService::getVersionedOptimizeIndexNameForIndexMapping)
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
    final String indexAlias = indexNameService.getOptimizeIndexAliasForIndex(indexName);
    OptimizeElasticsearchClient esClient = elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
    GetIndexRequest request = new GetIndexRequest(indexAlias);
    final boolean indexExists = esClient.exists(request, RequestOptions.DEFAULT);

    assertThat(indexExists).isEqualTo(true);
  }

  private void assertThatNewFieldExists() throws IOException {
    final String aliasForIndex = indexNameService.getOptimizeIndexAliasForIndex(METADATA_INDEX_NAME);

    GetFieldMappingsRequest request = new GetFieldMappingsRequest()
      .indices(aliasForIndex)
      .fields(MyUpdatedEventIndex.MY_NEW_FIELD);
    GetFieldMappingsResponse response =
      prefixAwareRestHighLevelClient.getHighLevelClient().indices().getFieldMapping(request, RequestOptions.DEFAULT);

    final MyUpdatedEventIndex updatedEventType = new MyUpdatedEventIndex();
    final FieldMappingMetaData fieldEntry =
      response.fieldMappings(
        indexNameService.getVersionedOptimizeIndexNameForIndexMapping(updatedEventType),
        MyUpdatedEventIndex.MY_NEW_FIELD
      );

    assertThat(fieldEntry).isNotNull();
  }

  private void initializeSchema() {
    embeddedOptimizeExtension.getElasticSearchSchemaManager().initializeSchema(prefixAwareRestHighLevelClient);
  }
}
