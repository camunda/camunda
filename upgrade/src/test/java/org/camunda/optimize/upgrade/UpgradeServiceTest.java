package org.camunda.optimize.upgrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.EntityUtils;
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
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * This test assumes that optimize-users index exists and
 * contains at least one user.
 *
 * @author Askar Akhmerov
 */
public class UpgradeServiceTest extends AbstractUpgradeTest {

  private static final String SEARCH = "/_search";
  private Map<String, Consumer<UpgradeStep>> stepAssertions = new HashMap<>();
  private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void execute() {
    //given
    restClient = initClient();
    initAssertions();
    String[] args = new String[0];
    UpgradeService toTest = new UpgradeService(new TestSchemaUpgrade(), args);

    //assertions
    Consumer<UpgradeStep> consumer = this::assertStepExecutionResult;

    //when - execute with assertions
    toTest.execute(consumer);
  }

  //then
  public void assertStepExecutionResult(UpgradeStep step) {
    this.stepAssertions.get(step.getName()).accept(step);
  }

  private void initAssertions() {
    stepAssertions.put(ChangeFieldTypeStep.NAME, this::assertFieldTypeChange);
    stepAssertions.put(CreateIndexStep.NAME, this::assertIndexCreation);
    stepAssertions.put(InsertDataStep.NAME, this::assertDataInsertion);
    stepAssertions.put(RenameFieldStep.NAME, this::assertRenameField);
    stepAssertions.put(RenameIndexStep.NAME, this::assertIndexRename);
    stepAssertions.put(AddFieldStep.NAME, this::assertAddField);
    stepAssertions.put(DeleteFieldStep.NAME, this::assertDeleteField);
    stepAssertions.put(DeleteIndexStep.NAME, this::assertDeleteIndex);
    stepAssertions.put(UpdateDataStep.NAME, this::assertUpdate);
  }

  private void assertUpdate(UpgradeStep upgradeStep) {
    UpdateDataStep cast = (UpdateDataStep) upgradeStep;

    Response response = null;

    try {
      response = restClient.performRequest("GET", cast.getIndex() + SEARCH);
      String body = EntityUtils.toString(response.getEntity());
      HashMap <String, Object> parsedBody = objectMapper.readValue(body, HashMap.class);
      Object passwordValue = ((Map)((Map)((List)((Map)parsedBody.get("hits")).get("hits")).get(0)).get("_source")).get("password");
      assertThat(passwordValue, is("admin1"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void assertDeleteIndex(UpgradeStep upgradeStep) {
    DeleteIndexStep cast = (DeleteIndexStep) upgradeStep;

    Response response = null;
    try {
      response = restClient.performRequest("HEAD", cast.getIndexName());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    assertThat(response.getStatusLine().getStatusCode(), is(404));
  }

  private void assertDeleteField(UpgradeStep upgradeStep) {
    DeleteFieldStep cast = (DeleteFieldStep) upgradeStep;
    String expectedMapping = SchemaUpgradeUtil.readClasspathFileAsString("steps/delete_field/expected_mapping.json");

    assertMappingAfterReindex(expectedMapping, cast.getFinalIndexName());
  }

  private void assertAddField(UpgradeStep upgradeStep) {
    AddFieldStep cast = (AddFieldStep) upgradeStep;
    String expectedMapping = SchemaUpgradeUtil.readClasspathFileAsString("steps/add_field/expected_mapping.json");

    assertMappingAfterReindex(expectedMapping, cast.getFinalIndexName());
  }

  private void assertIndexRename(UpgradeStep upgradeStep) {
    RenameIndexStep cast = (RenameIndexStep) upgradeStep;

    Response response = null;
    try {
      response = restClient.performRequest("HEAD", cast.getFinalIndexName());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    assertThat(response.getStatusLine().getStatusCode(), is(200));

    try {
      response = restClient.performRequest("HEAD", cast.getInitialIndexName());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    assertThat(response.getStatusLine().getStatusCode(), is(404));
  }

  private void assertRenameField(UpgradeStep upgradeStep) {
    RenameFieldStep cast = (RenameFieldStep) upgradeStep;
    String expectedMapping = SchemaUpgradeUtil.readClasspathFileAsString("steps/rename_field/expected_mapping.json");

    assertMappingAfterReindex(expectedMapping, cast.getFinalIndexName());
  }



  private void assertDataInsertion(UpgradeStep upgradeStep) {
    InsertDataStep cast = (InsertDataStep) upgradeStep;

    Response response = null;
    try {
      response = restClient.performRequest("GET", cast.getIndexName() + SEARCH);
      String body = EntityUtils.toString(response.getEntity());
      HashMap <String, Object> parsedBody = objectMapper.readValue(body, HashMap.class);

      assertNonMapNodes(parsedBody);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void assertIndexCreation(UpgradeStep upgradeStep) {
    CreateIndexStep cast = (CreateIndexStep) upgradeStep;

    Response response = null;
    try {
      response = restClient.performRequest("HEAD", cast.getIndexName());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    assertThat(response.getStatusLine().getStatusCode(), is(200));
  }

  private void assertFieldTypeChange(UpgradeStep upgradeStep) {
    ChangeFieldTypeStep cast = (ChangeFieldTypeStep) upgradeStep;
    String expectedMapping = SchemaUpgradeUtil.readClasspathFileAsString("steps/change_field_type/expected_mapping.json");

    assertMappingAfterReindex(expectedMapping, cast.getFinalIndexName());
    assertDataInsertion(cast.getFinalIndexName());
  }

  private void assertDataInsertion(String finalIndexName) {
    Response response = null;
    try {
      response = restClient.performRequest("GET", finalIndexName + SEARCH);
      String body = EntityUtils.toString(response.getEntity());
      HashMap <String, Object> parsedBody = objectMapper.readValue(body, HashMap.class);

      assertNonMapNodes(parsedBody);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void assertNonMapNodes(HashMap<String, Object> parsedBody) {
    for (Map.Entry entry : parsedBody.entrySet()) {
      Object value = entry.getValue();
      assertValue(value);
    }
  }

  private void assertValue(Object value) {
    if (value instanceof Map) {
      assertNonMapNodes((HashMap<String, Object>) value);
    } else if (value instanceof List) {
      List castValue = (List) value;
      for (Object o : castValue) {
        assertValue(o);
      }
    } else {
      assertThat(value, is(notNullValue()));
    }
  }

}