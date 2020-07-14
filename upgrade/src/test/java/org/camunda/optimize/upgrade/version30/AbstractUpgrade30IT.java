/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import org.assertj.core.util.Lists;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.version30.indices.AlertIndexV2;
import org.camunda.optimize.upgrade.version30.indices.BusinessKeyIndexV1;
import org.camunda.optimize.upgrade.version30.indices.CollectionIndexV3;
import org.camunda.optimize.upgrade.version30.indices.CombinedReportIndexV3;
import org.camunda.optimize.upgrade.version30.indices.DashboardIndexV2;
import org.camunda.optimize.upgrade.version30.indices.DashboardShareIndexV2;
import org.camunda.optimize.upgrade.version30.indices.DecisionDefinitionIndexV2;
import org.camunda.optimize.upgrade.version30.indices.DecisionInstanceIndexV3;
import org.camunda.optimize.upgrade.version30.indices.EventIndexV2;
import org.camunda.optimize.upgrade.version30.indices.EventProcessDefinitionIndexV1;
import org.camunda.optimize.upgrade.version30.indices.EventProcessMappingIndexV2;
import org.camunda.optimize.upgrade.version30.indices.EventProcessPublishStateIndexV2;
import org.camunda.optimize.upgrade.version30.indices.ImportIndexIndexV2;
import org.camunda.optimize.upgrade.version30.indices.LicenseIndexV2;
import org.camunda.optimize.upgrade.version30.indices.MetadataIndexV2;
import org.camunda.optimize.upgrade.version30.indices.OnboardingStateIndexV1;
import org.camunda.optimize.upgrade.version30.indices.ProcessDefinitionIndexV2;
import org.camunda.optimize.upgrade.version30.indices.ProcessInstanceIndexV4;
import org.camunda.optimize.upgrade.version30.indices.ReportShareIndexV2;
import org.camunda.optimize.upgrade.version30.indices.SingleDecisionReportIndexV2;
import org.camunda.optimize.upgrade.version30.indices.SingleProcessReportIndexV2;
import org.camunda.optimize.upgrade.version30.indices.TenantIndexV2;
import org.camunda.optimize.upgrade.version30.indices.TerminatedUserSessionIndexV2;
import org.camunda.optimize.upgrade.version30.indices.TimestampBasedImportIndexV3;
import org.camunda.optimize.upgrade.version30.indices.VariableUpdateInstanceIndexV1;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractUpgrade30IT extends AbstractUpgradeIT {

  private static final MetadataIndexV2 METADATA_INDEX = new MetadataIndexV2();
  private static final SingleProcessReportIndexV2 SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndexV2();
  private static final SingleDecisionReportIndexV2 SINGLE_DECISION_REPORT_INDEX = new SingleDecisionReportIndexV2();
  private static final CombinedReportIndexV3 COMBINED_REPORT_INDEX = new CombinedReportIndexV3();
  protected static final ImportIndexIndexV2 IMPORT_INDEX_INDEX = new ImportIndexIndexV2();
  protected static final TimestampBasedImportIndexV3 TIMESTAMP_BASED_IMPORT_INDEX = new TimestampBasedImportIndexV3();
  private static final AlertIndexV2 ALERT_INDEX = new AlertIndexV2();
  private static final ProcessInstanceIndexV4 PROCESS_INSTANCE_INDEX = new ProcessInstanceIndexV4();
  private static final EventProcessInstanceIndexV4 EVENT_PROCESS_INSTANCE_INDEX = new EventProcessInstanceIndexV4();
  private static final EventProcessPublishStateIndexV2 EVENT_PROCESS_PUBLISH_STATE_INDEX =
    new EventProcessPublishStateIndexV2();
  private static final BusinessKeyIndexV1 BUSINESS_KEY_INDEX = new BusinessKeyIndexV1();
  private static final CollectionIndexV3 COLLECTION_INDEX = new CollectionIndexV3();
  private static final DashboardIndexV2 DASHBOARD_INDEX = new DashboardIndexV2();
  private static final DashboardShareIndexV2 DASHBOARD_SHARE_INDEX = new DashboardShareIndexV2();
  private static final DecisionDefinitionIndexV2 DECISION_DEFINITION_INDEX = new DecisionDefinitionIndexV2();
  private static final DecisionInstanceIndexV3 DECISION_INSTANCE_INDEX = new DecisionInstanceIndexV3();
  private static final EventIndexV2 EVENT_INDEX = new EventIndexV2();
  private static final EventProcessDefinitionIndexV1 EVENT_PROCESS_DEFINITION_INDEX =
    new EventProcessDefinitionIndexV1();
  private static final EventProcessMappingIndexV2 EVENT_PROCESS_MAPPING_INDEX = new EventProcessMappingIndexV2();
  private static final LicenseIndexV2 LICENSE_INDEX = new LicenseIndexV2();
  private static final OnboardingStateIndexV1 ONBOARDING_STATE_INDEX = new OnboardingStateIndexV1();
  private static final ProcessDefinitionIndexV2 PROCESS_DEFINITION_INDEX = new ProcessDefinitionIndexV2();
  private static final ReportShareIndexV2 REPORT_SHARE_INDEX = new ReportShareIndexV2();
  private static final TerminatedUserSessionIndexV2 TERMINATED_USER_SESSION_INDEX = new TerminatedUserSessionIndexV2();
  private static final TenantIndexV2 TENANT_INDEX_V_2 = new TenantIndexV2();
  private static final VariableUpdateInstanceIndexV1 VARIABLE_UPDATE_INSTANCE_INDEX =
    new VariableUpdateInstanceIndexV1();

  private static final String FROM_VERSION = "3.0.0";

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(Lists.newArrayList(
      METADATA_INDEX,
      SINGLE_PROCESS_REPORT_INDEX,
      SINGLE_DECISION_REPORT_INDEX,
      COMBINED_REPORT_INDEX,
      TIMESTAMP_BASED_IMPORT_INDEX,
      IMPORT_INDEX_INDEX,
      ALERT_INDEX,
      PROCESS_INSTANCE_INDEX,
      EVENT_PROCESS_INSTANCE_INDEX,
      EVENT_PROCESS_PUBLISH_STATE_INDEX,
      BUSINESS_KEY_INDEX,
      COLLECTION_INDEX,
      DASHBOARD_INDEX,
      DASHBOARD_SHARE_INDEX,
      DECISION_DEFINITION_INDEX,
      DECISION_INSTANCE_INDEX,
      EVENT_INDEX,
      EVENT_PROCESS_DEFINITION_INDEX,
      EVENT_PROCESS_MAPPING_INDEX,
      LICENSE_INDEX,
      PROCESS_DEFINITION_INDEX,
      ONBOARDING_STATE_INDEX,
      REPORT_SHARE_INDEX,
      TERMINATED_USER_SESSION_INDEX,
      TENANT_INDEX_V_2,
      VARIABLE_UPDATE_INSTANCE_INDEX
    ));
    setMetadataIndexVersion(FROM_VERSION);

    // event process instance index also are associated as read only with the process-instance alias
    addAlias(
      EVENT_PROCESS_INSTANCE_INDEX,
      indexNameService.getOptimizeIndexAliasForIndex(PROCESS_INSTANCE_INDEX.getIndexName()),
      false
    );
  }

}
