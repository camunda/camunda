/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade;

import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.indices.RenameFieldTestIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateMappingIndexStep;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.GetMappingsResponse;
import org.elasticsearch.client.indices.IndexTemplatesExistRequest;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.xcontent.XContentType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.SuppressionConstants.SAME_PARAM_VALUE;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UpgradeStepsIT extends AbstractUpgradeIT {

  private static final String FROM_VERSION = "2.6.0";
  private static final String INTERMEDIATE_VERSION = "2.6.1";
  private static final String TO_VERSION = "2.7.0";

  @Test
  public void executeCreateIndexWithAliasStep() throws Exception {
    // given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new CreateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING_V2))
        .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final String versionedIndexName = indexNameService
      .getOptimizeIndexNameWithVersionForAllIndicesOf(TEST_INDEX_WITH_UPDATED_MAPPING_V2);
    assertThat(
      prefixAwareClient.getHighLevelClient().indices().exists(
        new GetIndexRequest(versionedIndexName).features(GetIndexRequest.Feature.MAPPINGS),
        prefixAwareClient.requestOptions()
      )
    ).isTrue();
    final GetAliasesResponse alias = getAliasesForAlias(
      indexNameService.getOptimizeIndexAliasForIndex(TEST_INDEX_WITH_UPDATED_MAPPING_V2.getIndexName()));
    assertThatIndexIsSetAsWriteIndex(versionedIndexName, alias);
  }

  @Test
  public void executeCreateTemplateBasedIndexWithAliasStep() throws Exception {
    // given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new CreateIndexStep(TEST_INDEX_WITH_TEMPLATE_V1))
        .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final String versionedIndexName = indexNameService
      .getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_TEMPLATE_V1);
    assertThat(
      prefixAwareClient.getHighLevelClient().indices().exists(
        new GetIndexRequest(versionedIndexName).features(GetIndexRequest.Feature.MAPPINGS),
        prefixAwareClient.requestOptions()
      )
    ).isTrue();
    final GetAliasesResponse alias = getAliasesForAlias(
      indexNameService.getOptimizeIndexAliasForIndex(TEST_INDEX_WITH_TEMPLATE_V1.getIndexName()));
    assertThatIndexIsSetAsWriteIndex(versionedIndexName, alias);
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexStep() {
    // given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new CreateIndexStep(TEST_INDEX_V1))
        .addUpgradeStep(new UpdateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING_V2))
        .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(prefixAwareClient.exists(TEST_INDEX_WITH_UPDATED_MAPPING_V2)).isTrue();
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexStep_preexistingIndexWithoutAliasWriteIndexFlag() {
    // given
    final String aliasForIndex = indexNameService.getOptimizeIndexAliasForIndex(TEST_INDEX_V1.getIndexName());
    createIndexWithoutWriteIndexFlagOnAlias(aliasForIndex);

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new UpdateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING_V2))
        .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(prefixAwareClient.exists(TEST_INDEX_WITH_UPDATED_MAPPING_V2)).isTrue();
    // even though not being set before the writeIndex flag is now set
    final GetAliasesResponse alias = getAliasesForAlias(aliasForIndex);
    assertThatIndexIsSetAsWriteIndex(
      indexNameService.getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_UPDATED_MAPPING_V2),
      alias
    );
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexWithAliasFromTemplateStep() {
    // given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new CreateIndexStep(TEST_INDEX_WITH_TEMPLATE_V1))
        .addUpgradeStep(new UpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2))
        .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(prefixAwareClient.exists(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2)).isTrue();

    final Map<String, Object> mappingFields = getMappingFields();
    assertThat(mappingFields).containsKey("email");
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexFromTemplateStep_preexistingIndexWasNotFromTemplateAndLackedAliasWriteIndexFlag() {
    // given
    final String aliasForIndex = indexNameService.getOptimizeIndexAliasForIndex(TEST_INDEX_V1.getIndexName());
    createIndexWithoutWriteIndexFlagOnAlias(aliasForIndex);

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new UpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2))
        .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(prefixAwareClient.exists(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2)).isTrue();

    final Map<String, Object> mappingFields = getMappingFields();
    assertThat(mappingFields).containsKey("email");

    // even though not being set before the writeIndex flag is now set
    assertThatIndexIsSetAsWriteIndex(
      indexNameService.getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2),
      getAliasesForAlias(aliasForIndex)
    );
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexFromTemplateStep_preexistingIndexWasNotFromTemplateAndHadWriteAndReadAlias() {
    // given
    final String aliasForIndex = indexNameService.getOptimizeIndexAliasForIndex(TEST_INDEX_V1.getIndexName());
    final String readOnlyAliasForIndex = indexNameService.getOptimizeIndexAliasForIndex("im-read-only");

    final CreateIndexRequest request = new CreateIndexRequest(
      indexNameService.getOptimizeIndexNameWithVersion(TEST_INDEX_V1)
    );
    request.alias(new Alias(aliasForIndex).writeIndex(true));
    request.alias(new Alias(readOnlyAliasForIndex).writeIndex(false));
    request.mapping(TEST_INDEX_V1.getSource());
    prefixAwareClient.getHighLevelClient().indices().create(request, prefixAwareClient.requestOptions());

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new UpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2))
        .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(prefixAwareClient.exists(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2)).isTrue();

    final String versionedIndexName = indexNameService
      .getOptimizeIndexNameWithVersion(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2);
    assertThatIndexIsSetAsWriteIndex(versionedIndexName, getAliasesForAlias(aliasForIndex));

    assertThat(getAliasesForAlias(readOnlyAliasForIndex).getAliases())
      .hasSize(1)
      .extractingByKey(versionedIndexName)
      .satisfies(aliasMetaData -> assertThat(aliasMetaData)
        .hasSize(1)
        .extracting(AliasMetadata::writeIndex)
        .containsExactly(false)
      );
  }

  @SneakyThrows
  @Test
  public void executeUpdateIndexWithTemplateAfterRolloverStep() {
    // given rolled over users index
    UpgradePlan buildIndexPlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(INTERMEDIATE_VERSION)
        .addUpgradeStep(new CreateIndexStep(TEST_INDEX_WITH_TEMPLATE_V1))
        .build();

    upgradeProcedure.performUpgrade(buildIndexPlan);

    ElasticsearchWriterUtil.triggerRollover(prefixAwareClient, TEST_INDEX_WITH_TEMPLATE_V1.getIndexName(), 0);

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(INTERMEDIATE_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new UpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2))
        .build();

    // when update index after rollover
    upgradeProcedure.performUpgrade(upgradePlan);

    // then optimize-users write alias points to updated users index
    final String expectedSuffixAfterRollover = "-000002";
    final String indexAlias =
      indexNameService.getOptimizeIndexAliasForIndex(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName());
    final Map<String, Set<AliasMetadata>> aliasMap = getAliasMap(indexAlias);
    final List<String> indicesWithWriteAlias = aliasMap.entrySet()
      .stream()
      .filter(e -> e.getValue().removeIf(AliasMetadata::writeIndex))
      .map(Map.Entry::getKey)
      .collect(toList());
    final Map<String, Object> mappingFields = getMappingFields();
    assertThat(mappingFields).containsKey("email");
    assertThat(aliasMap.keySet()).hasSize(2);
    assertThat(indicesWithWriteAlias).hasSize(1);
    assertThat(indicesWithWriteAlias.get(0)).contains(expectedSuffixAfterRollover);
    // old template is gone
    assertThat(
      prefixAwareClient.getHighLevelClient().indices().existsTemplate(new IndexTemplatesExistRequest(
        indexNameService.getOptimizeIndexTemplateNameWithVersion(TEST_INDEX_WITH_TEMPLATE_V1)
      ), prefixAwareClient.requestOptions())
    ).isFalse();
  }

  @Test
  public void executeInsertDataStep() throws Exception {
    // given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new CreateIndexStep(TEST_INDEX_V2))
        .addUpgradeStep(buildInsertTestIndexDataStep(UpgradeStepsIT.TEST_INDEX_V1))
        .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final SearchResponse searchResponse = prefixAwareClient.search(new SearchRequest(TEST_INDEX_V2.getIndexName()));

    assertThat(searchResponse.getHits())
      .hasSize(1)
      .extracting(SearchHit::getSourceAsMap)
      .extracting("username", "password")
      .containsExactly(new Tuple("admin", "admin"));
  }

  @Test
  public void executeUpdateDataStep() throws Exception {
    // given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new CreateIndexStep(TEST_INDEX_V2))
        .addUpgradeStep(buildInsertTestIndexDataStep(UpgradeStepsIT.TEST_INDEX_V1))
        .addUpgradeStep(buildUpdateTestIndexDataStep(UpgradeStepsIT.TEST_INDEX_V1))
        .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(TEST_INDEX_V2.getIndexName()));
    assertThat(searchResponse.getHits())
      .hasSize(1)
      .extracting(SearchHit::getSourceAsMap)
      .extracting("username", "password")
      .containsExactly(new Tuple("admin", "admin1"));
  }

  @Test
  public void executeDeleteDataStep() throws Exception {
    // given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new CreateIndexStep(TEST_INDEX_V2))
        .addUpgradeStep(buildInsertTestIndexDataStep(UpgradeStepsIT.TEST_INDEX_V1))
        .addUpgradeStep(buildDeleteTestIndexDataStep(UpgradeStepsIT.TEST_INDEX_V1))
        .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(TEST_INDEX_V2.getIndexName()));
    assertThat(searchResponse.getHits().getHits()).isEmpty();
  }

  @Test
  public void executeDeleteIndexStep() throws Exception {
    // given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new CreateIndexStep(TEST_INDEX_V2))
        .addUpgradeStep(buildDeleteIndexStep(TEST_INDEX_V2))
        .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(prefixAwareClient.exists(TEST_INDEX_V2)).isFalse();
  }

  @Test
  public void executeDeleteIndexStep_rolledOverIndex() throws Exception {
    // given rolled over users index
    UpgradePlan buildIndexPlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(INTERMEDIATE_VERSION)
        .addUpgradeStep(new CreateIndexStep(TEST_INDEX_WITH_TEMPLATE_V1))
        .build();

    upgradeProcedure.performUpgrade(buildIndexPlan);

    ElasticsearchWriterUtil.triggerRollover(prefixAwareClient, TEST_INDEX_WITH_TEMPLATE_V1.getIndexName(), 0);

    // then two indices exist after the rollover
    boolean indicesExist = prefixAwareClient.exists(TEST_INDEX_WITH_TEMPLATE_V1);
    assertThat(indicesExist).isTrue();
    final GetIndexResponse response = getIndicesForMapping(TEST_INDEX_WITH_TEMPLATE_V1);
    assertThat(response.getIndices()).hasSize(2);

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(INTERMEDIATE_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildDeleteIndexStep(TEST_INDEX_WITH_TEMPLATE_V1))
        .build();

    // when update index after rollover
    upgradeProcedure.performUpgrade(upgradePlan);

    // then both the initial and rolled over index are deleted
    indicesExist = prefixAwareClient.exists(TEST_INDEX_WITH_TEMPLATE_V1);
    assertThat(indicesExist).isFalse();
  }

  @Test
  public void executeUpgradeMappingIndexStep() throws Exception {
    // given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new CreateIndexStep(TEST_INDEX_V2))
        .addUpgradeStep(new UpdateMappingIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING_V2))
        .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final Map<String, Object> mappingFields = getMappingFields();
    assertThat(mappingFields).containsKey("email");
  }

  @Test
  public void fieldRenameWithoutRemovingOldFieldAbortsUpgrade() throws IOException {
    // given
    createOptimizeIndexWithTypeAndVersion(new RenameFieldTestIndex(), 1);

    IndexRequest indexRequest = new IndexRequest("users")
      .source("{\"name\": \"yuri_loza\"}", XContentType.JSON);

    prefixAwareClient.index(indexRequest);

    prefixAwareClient.refresh(new RefreshRequest("*"));

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new UpdateIndexStep(TEST_INDEX_WITH_UPDATED_MAPPING_V2, "def foo = \"noop\";"))
        .build();

    // when
    assertThrows(UpgradeRuntimeException.class, () -> upgradeProcedure.performUpgrade(upgradePlan));
  }

  @Test
  public void versionIsUpdatedAfterPlanWasExecuted() throws Exception {
    // given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new CreateIndexStep(TEST_INDEX_V2))
        .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(METADATA_INDEX.getIndexName()));
    assertThat(searchResponse.getHits())
      .hasSize(1)
      .extracting(SearchHit::getSourceAsMap)
      .extracting(MetadataIndex.SCHEMA_VERSION)
      .containsExactly(TO_VERSION);
  }

  private GetAliasesResponse getAliasesForAlias(final String readOnlyAliasForIndex) throws IOException {
    return prefixAwareClient.getAlias(new GetAliasesRequest(readOnlyAliasForIndex));
  }

  private void assertThatIndexIsSetAsWriteIndex(final String versionedIndexName, final GetAliasesResponse alias) {
    assertThat(alias.getAliases())
      .hasSize(1)
      .extractingByKey(versionedIndexName)
      .satisfies(aliasMetaData -> assertThat(aliasMetaData)
        .hasSize(1)
        .extracting(AliasMetadata::writeIndex)
        .containsExactly(true)
      );
  }

  private void createIndexWithoutWriteIndexFlagOnAlias(final String aliasForIndex) throws IOException {
    final CreateIndexRequest request = new CreateIndexRequest(
      indexNameService.getOptimizeIndexNameWithVersion(TEST_INDEX_V1)
    );
    request.alias(new Alias(aliasForIndex));
    request.mapping(TEST_INDEX_V1.getSource());
    prefixAwareClient.getHighLevelClient().indices().create(request, prefixAwareClient.requestOptions());
  }

  @SuppressWarnings(UNCHECKED_CAST)
  private Map<String, Object> getMappingFields() throws IOException {
    GetMappingsRequest request = new GetMappingsRequest();
    request.indices(TEST_INDEX_WITH_UPDATED_MAPPING_V2.getIndexName());
    GetMappingsResponse getMappingResponse = prefixAwareClient.getMapping(request);
    final Object propertiesMap = getMappingResponse.mappings()
      .values()
      .stream()
      .findFirst()
      .orElseThrow(() -> new OptimizeRuntimeException("There should be at least one mapping available for the index!"))
      .getSourceAsMap()
      .get("properties");
    if (propertiesMap instanceof Map) {
      return (Map<String, Object>) propertiesMap;
    } else {
      throw new OptimizeRuntimeException("ElasticSearch index mapping properties should be of type map");
    }
  }

  @SuppressWarnings(SAME_PARAM_VALUE)
  private DeleteIndexIfExistsStep buildDeleteIndexStep(final IndexMappingCreator indexMapping) {
    return new DeleteIndexIfExistsStep(indexMapping);
  }

  private Map<String, Set<AliasMetadata>> getAliasMap(final String aliasName) {
    GetAliasesRequest aliasesRequest = new GetAliasesRequest().aliases(aliasName);
    try {
      return prefixAwareClient.getAlias(aliasesRequest).getAliases();
    } catch (IOException e) {
      String message = String.format("Could not retrieve alias map for alias {%s}.", aliasName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

}
