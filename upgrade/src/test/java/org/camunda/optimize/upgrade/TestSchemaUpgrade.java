package org.camunda.optimize.upgrade;

import org.camunda.optimize.upgrade.plan.AbstractUpgradePlan;
import org.camunda.optimize.upgrade.steps.AddFieldStep;
import org.camunda.optimize.upgrade.steps.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.DeleteFieldStep;
import org.camunda.optimize.upgrade.steps.DeleteIndexStep;
import org.camunda.optimize.upgrade.steps.InsertDataStep;
import org.camunda.optimize.upgrade.steps.RenameFieldStep;
import org.camunda.optimize.upgrade.steps.ChangeFieldTypeStep;
import org.camunda.optimize.upgrade.steps.RenameIndexStep;
import org.camunda.optimize.upgrade.steps.UpdateDataStep;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;

/**
 * @author Askar Akhmerov
 */
public class TestSchemaUpgrade extends AbstractUpgradePlan {

  private static final String TEST_INDEX = "test-index";

  private static final String RENAMED_INDEX = "renamed-index";

  private static final String TEST_TYPE = "users";

  static {
    UPGRADE_STEPS.add(
      new CreateIndexStep(
        TEST_INDEX,
        SchemaUpgradeUtil.readClasspathFileAsString("steps/create_index/new_index_mapping.json")
      )
    );

    UPGRADE_STEPS.add(
      new InsertDataStep(
        TEST_INDEX,
        TEST_TYPE,
        SchemaUpgradeUtil.readClasspathFileAsString("steps/insert_data/test_data.json")
      )
    );

    UPGRADE_STEPS.add(
      new UpdateDataStep(
        TEST_INDEX,
        TEST_TYPE,
        SchemaUpgradeUtil.readClasspathFileAsString("steps/update_data/query.json"),
        SchemaUpgradeUtil.readClasspathFileAsString("steps/update_data/updateScript.json")
      )
    );

    UPGRADE_STEPS.add(new ChangeFieldTypeStep(
      TEST_INDEX,
      TEST_INDEX,
      SchemaUpgradeUtil.readClasspathFileAsString("steps/change_field_type/new_index_mapping.json"),
      SchemaUpgradeUtil.readClasspathFileAsString("steps/change_field_type/mapping_script.painless")
    ));

    UPGRADE_STEPS.add(
      new RenameFieldStep(
        TEST_INDEX,
        SchemaUpgradeUtil.readClasspathFileAsString("steps/rename_field/new_index_mapping.json"),
        SchemaUpgradeUtil.readClasspathFileAsString("steps/rename_field/mapping_script.painless")
      )
    );

    UPGRADE_STEPS.add(
      new AddFieldStep(
        TEST_INDEX,
        SchemaUpgradeUtil.readClasspathFileAsString("steps/add_field/new_index_mapping.json")
      )
    );

    UPGRADE_STEPS.add(
      new DeleteFieldStep(
        TEST_INDEX,
        SchemaUpgradeUtil.readClasspathFileAsString("steps/delete_field/new_index_mapping.json")
      )
    );

    UPGRADE_STEPS.add(
      new RenameIndexStep(
        TEST_INDEX,
        RENAMED_INDEX,
        SchemaUpgradeUtil.readClasspathFileAsString("steps/rename_index/new_index_mapping.json")
      )
    );

    UPGRADE_STEPS.add(
      new DeleteIndexStep(RENAMED_INDEX)
    );
  }
}
