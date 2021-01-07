/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate32To33;

import org.camunda.optimize.service.metadata.PreviousVersion;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate32To33.indices.DashboardIndexV3Old;
import org.camunda.optimize.upgrade.migrate32To33.indices.DecisionDefinitionIndexV3Old;
import org.camunda.optimize.upgrade.migrate32To33.indices.EventIndexV3Old;
import org.camunda.optimize.upgrade.migrate32To33.indices.EventProcessDefinitionIndexV2Old;
import org.camunda.optimize.upgrade.migrate32To33.indices.ProcessDefinitionIndexV3Old;
import org.camunda.optimize.upgrade.migrate32To33.indices.SingleDecisionReportIndexV4Old;
import org.camunda.optimize.upgrade.migrate32To33.indices.SingleProcessReportIndexV4Old;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;

public class AbstractUpgrade32IT extends AbstractUpgradeIT {

  protected static final SingleProcessReportIndexV4Old SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndexV4Old();
  protected static final SingleDecisionReportIndexV4Old SINGLE_DECISION_REPORT_INDEX =
    new SingleDecisionReportIndexV4Old();
  protected static final ProcessDefinitionIndexV3Old PROCESS_DEFINITION_INDEX = new ProcessDefinitionIndexV3Old();
  protected static final DecisionDefinitionIndexV3Old DECISION_DEFINITION_INDEX = new DecisionDefinitionIndexV3Old();
  protected static final EventProcessDefinitionIndexV2Old EVENT_PROCESS_DEFINITION_INDEX =
    new EventProcessDefinitionIndexV2Old();
  protected static final DashboardIndexV3Old DASHBOARD_INDEX = new DashboardIndexV3Old();
  protected static final EventIndexV3Old EVENT_INDEX = new EventIndexV3Old();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(
      Arrays.asList(
        METADATA_INDEX,
        SINGLE_PROCESS_REPORT_INDEX,
        SINGLE_DECISION_REPORT_INDEX,
        PROCESS_DEFINITION_INDEX,
        DECISION_DEFINITION_INDEX,
        EVENT_PROCESS_DEFINITION_INDEX,
        DASHBOARD_INDEX,
        EVENT_INDEX
      )
    );
    setMetadataVersion(PreviousVersion.PREVIOUS_VERSION);
  }

}
