package org.camunda.optimize.upgrade;

import com.jayway.jsonpath.JsonPath;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.service.es.schema.type.MetadataType;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.document.InsertDataStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexAliasForExistingIndexStep;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexStep;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.client.Response;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexNameForAliasAndVersion;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class UpgradeVersionTest extends AbstractUpgradeTest {

  private static final String SEARCH = "/_search";
  private static final String MAPPING = "/_mapping";

  private static final String GET = "GET";

  private static final String TEST_TYPE = "users";
  private static final String TEST_INDEX = "optimize-users";

  private static final String FROM_VERSION = "2.0.0";
  private static final String TO_VERSION = "2.1.0";

  @Before
  public void init() throws Exception {
    initClient();
    cleanAllDataFromElasticsearch();
    try {
      addVersionToElasticsearch(FROM_VERSION);
    } catch (IOException e) {
      // ignore
    }
    createEmptyEnvConfig();
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
    Response aliasResponse = restClient.performRequest("HEAD", TEST_INDEX);
    assertThat(aliasResponse.getStatusLine().getStatusCode(), is(200));
    Response indexResponse = restClient.performRequest(
      "HEAD",
      getOptimizeIndexNameForAliasAndVersion(TEST_INDEX, TO_VERSION)
    );
    assertThat(indexResponse.getStatusLine().getStatusCode(), is(200));
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
    Response response = restClient.performRequest("HEAD", TEST_INDEX);
    assertThat(response.getStatusLine().getStatusCode(), is(200));
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
    Response aliasResponse = restClient.performRequest("HEAD", TEST_INDEX);
    assertThat(aliasResponse.getStatusLine().getStatusCode(), is(200));
    Response indexWithVersionResponse = restClient.performRequest(
      "HEAD",
      getOptimizeIndexNameForAliasAndVersion(
        TEST_INDEX,
        TO_VERSION
      )
    );
    assertThat(indexWithVersionResponse.getStatusLine().getStatusCode(), is(200));
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
    Response response = restClient.performRequest(GET, TEST_INDEX + SEARCH);
    String bodyAsJson = EntityUtils.toString(response.getEntity());
    String username = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source.username");
    assertThat(username, is("admin"));
    String password = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source.password");
    assertThat(password, is("admin"));
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
    Response response = restClient.performRequest(GET, TEST_INDEX + SEARCH);
    String bodyAsJson = EntityUtils.toString(response.getEntity());
    String username = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source.username");
    assertThat(username, is("admin"));
    String password = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source.password");
    assertThat(password, is("admin1"));
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
    try {
      restClient.performRequest(GET, TEST_INDEX + SEARCH);
      fail("Should throw an exception, because index does not exist anymore!");
    } catch (Exception ex) {
      // expected
    }
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
    Response response = restClient.performRequest(GET, OPTIMIZE_METADATA + SEARCH);
    String bodyAsJson = EntityUtils.toString(response.getEntity());
    String actualSchemaVersion =
      JsonPath.read(bodyAsJson, "$.hits.hits[0]._source." + MetadataType.SCHEMA_VERSION);
    assertThat(actualSchemaVersion, is(TO_VERSION));
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
