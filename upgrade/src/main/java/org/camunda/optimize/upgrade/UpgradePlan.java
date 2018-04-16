package org.camunda.optimize.upgrade;

import org.camunda.optimize.upgrade.plan.AbstractUpgradePlan;
import org.camunda.optimize.upgrade.steps.UpdateDataStep;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;

/**
 * @author Askar Akhmerov
 */
public class UpgradePlan extends AbstractUpgradePlan {

  private static final String DOC_TYPE = "_doc";

  private static final String META_INDEX = "optimize-metadata";

  static {
    UPGRADE_STEPS.add(
      new UpdateDataStep(
        META_INDEX,
        DOC_TYPE,
        SchemaUpgradeUtil.readClasspathFileAsString("steps/update_version/query.json"),
        SchemaUpgradeUtil.readClasspathFileAsString("steps/update_version/updateScript.json")
      )
    );
  }
}
