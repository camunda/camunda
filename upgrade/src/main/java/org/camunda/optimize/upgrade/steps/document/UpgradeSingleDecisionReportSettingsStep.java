package org.camunda.optimize.upgrade.steps.document;

import org.elasticsearch.index.query.QueryBuilders;

import java.util.Collections;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_TYPE;

public class UpgradeSingleDecisionReportSettingsStep extends AbstractReportConfigurationUpdateStep {

  public UpgradeSingleDecisionReportSettingsStep(final Map defaultReportConfiguration) {
    super(
      SINGLE_DECISION_REPORT_TYPE,
      QueryBuilders.matchAllQuery(),
      getDeepCopyMapScript() +
        "def reportData = ctx._source.data;\n" +
        "def newConfig = deepCopyMap(params.defaultConfiguration);\n" +
        // compatible field migration
        getMigrateCompatibleFieldsScript() +
        "reportData.configuration = newConfig;\n",
      Collections.singletonMap("defaultConfiguration", defaultReportConfiguration)
    );
  }

}
