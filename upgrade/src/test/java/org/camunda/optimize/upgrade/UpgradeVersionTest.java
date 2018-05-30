package org.camunda.optimize.upgrade;

import com.jayway.jsonpath.JsonPath;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.service.es.schema.type.MetadataType;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.AddFieldStep;
import org.camunda.optimize.upgrade.steps.ChangeFieldTypeStep;
import org.camunda.optimize.upgrade.steps.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.DeleteFieldStep;
import org.camunda.optimize.upgrade.steps.DeleteIndexStep;
import org.camunda.optimize.upgrade.steps.InsertDataStep;
import org.camunda.optimize.upgrade.steps.RenameFieldStep;
import org.camunda.optimize.upgrade.steps.RenameIndexStep;
import org.camunda.optimize.upgrade.steps.UpdateDataStep;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.client.Response;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class UpgradeVersionTest extends AbstractUpgradeTest {

  private static final String SEARCH = "/_search";

  public static final String GET = "GET";

  private static final String TEST_INDEX = "test-index";
  private static final String RENAMED_INDEX = "renamed-index";

  private static final String TEST_TYPE = "users";

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
  public void executeCreateIndexStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .build();

    // when
    upgradePlan.execute();

    // then
    Response response = restClient.performRequest("HEAD", TEST_INDEX);
    assertThat(response.getStatusLine().getStatusCode(), is(200));
  }

  @Test
  public void executeInsertDataStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
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
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
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
  public void executeChangeFieldTypeStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .addUpgradeStep(buildInsertDataStep())
        .addUpgradeStep(buildChangeFieldTypeStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    Response response = restClient.performRequest(GET, TEST_INDEX + SEARCH);
    String bodyAsJson = EntityUtils.toString(response.getEntity());
    String username = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source.username");
    assertThat(username, is("admin"));
    String password = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source.new_password.pw");
    assertThat(password, is("admin"));
  }

  @Test
  public void executeRenamePlainFieldStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .addUpgradeStep(buildInsertDataStep())
        .addUpgradeStep(buildRenameFieldStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    Response response = restClient.performRequest(GET, TEST_INDEX + SEARCH);
    String bodyAsJson = EntityUtils.toString(response.getEntity());
    String username = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source.username");
    assertThat(username, is("admin"));
    String password = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source.renamed_password_field");
    assertThat(password, is("admin"));
  }

  @Test
  public void executeRenameNestedFieldStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .addUpgradeStep(buildInsertDataStep())
        .addUpgradeStep(buildChangeFieldTypeStep())
        .addUpgradeStep(buildNestedRenameFieldStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    Response response = restClient.performRequest(GET, TEST_INDEX + SEARCH);
    String bodyAsJson = EntityUtils.toString(response.getEntity());
    String username = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source.username");
    assertThat(username, is("admin"));
    String password = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source.new_password.renamed_pw");
    assertThat(password, is("admin"));
  }

  @Test
  public void executeAddFieldStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .addUpgradeStep(buildInsertDataStep())
        .addUpgradeStep(buildAddFieldStep())
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
    String newField = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source.added_field");
    assertThat(newField, is(""));
  }

  @Test
  public void executeDeleteFieldStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .addUpgradeStep(buildInsertDataStep())
        .addUpgradeStep(buildDeleteFieldStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    Response response = restClient.performRequest(GET, TEST_INDEX + SEARCH);
    String bodyAsJson = EntityUtils.toString(response.getEntity());
    Map<String, String> entryFields = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source");
    assertThat(entryFields.size(), is(1));
    assertThat(entryFields.get("username"), is("admin"));
  }

  @Test
  public void executeRenameIndexStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .addUpgradeStep(buildRenameIndexStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    Response response = restClient.performRequest("HEAD", RENAMED_INDEX);
    assertThat(response.getStatusLine().getStatusCode(), is(200));
  }

  @Test
  public void executeDeleteIndexStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .addUpgradeStep(buildDeleteIndexStep(TEST_INDEX))
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
  public void executeAllUpgradeStepsAtOnce() throws Exception {
    //given
    createEmptyEnvConfig();

    // when
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
      .addUpgradeStep(buildInsertDataStep())
      .addUpgradeStep(buildUpdateDataStep())
        .addUpgradeStep(buildAddFieldStep())
        .addUpgradeStep(buildDeleteFieldStep())
        .addUpgradeStep(buildChangeFieldTypeStep())
        .addUpgradeStep(buildNestedRenameFieldStep())
      .addUpgradeStep(buildRenameIndexStep())
      .addUpgradeStep(buildDeleteIndexStep(RENAMED_INDEX))
      .build();

    upgradePlan.execute();
    removeVersionIndex();

    // then
    assertThatThereIsNoDataInElasticsearch();
  }

  @Test
  public void versionIsUpdatedAfterPlanWasExecuted() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
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

  @Test
  public void adjustmentsOnOneIndexDoesNoInfluenceTheOthers() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX))
        .addUpgradeStep(buildCreateIndexStep("nochange-index"))
        .addUpgradeStep(buildRenameFieldStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    Response response = restClient.performRequest(GET, "nochange-index" + MAPPING);
    String bodyAsJson = EntityUtils.toString(response.getEntity());
    Object passwordField = JsonPath.read(bodyAsJson, "$.nochange-index.mappings.users.properties.password");
    assertThat(passwordField, notNullValue());
  }

  public void assertThatThereIsNoDataInElasticsearch() throws IOException {
    Response response = restClient.performRequest(GET, SEARCH);
    String bodyAsJson = EntityUtils.toString(response.getEntity());
    Integer numberOfDocuments = JsonPath.read(bodyAsJson, "$.hits.total");
    assertThat(numberOfDocuments, is(0));
  }

  public InsertDataStep buildInsertDataStep() {
    return new InsertDataStep(
    TEST_INDEX,
    TEST_TYPE,
    SchemaUpgradeUtil.readClasspathFileAsString("steps/insert_data/test_data.json")
  );
  }

  public CreateIndexStep buildCreateIndexStep(String indexName) {
    return new CreateIndexStep(
      indexName,
      SchemaUpgradeUtil.readClasspathFileAsString("steps/create_index/new_index_mapping.json")
    );
  }

  private UpdateDataStep buildUpdateDataStep() {
    return new UpdateDataStep(
      TEST_INDEX,
      SchemaUpgradeUtil.readClasspathFileAsString("steps/update_data/query.json"),
      SchemaUpgradeUtil.readClasspathFileAsString("steps/update_data/updateScript.painless")
    );
  }

  private ChangeFieldTypeStep buildChangeFieldTypeStep() {
    return new ChangeFieldTypeStep(
      TEST_INDEX,
      TEST_INDEX,
      SchemaUpgradeUtil.readClasspathFileAsString("steps/change_field_type/new_index_mapping.json"),
      SchemaUpgradeUtil.readClasspathFileAsString("steps/change_field_type/mapping_script.painless")
    );
  }

  private RenameFieldStep buildRenameFieldStep() {
    return new RenameFieldStep(
      TEST_INDEX,
      SchemaUpgradeUtil.readClasspathFileAsString("steps/rename_field/new_index_mapping.json"),
      SchemaUpgradeUtil.readClasspathFileAsString("steps/rename_field/mapping_script.painless")
    );
  }

  private RenameFieldStep buildNestedRenameFieldStep() {
    return new RenameFieldStep(
      TEST_INDEX,
      SchemaUpgradeUtil.readClasspathFileAsString("steps/rename_nested_field/new_index_mapping.json"),
      SchemaUpgradeUtil.readClasspathFileAsString("steps/rename_nested_field/mapping_script.painless")
    );
  }

  private AddFieldStep buildAddFieldStep() {
    return new AddFieldStep(
      TEST_INDEX,
      SchemaUpgradeUtil.readClasspathFileAsString("steps/add_field/new_index_mapping.json"),
      SchemaUpgradeUtil.readClasspathFileAsString("steps/add_field/mapping_script.painless")
    );
  }

  private DeleteFieldStep buildDeleteFieldStep() {
    return new DeleteFieldStep(
      TEST_INDEX,
      SchemaUpgradeUtil.readClasspathFileAsString("steps/delete_field/new_index_mapping.json"),
      SchemaUpgradeUtil.readClasspathFileAsString("steps/delete_field/mapping_script.painless")
    );
  }

  private RenameIndexStep buildRenameIndexStep() {
    return new RenameIndexStep(
      TEST_INDEX,
      RENAMED_INDEX,
      SchemaUpgradeUtil.readClasspathFileAsString("steps/rename_index/new_index_mapping.json")
    );
  }

  private DeleteIndexStep buildDeleteIndexStep(String indexName) {
    return new DeleteIndexStep(indexName);
  }

}