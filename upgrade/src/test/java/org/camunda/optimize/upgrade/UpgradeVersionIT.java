/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import org.camunda.optimize.service.es.schema.type.MetadataType;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.document.InsertDataStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexAliasForExistingIndexStep;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexStep;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.Before;
import org.junit.Test;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexNameForAliasAndVersion;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class UpgradeVersionIT extends AbstractUpgradeIT {

  private static final String TEST_TYPE = "users";
  private static final String TEST_INDEX = "optimize-users";

  private static final String FROM_VERSION = "2.0.0";
  private static final String TO_VERSION = "2.1.0";

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(METADATA_TYPE));
    setMetadataIndexVersion(FROM_VERSION);
  }

  @Test
  public void executeCreateIndexWithAliasStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexWithAliasStep(TEST_TYPE))
        .build();

    // when
    upgradePlan.execute();

    // then
    assertThat(
      restClient.indices().exists(
        new GetIndexRequest().indices(TEST_INDEX).features(GetIndexRequest.Feature.ALIASES),
        RequestOptions.DEFAULT
      ),
      is(true)
    );
    final String versionedIndexName = getOptimizeIndexNameForAliasAndVersion(TEST_INDEX, TO_VERSION);
    assertThat(
      restClient.indices().exists(
        new GetIndexRequest().indices(versionedIndexName).features(GetIndexRequest.Feature.MAPPINGS),
        RequestOptions.DEFAULT
      ),
      is(true)
    );
  }

  @Test
  public void executeCreateIndexWithoutAliasStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexWithoutAliasStep(TEST_TYPE))
        .build();

    // when
    upgradePlan.execute();

    // then
    assertThat(
      restClient.indices().exists(
        new GetIndexRequest().indices(TEST_INDEX).features(GetIndexRequest.Feature.MAPPINGS),
        RequestOptions.DEFAULT
      ),
      is(true)
    );
  }

  @Test
  public void executeCreateIndexAliasForExistingIndexStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexWithoutAliasStep(TEST_TYPE))
        .addUpgradeStep(new CreateIndexAliasForExistingIndexStep(TEST_TYPE, TO_VERSION))
        .build();

    // when
    upgradePlan.execute();

    // then
    assertThat(
      restClient.indices().exists(
        new GetIndexRequest().indices(TEST_INDEX).features(GetIndexRequest.Feature.ALIASES),
        RequestOptions.DEFAULT
      ),
      is(true)
    );
    final String versionedIndexName = getOptimizeIndexNameForAliasAndVersion(TEST_INDEX, TO_VERSION);
    assertThat(
      restClient.indices().exists(
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
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexWithAliasStep(TEST_TYPE))
        .addUpgradeStep(buildInsertDataStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse searchResponse = restClient.search(new SearchRequest(TEST_INDEX), RequestOptions.DEFAULT);
    assertThat(searchResponse.getHits().getHits().length, is(1));
    assertThat(searchResponse.getHits().getHits()[0].getSourceAsMap().get("username"), is("admin"));
    assertThat(searchResponse.getHits().getHits()[0].getSourceAsMap().get("password"), is("admin"));
  }

  @Test
  public void executeUpdateDataStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexWithAliasStep(TEST_TYPE))
        .addUpgradeStep(buildInsertDataStep())
        .addUpgradeStep(buildUpdateDataStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse searchResponse = restClient.search(new SearchRequest(TEST_INDEX), RequestOptions.DEFAULT);
    assertThat(searchResponse.getHits().getHits().length, is(1));
    assertThat(searchResponse.getHits().getHits()[0].getSourceAsMap().get("username"), is("admin"));
    assertThat(searchResponse.getHits().getHits()[0].getSourceAsMap().get("password"), is("admin1"));
  }

  @Test
  public void executeDeleteIndexStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexWithAliasStep(TEST_TYPE))
        .addUpgradeStep(buildDeleteIndexStep(TEST_TYPE))
        .build();

    // when
    upgradePlan.execute();

    // then
    assertThat(
      restClient.indices().exists(new GetIndexRequest().indices(TEST_INDEX), RequestOptions.DEFAULT),
      is(false)
    );
  }

  @Test
  public void versionIsUpdatedAfterPlanWasExecuted() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexWithAliasStep(TEST_TYPE))
        .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse searchResponse = restClient.search(
      new SearchRequest(getOptimizeIndexAliasForType(METADATA_TYPE.getType())),
      RequestOptions.DEFAULT
    );
    assertThat(searchResponse.getHits().getHits().length, is(1));
    assertThat(
      searchResponse.getHits().getHits()[0].getSourceAsMap().get(MetadataType.SCHEMA_VERSION),
      is(TO_VERSION)
    );
  }

  private InsertDataStep buildInsertDataStep() {
    return new InsertDataStep(
      TEST_TYPE,
      SchemaUpgradeUtil.readClasspathFileAsString("steps/insert_data/test_data.json")
    );
  }

  private CreateIndexStep buildCreateIndexWithoutAliasStep(String indexName) {
    return new CreateIndexStep(
      null,
      indexName,
      SchemaUpgradeUtil.readClasspathFileAsString("steps/create_index/new_index_mapping.json")
    );
  }

  private CreateIndexStep buildCreateIndexWithAliasStep(String indexName) {
    return new CreateIndexStep(
      TO_VERSION,
      indexName,
      SchemaUpgradeUtil.readClasspathFileAsString("steps/create_index/new_index_mapping.json")
    );
  }

  private UpdateDataStep buildUpdateDataStep() {
    return new UpdateDataStep(
      TEST_TYPE,
      termQuery("username", "admin"),
      "ctx._source.password = ctx._source.password + \"1\""
    );
  }

  private DeleteIndexStep buildDeleteIndexStep(String indexName) {
    return new DeleteIndexStep(TO_VERSION, indexName);
  }

}
