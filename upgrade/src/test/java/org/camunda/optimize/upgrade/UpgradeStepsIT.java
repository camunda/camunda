/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import com.jayway.jsonpath.JsonPath;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.upgrade.indexes.UserTestIndex;
import org.camunda.optimize.upgrade.indexes.UserTestUpdatedMappingIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.DeleteDataStep;
import org.camunda.optimize.upgrade.steps.document.InsertDataStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateMappingIndexStep;
import org.camunda.optimize.upgrade.util.UpgradeUtil;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DEFAULT_INDEX_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class UpgradeStepsIT extends AbstractUpgradeIT {

  private static final IndexMappingCreator TEST_INDEX = new UserTestIndex();
  private static final IndexMappingCreator TEST_INDEX_WITH_UPDATED_MAPPING = new UserTestUpdatedMappingIndex();

  private static final String FROM_VERSION = "2.6.0";
  private static final String TO_VERSION = "2.7.0";

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(METADATA_INDEX));
    setMetadataIndexVersion(FROM_VERSION);
  }

  @Test
  public void executeCreateIndexWithAliasStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .build();

    // when
    upgradePlan.execute();

    // then
    final String versionedIndexName = getTestIndexName(TEST_INDEX);
    assertThat(
      prefixAwareClient.getHighLevelClient().indices().exists(
        new GetIndexRequest().indices(versionedIndexName).features(GetIndexRequest.Feature.MAPPINGS),
        RequestOptions.DEFAULT
      ),
      is(true)
    );
  }

  @Test
  public void executeInsertDataStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .addUpgradeStep(buildInsertDataStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(TEST_INDEX.getIndexName()),
      RequestOptions.DEFAULT
    );
    assertThat(searchResponse.getHits().getHits().length, is(1));
    assertThat(searchResponse.getHits().getHits()[0].getSourceAsMap().get("username"), is("admin"));
    assertThat(searchResponse.getHits().getHits()[0].getSourceAsMap().get("password"), is("admin"));
  }

  @Test
  public void executeUpdateDataStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .addUpgradeStep(buildInsertDataStep())
        .addUpgradeStep(buildUpdateDataStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(TEST_INDEX.getIndexName()),
      RequestOptions.DEFAULT
    );
    assertThat(searchResponse.getHits().getHits().length, is(1));
    assertThat(searchResponse.getHits().getHits()[0].getSourceAsMap().get("username"), is("admin"));
    assertThat(searchResponse.getHits().getHits()[0].getSourceAsMap().get("password"), is("admin1"));
  }

  @Test
  public void executeDeleteDataStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .addUpgradeStep(buildInsertDataStep())
        .addUpgradeStep(buildDeleteDataStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(TEST_INDEX.getIndexName()),
      RequestOptions.DEFAULT
    );
    assertThat(searchResponse.getHits().getHits().length, is(0));
  }

  @Test
  public void executeDeleteIndexStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .addUpgradeStep(buildDeleteIndexStep(TEST_INDEX))
        .build();

    // when
    upgradePlan.execute();

    // then
    assertThat(
      prefixAwareClient.exists(new GetIndexRequest().indices(getTestIndexName(TEST_INDEX)), RequestOptions.DEFAULT),
      is(false)
    );
  }

  @Test
  public void executeUpgradeMappingIndexStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .addUpgradeStep(new UpdateMappingIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING))
        .build();

    // when
    upgradePlan.execute();

    // then
    Map mappingFields = getMappingFields();
    assertThat(mappingFields.containsKey("email"), is(true));
  }

  private Map getMappingFields() throws IOException {
    // we need to perform this request manually since Elasticsearch 6.5 automatically
    // adds "master_timeout" parameter to the get mappings request which is not
    // recognized prior to 6.4 and throws an error. As soon as we don't support 6.3 or
    // older those lines can be replaced with the high rest client equivalent.
    Request request = new Request("GET", "/" + getTestIndexName(TEST_INDEX_WITH_UPDATED_MAPPING) + "/_mappings");
    Response response = prefixAwareClient.getLowLevelClient().performRequest(request);
    String responseBody = EntityUtils.toString(response.getEntity());
    String jsonPathToNewField = String.format(
      "$.%s.mappings.%s.properties",
      getTestIndexName(TEST_INDEX_WITH_UPDATED_MAPPING),
      DEFAULT_INDEX_TYPE
    );
    return JsonPath.parse(responseBody).read(jsonPathToNewField);
  }

  @Test
  public void versionIsUpdatedAfterPlanWasExecuted() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(METADATA_INDEX.getIndexName()),
      RequestOptions.DEFAULT
    );
    assertThat(searchResponse.getHits().getHits().length, is(1));
    assertThat(
      searchResponse.getHits().getHits()[0].getSourceAsMap().get(MetadataIndex.SCHEMA_VERSION),
      is(TO_VERSION)
    );
  }

  @ParameterizedTest
  @MethodSource("getIndexMapperAndType")
  public void indexTypeIsUpdatedToOrRetainsDefaultValue(IndexMappingCreator indexMapper, String type) throws
                                                                                                      IOException {
    //given index exists with previous version
    createAndPopulateOptimizeIndexWithTypeAndVersion(indexMapper, type, indexMapper.getVersion() - 1);

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildUpdateIndexStep(indexMapper))
        .build();

    // when
    upgradePlan.execute();

    // then the type and index name are as expected and the data is reindexed
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(indexMapper.getIndexName()),
      RequestOptions.DEFAULT
    );
    assertThat(searchResponse.getHits().getHits().length, is(1));
    assertThat(searchResponse.getHits().getHits()[0].getType(), is(DEFAULT_INDEX_TYPE));
  }

  private InsertDataStep buildInsertDataStep() {
    return new InsertDataStep(
      TEST_INDEX,
      UpgradeUtil.readClasspathFileAsString("steps/insert_data/test_data.json")
    );
  }

  private UpdateIndexStep buildUpdateIndexStep(IndexMappingCreator indexMapping) {
    return new UpdateIndexStep(
      indexMapping,
      null
    );
  }

  private CreateIndexStep buildCreateIndexStep(final IndexMappingCreator index) {
    return new CreateIndexStep(index);
  }

  private String getTestIndexName(final IndexMappingCreator index) {
    return indexNameService.getOptimizeIndexNameForAliasAndVersion(
      indexNameService.getOptimizeIndexAliasForIndex(index.getIndexName()),
      String.valueOf(index.getVersion())
    );
  }

  private UpdateDataStep buildUpdateDataStep() {
    return new UpdateDataStep(
      TEST_INDEX.getIndexName(),
      termQuery("username", "admin"),
      "ctx._source.password = ctx._source.password + \"1\""
    );
  }

  private UpgradeStep buildDeleteDataStep() {
    return new DeleteDataStep(
      TEST_INDEX.getIndexName(),
      QueryBuilders.termQuery("username", "admin")
    );
  }

  private DeleteIndexStep buildDeleteIndexStep(final IndexMappingCreator indexMapping) {
    return new DeleteIndexStep(indexMapping);
  }

  private void createAndPopulateOptimizeIndexWithTypeAndVersion(IndexMappingCreator indexMapping,
                                                                String type,
                                                                int version) throws IOException {
    final String aliasName = indexNameService.getOptimizeIndexAliasForIndex(indexMapping.getIndexName());
    final String indexName =
      indexNameService.getOptimizeIndexNameForAliasAndVersion(indexNameService.getOptimizeIndexAliasForIndex(
        indexMapping.getIndexName()), String.valueOf(version));
    final Settings indexSettings = createIndexSettings();

    CreateIndexRequest request = new CreateIndexRequest(indexName);
    request.alias(new Alias(aliasName));
    request.settings(indexSettings);
    request.mapping(type, indexMapping.getSource());
    prefixAwareClient.getHighLevelClient().indices().create(request, RequestOptions.DEFAULT);

    final IndexRequest indexRequest = new IndexRequest(indexName, type);
    indexRequest.source(UpgradeUtil.readClasspathFileAsString("steps/insert_data/test_data.json"), XContentType.JSON);
    indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
    prefixAwareClient.getHighLevelClient().index(indexRequest, RequestOptions.DEFAULT);
  }

  private Settings createIndexSettings() {
    try {
      return IndexSettingsBuilder.buildAllSettings(configurationService);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not create index settings");
    }
  }

  private static Stream<Arguments> getIndexMapperAndType() {
    return Stream.of(
      Arguments.of(TEST_INDEX, TEST_INDEX.getIndexName()),
      Arguments.of(TEST_INDEX, DEFAULT_INDEX_TYPE)
    );
  }
}
