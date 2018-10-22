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
import java.util.Collections;
import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class UpgradeVersionTest extends AbstractUpgradeTest {

  private static final String SEARCH = "/_search";
  private static final String MAPPING = "/_mapping";

  private static final String GET = "GET";

  private static final String TEST_TYPE = "users";
  private static final String TEST_INDEX = "optimize-users";
  private static final String RENAMED_TYPE = "renamed-users";
  private static final String RENAMED_INDEX = "optimize-renamed-users";


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
        .addUpgradeStep(buildCreateIndexStep(TEST_TYPE))
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
        .addUpgradeStep(buildCreateIndexStep(TEST_TYPE))
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
        .addUpgradeStep(buildCreateIndexStep(TEST_TYPE))
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
        .addUpgradeStep(buildCreateIndexStep(TEST_TYPE))
        .addUpgradeStep(buildInsertDataStep())
        .addUpgradeStep(buildChangeFieldTypeStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    Response response = restClient.performRequest(GET, TEST_INDEX + MAPPING);
    String bodyAsJson = EntityUtils.toString(response.getEntity());
    String passwordType = JsonPath.read(bodyAsJson, "$.optimize-users.mappings.users.properties.password.type");
    assertThat(passwordType, is("text"));
  }

  @Test
  public void executeChangeFieldTypeToNestedStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_TYPE))
        .addUpgradeStep(buildInsertDataStep())
        .addUpgradeStep(buildChangeFieldTypeToNestedStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    Response response = restClient.performRequest(GET, TEST_INDEX + SEARCH);
    String bodyAsJson = EntityUtils.toString(response.getEntity());
    String username = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source.username");
    assertThat(username, is("admin"));
    String password = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source.password.pw");
    assertThat(password, is("admin"));
  }

  @Test
  public void executeRenamePlainFieldStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_TYPE))
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
        .addUpgradeStep(buildCreateIndexStep(TEST_TYPE))
        .addUpgradeStep(buildInsertDataStep())
        .addUpgradeStep(buildChangeFieldTypeToNestedStep())
        .addUpgradeStep(buildNestedRenameFieldStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    Response response = restClient.performRequest(GET, TEST_INDEX + SEARCH);
    String bodyAsJson = EntityUtils.toString(response.getEntity());
    String username = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source.username");
    assertThat(username, is("admin"));
    String password = JsonPath.read(bodyAsJson, "$.hits.hits[0]._source.password.renamed_pw");
    assertThat(password, is("admin"));
  }

  @Test
  public void executeAddFieldStep() throws Exception {
    //given
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_TYPE))
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
        .addUpgradeStep(buildCreateIndexStep(TEST_TYPE))
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
        .addUpgradeStep(buildCreateIndexStep(TEST_TYPE))
        .addUpgradeStep(buildInsertDataStep())
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
        .addUpgradeStep(buildCreateIndexStep(TEST_TYPE))
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
  public void executeAllUpgradeStepsAtOnce() throws Exception {
    //given
    createEmptyEnvConfig();

    // when
    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(buildCreateIndexStep(TEST_TYPE))
      .addUpgradeStep(buildInsertDataStep())
      .addUpgradeStep(buildUpdateDataStep())
        .addUpgradeStep(buildAddFieldStep())
        .addUpgradeStep(buildChangeFieldTypeToNestedStep())
        .addUpgradeStep(buildNestedRenameFieldStep())
        .addUpgradeStep(buildDeleteFieldStep())
        .addUpgradeStep(buildRenameIndexStep())
      .addUpgradeStep(buildDeleteIndexStep(RENAMED_TYPE))
      .build();

    upgradePlan.execute();
    removeVersionIndex();
    removeTaskIndex();

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
        .addUpgradeStep(buildCreateIndexStep(TEST_TYPE))
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
        .addUpgradeStep(buildCreateIndexStep(TEST_TYPE))
        .addUpgradeStep(buildCreateIndexStep("nochange-type"))
        .addUpgradeStep(buildRenameFieldStep())
        .build();

    // when
    upgradePlan.execute();

    // then
    Response response = restClient.performRequest(GET, "optimize-nochange-type" + MAPPING);
    String bodyAsJson = EntityUtils.toString(response.getEntity());
    Object passwordField = JsonPath.read(bodyAsJson, "$.optimize-nochange-type.mappings.users.properties.password");
    assertThat(passwordField, notNullValue());
  }

  private void assertThatThereIsNoDataInElasticsearch() throws IOException {
    Response response = restClient.performRequest(GET, SEARCH);
    String bodyAsJson = EntityUtils.toString(response.getEntity());
    Integer numberOfDocuments = JsonPath.read(bodyAsJson, "$.hits.total");
    assertThat(numberOfDocuments, is(0));
  }

  private InsertDataStep buildInsertDataStep() {
    return new InsertDataStep(
      TEST_TYPE,
      SchemaUpgradeUtil.readClasspathFileAsString("steps/insert_data/test_data.json")
    );
  }

  private CreateIndexStep buildCreateIndexStep(String indexName) {
    return new CreateIndexStep(
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

  private ChangeFieldTypeStep buildChangeFieldTypeStep() {
    return new ChangeFieldTypeStep(
      TEST_TYPE,
      "$.mappings.users.properties.password.type",
      "text",
      null
    );
  }

  private ChangeFieldTypeStep buildChangeFieldTypeToNestedStep() {
    return new ChangeFieldTypeStep(
      TEST_TYPE,
      "$.mappings.users.properties.password",
      new NestedPasswordType(),
      "def tmp = ctx._source.remove(\"password\");" +
        "ctx._source.password = new HashMap();\n" +
        "ctx._source.password.pw = tmp;"
    );
  }

  public class NestedPasswordType {
    public String type = "nested";
    public Properties properties = new Properties();

    public class Properties {
      public PW pw = new PW();

      public class PW {
        public String type = "keyword";
      }
    }
  }

  private RenameFieldStep buildRenameFieldStep() {
    return new RenameFieldStep(
      TEST_TYPE,
      "$.mappings.users.properties",
      "password",
      "renamed_password_field",
      "ctx._source.renamed_password_field = ctx._source.remove(\"password\");"
    );
  }

  private RenameFieldStep buildNestedRenameFieldStep() {
    return new RenameFieldStep(
      TEST_TYPE,
      "$.mappings.users.properties.password.properties",
      "pw",
      "renamed_pw",
      "def tempPassword = ctx._source.password.remove(\"pw\");\n" +
        "ctx._source.password = new HashMap();\n" +
        "ctx._source.password.renamed_pw = tempPassword;"
    );
  }

  private AddFieldStep buildAddFieldStep() {

    return new AddFieldStep(
      TEST_TYPE,
      "$.mappings.users.properties",
      "added_field",
      Collections.singletonMap("type", "keyword"),
      "ctx._source.added_field = \"\""
    );
  }

  private DeleteFieldStep buildDeleteFieldStep() {
    return new DeleteFieldStep(
      TEST_TYPE,
      "$.mappings.users.properties.password",
      "ctx._source.remove(\"password\");\n" +
        "ctx._source.remove(\"added_field\");"
    );
  }

  private RenameIndexStep buildRenameIndexStep() {
    return new RenameIndexStep(
      TEST_TYPE,
      RENAMED_TYPE
    );
  }

  private DeleteIndexStep buildDeleteIndexStep(String indexName) {
    return new DeleteIndexStep(indexName);
  }

}