package org.camunda.optimize.upgrade;

import org.camunda.optimize.upgrade.steps.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.DeleteIndexStep;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Askar Akhmerov
 */
public class DateFormatProcessingTest extends AbstractUpgradeTest {

  private static final String TEST_INDEX = "test-index";
  private String[] args;
  private AbstractUpgradePlan upgradeSchema;


  @Before
  public void setUp() {
    restClient = initClient();

    this.args = new String[0];
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

  @Test
  public void verifyDateFormatEnhancedFromConfig() {
    UpgradeService toTest = new UpgradeService(upgradeSchema, args);
    Consumer<UpgradeStep> assertions = this::assertDateFormatsWithConfig;
    toTest.execute(assertions);
  }

  private void assertDateFormatsWithConfig(UpgradeStep upgradeStep) {
    if (upgradeStep instanceof CreateIndexStep) {
      String expectedMapping = SchemaUpgradeUtil.readClasspathFileAsString("steps/date_field/expected_mapping_with_config.json");
      assertMappingAfterReindex(expectedMapping, ((CreateIndexStep) upgradeStep).getIndexName());
    }
  }

}
