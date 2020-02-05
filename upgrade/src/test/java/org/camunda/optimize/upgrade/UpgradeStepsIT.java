/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.service.es.reader.ElasticsearchHelper;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.indexes.RenameFieldTestIndex;
import org.camunda.optimize.upgrade.indexes.UserTestIndex;
import org.camunda.optimize.upgrade.indexes.UserTestUpdatedMappingIndex;
import org.camunda.optimize.upgrade.indexes.UserTestWithTemplateIndex;
import org.camunda.optimize.upgrade.indexes.UserTestWithTemplateUpdatedMappingIndex;
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
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.GetMappingsResponse;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UpgradeStepsIT extends AbstractUpgradeIT {

  private static final IndexMappingCreator TEST_INDEX_V1 = new UserTestIndex(1);
  private static final IndexMappingCreator TEST_INDEX_V2 = new UserTestIndex(2);
  private static final IndexMappingCreator TEST_INDEX_WITH_UPDATED_MAPPING = new UserTestUpdatedMappingIndex();
  private static final IndexMappingCreator TEST_INDEX_WITH_TEMPLATE = new UserTestWithTemplateIndex();
  private static final IndexMappingCreator TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING =
    new UserTestWithTemplateUpdatedMappingIndex();

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
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING))
        .build();

    // when
    upgradePlan.execute();

    // then
    final String versionedIndexName = getTestIndexName(TEST_INDEX_WITH_UPDATED_MAPPING);
    assertThat(
      prefixAwareClient.getHighLevelClient().indices().exists(
        new GetIndexRequest(versionedIndexName).features(GetIndexRequest.Feature.MAPPINGS),
        RequestOptions.DEFAULT
      ),
      is(true)
    );
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexStep() {
    // given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V1))
        .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING))
        .build();

    // when
    upgradePlan.execute();

    // then
    final String versionedIndexName = getTestIndexName(TEST_INDEX_WITH_UPDATED_MAPPING);
    assertThat(
      prefixAwareClient.getHighLevelClient().indices().exists(
        new GetIndexRequest(versionedIndexName),
        RequestOptions.DEFAULT
      ),
      is(true)
    );
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexWithAliasFromTemplateStep() {
    // given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_WITH_TEMPLATE))
        .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING))
        .build();

    // when
    upgradePlan.execute();

    // then
    final String versionedIndexName = indexNameService.getOptimizeIndexAliasForIndex(
      TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING.getIndexName());
    assertThat(
      prefixAwareClient.getHighLevelClient().indices().exists(
        new GetIndexRequest(versionedIndexName),
        RequestOptions.DEFAULT
      ),
      is(true)
    );

    final Map<?, ?> mappingFields = getMappingFields();
    assertThat(mappingFields.containsKey("email"), is(true));
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexWithTemplateAfterRolloverStep() {
    // given rolled over users index
    UpgradePlan buildIndexPlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_WITH_TEMPLATE))
        .build();

    buildIndexPlan.execute();

    ElasticsearchHelper.triggerRollover(
      prefixAwareClient,
      TEST_INDEX_WITH_TEMPLATE.getIndexName(),
      0
    );

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING))
        .build();

    // when update index after rollover
    upgradePlan.execute();

    // then optimize-users write alias points to updated users index
    final String expectedSuffixAfterRollover = "-000002";
    final String indexAlias =
      indexNameService.getOptimizeIndexAliasForIndex(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING.getIndexName());
    final Map<String, Set<AliasMetaData>> aliasMap = getAliasMap(indexAlias);
    final List<String> indicesWithWriteAlias = aliasMap.entrySet()
      .stream()
      .filter(e -> e.getValue().removeIf(AliasMetaData::writeIndex))
      .map(e -> e.getKey())
      .collect(toList());
    Map<?, ?> mappingFields = getMappingFields();


    assertThat(mappingFields.containsKey("email"), is(true));
    assertThat(aliasMap.keySet().size()).isEqualTo(2);
    assertThat(indicesWithWriteAlias.size()).isEqualTo(1);
    assertThat(indicesWithWriteAlias.get(0)).contains(expectedSuffixAfterRollover);
  }

  @Test
  public void executeInsertDataStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V2))
        .addUpgradeStep(buildInsertDataStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(TEST_INDEX_V2.getIndexName()),
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
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V2))
        .addUpgradeStep(buildInsertDataStep())
        .addUpgradeStep(buildUpdateDataStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(TEST_INDEX_V2.getIndexName()),
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
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V2))
        .addUpgradeStep(buildInsertDataStep())
        .addUpgradeStep(buildDeleteDataStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(TEST_INDEX_V2.getIndexName()),
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
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V2))
        .addUpgradeStep(buildDeleteIndexStep(TEST_INDEX_V2))
        .build();

    // when
    upgradePlan.execute();

    // then
    assertThat(
      prefixAwareClient.exists(new GetIndexRequest(getTestIndexName(TEST_INDEX_V2)), RequestOptions.DEFAULT),
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
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V2))
        .addUpgradeStep(new UpdateMappingIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING))
        .build();

    // when
    upgradePlan.execute();

    // then
    Map<?, ?> mappingFields = getMappingFields();
    assertThat(mappingFields.containsKey("email"), is(true));
  }

  @Test
  public void fieldRenameWithoutRemovingOldFieldAbortsUpgrade() throws IOException {
    //given
    createOptimizeIndexWithTypeAndVersion(new RenameFieldTestIndex(), 1);

    IndexRequest indexRequest = new IndexRequest("users")
      .source("{\"name\": \"yuri_loza\"}", XContentType.JSON);

    prefixAwareClient.index(indexRequest, RequestOptions.DEFAULT);

    RefreshRequest refreshRequest = new RefreshRequest("*");
    prefixAwareClient.getHighLevelClient().indices().refresh(refreshRequest, RequestOptions.DEFAULT);

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new UpdateIndexStep(
                          TEST_INDEX_WITH_UPDATED_MAPPING,
                          "def foo = \"noop\";"
                        )
        )
        .build();

    // when
    assertThrows(UpgradeRuntimeException.class, upgradePlan::execute);
  }

  @Test
  public void versionIsUpdatedAfterPlanWasExecuted() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .addUpgradeDependencies(upgradeDependencies)
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V2))
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

  private Map<?, ?> getMappingFields() throws IOException {
    GetMappingsRequest request = new GetMappingsRequest();
    request.indices(TEST_INDEX_WITH_UPDATED_MAPPING.getIndexName());
    GetMappingsResponse getMappingResponse = prefixAwareClient.getMapping(request, RequestOptions.DEFAULT);
    final Object propertiesMap = getMappingResponse.mappings()
      .values()
      .stream()
      .findFirst()
      .orElseThrow(() -> new OptimizeRuntimeException("There should be at least one mapping available for the index!"))
      .getSourceAsMap()
      .get("properties");
    if (propertiesMap instanceof Map) {
      return (Map<?, ?>) propertiesMap;
    } else {
      throw new OptimizeRuntimeException("ElasticSearch index mapping properties should be of type map");
    }
  }

  private InsertDataStep buildInsertDataStep() {
    return new InsertDataStep(
      TEST_INDEX_V2,
      UpgradeUtil.readClasspathFileAsString("steps/insert_data/test_data.json")
    );
  }

  private CreateIndexStep buildCreateIndexStep(final IndexMappingCreator index) {
    return new CreateIndexStep(index);
  }

  private UpdateIndexStep buildUpdateIndexStep(final IndexMappingCreator index) {
    return new UpdateIndexStep(index, null);
  }

  private String getTestIndexName(final IndexMappingCreator index) {
    return OptimizeIndexNameService.getOptimizeIndexNameForAliasAndVersion(
      indexNameService.getOptimizeIndexAliasForIndex(index.getIndexName()),
      String.valueOf(index.getVersion())
    );
  }

  private UpdateDataStep buildUpdateDataStep() {
    return new UpdateDataStep(
      TEST_INDEX_V2.getIndexName(),
      termQuery("username", "admin"),
      "ctx._source.password = ctx._source.password + \"1\""
    );
  }

  private UpgradeStep buildDeleteDataStep() {
    return new DeleteDataStep(
      TEST_INDEX_V2.getIndexName(),
      QueryBuilders.termQuery("username", "admin")
    );
  }

  private DeleteIndexStep buildDeleteIndexStep(final IndexMappingCreator indexMapping) {
    return new DeleteIndexStep(indexMapping);
  }

  private Map<String, Set<AliasMetaData>> getAliasMap(final String aliasName) {
    GetAliasesRequest aliasesRequest = new GetAliasesRequest().aliases(aliasName);
    try {
      return prefixAwareClient
        .getHighLevelClient()
        .indices()
        .getAlias(aliasesRequest, RequestOptions.DEFAULT)
        .getAliases();
    } catch (IOException e) {
      String message = String.format("Could not retrieve alias map for alias {}.", aliasName);
      throw new OptimizeRuntimeException(message, e);
    }
  }
}
