package org.camunda.optimize.upgrade;

import com.jayway.jsonpath.JsonPath;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.CreateIndexStep;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.client.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class DateFormatProcessingTest extends AbstractUpgradeTest {

  private static final String TEST_INDEX = "test-index";

  @Before
  public void setUp() {
    initClient();
    cleanAllDataFromElasticsearch();
    try {
      addVersionToElasticsearch("2.0.0");
    } catch (IOException e) {
      // ignore
    }
  }

  @After
  public void tearDown() {
    try {
      restClient.performRequest("DELETE", OPTIMIZE_METADATA, Collections.emptyMap());
    } catch (IOException e) {
      //nothing to do
    }
  }

  @Test
  public void verifyDateFormatEnhancedFromConfig() throws Exception {
    // given
    createEmptyEnvConfig();

    UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion("2.0.0")
        .toVersion("2.1.0")
        .addUpgradeStep(buildIndexWithDateFieldStep(TEST_INDEX))
        .build();

    // when
    upgradePlan.execute();

    // then
    Response response = restClient.performRequest("GET", TEST_INDEX + MAPPING);
    String bodyAsJson = EntityUtils.toString(response.getEntity());
    String dateFormat = JsonPath.read(bodyAsJson, "$.test-index.mappings.users.properties.date_field.format");
    assertThat(dateFormat, is("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
  }

  public CreateIndexStep buildIndexWithDateFieldStep(String indexName) {
    return new CreateIndexStep(
      indexName,
      SchemaUpgradeUtil.readClasspathFileAsString("steps/date_field/mapping.json")
    );
  }

}
