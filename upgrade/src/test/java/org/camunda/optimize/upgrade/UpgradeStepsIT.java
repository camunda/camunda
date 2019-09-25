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
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.upgrade.indexes.UserTestIndex;
import org.camunda.optimize.upgrade.indexes.UserTestUpdatedMappingIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.document.InsertDataStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateMappingIndexStep;
import org.camunda.optimize.upgrade.util.UpgradeUtil;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class UpgradeStepsIT extends AbstractUpgradeIT {

  private static final IndexMappingCreator TEST_INDEX = new UserTestIndex();
  private static final IndexMappingCreator TEST_INDEX_WITH_UPDATED_MAPPING = new UserTestUpdatedMappingIndex();

  private static final String FROM_VERSION = "2.0.0";
  private static final String TO_VERSION = "2.1.0";

  @Before
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
      TEST_INDEX_WITH_UPDATED_MAPPING.getIndexName()
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

  private InsertDataStep buildInsertDataStep() {
    return new InsertDataStep(
      TEST_INDEX,
      UpgradeUtil.readClasspathFileAsString("steps/insert_data/test_data.json")
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

  private DeleteIndexStep buildDeleteIndexStep(final IndexMappingCreator index) {
    return new DeleteIndexStep(index);
  }

}
