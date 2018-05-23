package org.camunda.optimize.upgrade;

import org.camunda.optimize.upgrade.plan.AbstractUpgradePlan;
import org.camunda.optimize.upgrade.service.UpgradeService;
import org.camunda.optimize.upgrade.steps.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.DeleteIndexStep;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.camunda.optimize.upgrade.service.ValidationServiceTest.OPTIMIZE_METADATA;

public class DateFormatProcessingTest extends AbstractUpgradeTest {

  private static final String TEST_INDEX = "test-index";
  private String[] args = new String[] {"--config", "/../test-classes/upgrade-config.yaml"};
  private AbstractUpgradePlan upgradeSchema;


  @Before
  public void setUp() {
    restClient = initClient();

    this.args = new String[] {"--config", "/../test-classes/upgrade-config.yaml"};
    this.upgradeSchema = new AbstractUpgradePlan() {
      @Override
      public List<UpgradeStep> getUpgradeSteps() {
        List<UpgradeStep> steps = new ArrayList<>();
        steps.add(
          new CreateIndexStep(
            TEST_INDEX,
            SchemaUpgradeUtil.readClasspathFileAsString("steps/date_field/mapping.json")
          )
        );
        steps.add(new DeleteIndexStep(TEST_INDEX));
        return steps;
      }
    };
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
  public void verifyDateFormatEnhancedFromConfig() {
    UpgradeService toTest = new UpgradeService(upgradeSchema, args);
    Consumer<UpgradeStep> assertions = this::assertDateFormatsWithConfig;
    toTest.execute(assertions);
  }

  private void assertDateFormatsWithConfig(UpgradeStep upgradeStep) {
    if (upgradeStep instanceof CreateIndexStep) {
      String expectedMapping =
        SchemaUpgradeUtil.readClasspathFileAsString("steps/date_field/expected_mapping_with_config.json");
      expectedMapping = expectedMapping.replaceAll("\\s", "");
      assertMappingAfterReindex(expectedMapping, ((CreateIndexStep) upgradeStep).getIndexName());
    }
  }

}
