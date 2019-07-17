/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.schema;

import com.jayway.jsonpath.JsonPath;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.TypeMappingCreator;
import org.camunda.optimize.service.es.schema.type.DecisionInstanceType;
import org.camunda.optimize.service.schema.type.MyUpdatedEventType;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse.FieldMappingMetaData;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.IndexSettingsBuilder.DYNAMIC_SETTING_MAX_NGRAM_DIFF;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SchemaInitializerIT {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  public static ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public static EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private OptimizeElasticsearchClient prefixAwareRestHighLevelClient;
  private OptimizeIndexNameService indexNameService;

  @ClassRule
  public static RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Before
  public void setUp() {
    // given
    elasticSearchRule.cleanAndVerify();
    prefixAwareRestHighLevelClient = embeddedOptimizeRule.getOptimizeElasticClient();
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
    assertThat(
      embeddedOptimizeRule.getElasticSearchSchemaManager().schemaAlreadyExists(prefixAwareRestHighLevelClient),
      is(true)
    );
  }

  @Test
  public void dontFailIfSomeIndexesAlreadyExist() throws IOException {

    // given
    initializeSchema();
    embeddedOptimizeRule.getOptimizeElasticClient().getHighLevelClient().indices().delete(
      new DeleteIndexRequest(indexNameService.getVersionedOptimizeIndexNameForTypeMapping(new DecisionInstanceType())),
      RequestOptions.DEFAULT
    );

    //when
    initializeSchema();

    // then
    assertThat(
      embeddedOptimizeRule.getElasticSearchSchemaManager().schemaAlreadyExists(prefixAwareRestHighLevelClient),
      is(true)
    );
  }

  @Test
  public void allTypesExistsAfterSchemaInitialization() throws IOException {

    // when
    initializeSchema();

    // then
    final List<TypeMappingCreator> mappings = embeddedOptimizeRule.getElasticSearchSchemaManager().getMappings();
    assertThat(mappings.size(), is(18));
    for (TypeMappingCreator mapping : mappings) {
      assertTypeExists(mapping.getType());
    }
  }

  @Test
  public void mappingsAreUpdated() throws IOException {

    // given schema is created
    initializeSchema();

    // when there is a new mapping and I update the mapping
    MyUpdatedEventType updatedEventType = new MyUpdatedEventType();
    try {
      embeddedOptimizeRule.getElasticSearchSchemaManager().addMapping(updatedEventType);
      initializeSchema();

      // then the mapping contains the new fields
      assertThatNewFieldExists();
    } finally {
      embeddedOptimizeRule.getElasticSearchSchemaManager().getMappings().remove(updatedEventType);
    }
  }

  @Test
  public void dynamicSettingsAreUpdated() throws IOException {

    // given schema exists
    initializeSchema();

    // with a different dynamic setting than default
    final List<TypeMappingCreator> mappings = embeddedOptimizeRule.getElasticSearchSchemaManager().getMappings();
    modifyDynamicIndexSetting(mappings);

    // when
    initializeSchema();

    // then the settings contain the updated value
    final GetSettingsResponse getSettingsResponse = getIndexSettingsFor(mappings);

    assertDynamicSettingsComplyWithDefault(mappings, getSettingsResponse);
  }

  @Test
  public void dynamicSettingsAreUpdatedForExistingIndexesWhenNewIndexesAreCreated() throws IOException {

    // given schema exists
    initializeSchema();

    // with a different dynamic setting than default
    final List<TypeMappingCreator> mappings = embeddedOptimizeRule.getElasticSearchSchemaManager().getMappings();
    modifyDynamicIndexSetting(mappings);

    // one index is missing so recreating of indexes is triggered
    embeddedOptimizeRule.getOptimizeElasticClient().getHighLevelClient().indices().delete(
      new DeleteIndexRequest(indexNameService.getVersionedOptimizeIndexNameForTypeMapping(new DecisionInstanceType())),
      RequestOptions.DEFAULT
    );

    // when
    initializeSchema();

    // then the settings contain the updated value
    final GetSettingsResponse getSettingsResponse = getIndexSettingsFor(mappings);

    assertDynamicSettingsComplyWithDefault(mappings, getSettingsResponse);
  }

  @Test
  public void newIndexIsNotAddedDynamically() {
    // given schema is created
    initializeSchema();

    // then an exception is thrown
    thrown.expect(ElasticsearchStatusException.class);

    // when I add a document to an unknown type
    ProcessInstanceDto dto = new ProcessInstanceDto();
    elasticSearchRule.addEntryToElasticsearch("myAwesomeNewIndex", "12312412", dto);
  }

  @Test
  public void onlyAcceptDocumentsThatComplyWithTheSchema() {
    // given schema is created
    initializeSchema();

    // then
    thrown.expect(ElasticsearchStatusException.class);

    // when we add an event with an undefined type in schema
    ExtendedFlowNodeEventDto extendedEventDto = new ExtendedFlowNodeEventDto();
    elasticSearchRule.addEntryToElasticsearch(
      ElasticsearchConstants.METADATA_TYPE,
      "12312412",
      extendedEventDto
    );
  }

  private void assertDynamicSettingsComplyWithDefault(final List<TypeMappingCreator> mappings,
                                                      final GetSettingsResponse getSettingsResponse) throws
                                                                                                     IOException {
    final Settings settings = IndexSettingsBuilder.buildDynamicSettings(embeddedOptimizeRule.getConfigurationService());

    for (TypeMappingCreator mapping : mappings) {
      settings.names().forEach(settingName -> {
        final String ngramMaxValue = getSettingsResponse.getSetting(
          indexNameService.getVersionedOptimizeIndexNameForTypeMapping(mapping),
          "index." + settingName
        );
        assertThat(ngramMaxValue, is(settings.get(settingName)));
      });
    }
  }

  private void modifyDynamicIndexSetting(final List<TypeMappingCreator> mappings) throws IOException {
    for (TypeMappingCreator mapping : mappings) {
      final String indexName = indexNameService.getVersionedOptimizeIndexNameForTypeMapping(mapping);
      final UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest(indexName);
      updateSettingsRequest.settings(Settings.builder().put(DYNAMIC_SETTING_MAX_NGRAM_DIFF, "1").build());
      prefixAwareRestHighLevelClient.getHighLevelClient()
        .indices().putSettings(updateSettingsRequest, RequestOptions.DEFAULT);
    }
  }

  private GetSettingsResponse getIndexSettingsFor(final List<TypeMappingCreator> mappings) throws IOException {
    final String indices = mappings.stream()
      .map(indexNameService::getVersionedOptimizeIndexNameForTypeMapping)
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

  private void assertTypeExists(String type) throws IOException {
    final String optimizeIndexAliasForType = indexNameService.getOptimizeIndexAliasForType(type);

    RestClient esClient = elasticSearchRule.getOptimizeElasticClient().getLowLevelClient();
    Request request = new Request(HttpGet.METHOD_NAME, "/" + optimizeIndexAliasForType + "/_mapping");
    Response response = esClient.performRequest(request);

    String responseBody = EntityUtils.toString(response.getEntity());
    Map<String, Map<String, Map<String, Object>>> mappings = JsonPath.read(responseBody, "$");

    assertThat(mappings.size(), is(1));

    boolean containsType = mappings
      .values()
      .iterator()
      .next()
      .get("mappings")
      .keySet()
      .contains(type);
    assertThat(containsType, is(true));
  }

  private void assertThatNewFieldExists() throws IOException {
    final String metaDataType = ElasticsearchConstants.METADATA_TYPE;
    final String optimizeIndexAliasForType = indexNameService.getOptimizeIndexAliasForType(metaDataType);

    GetFieldMappingsRequest request = new GetFieldMappingsRequest().indices(optimizeIndexAliasForType)
      .indices(optimizeIndexAliasForType)
      .types(metaDataType)
      .fields(MyUpdatedEventType.MY_NEW_FIELD);
    GetFieldMappingsResponse response =
      prefixAwareRestHighLevelClient.getHighLevelClient().indices().getFieldMapping(request, RequestOptions.DEFAULT);

    final MyUpdatedEventType updatedEventType = new MyUpdatedEventType();
    final FieldMappingMetaData fieldEntry =
      response.fieldMappings(
        indexNameService.getVersionedOptimizeIndexNameForTypeMapping(updatedEventType),
        metaDataType,
        MyUpdatedEventType.MY_NEW_FIELD
      );

    assertThat(fieldEntry.isNull(), is(false));
  }

  private void initializeSchema() {
    embeddedOptimizeRule.getElasticSearchSchemaManager().initializeSchema(prefixAwareRestHighLevelClient);
  }
}
