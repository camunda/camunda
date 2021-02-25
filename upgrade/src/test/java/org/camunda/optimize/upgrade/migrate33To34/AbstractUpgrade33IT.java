/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34;

import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate33To34.indices.EventProcessMappingIndexV3Old;
import org.camunda.optimize.upgrade.migrate33To34.indices.EventProcessPublishStateIndexV3Old;
import org.camunda.optimize.upgrade.migrate33To34.indices.SingleDecisionReportIndexV5Old;
import org.camunda.optimize.upgrade.migrate33To34.indices.SingleProcessReportIndexV5Old;
import org.camunda.optimize.upgrade.plan.indices.DecisionInstanceIndexV4Old;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;

public class AbstractUpgrade33IT extends AbstractUpgradeIT {
  protected static final String FROM_VERSION = "3.3.0";

  protected static final SingleProcessReportIndexV5Old PROCESS_REPORT_INDEX = new SingleProcessReportIndexV5Old();
  protected static final SingleDecisionReportIndexV5Old DECISION_REPORT_INDEX = new SingleDecisionReportIndexV5Old();
  protected static final DecisionInstanceIndexV4Old DECISION_INSTANCE_INDEX = new DecisionInstanceIndexV4Old();
  protected static final EventProcessMappingIndexV3Old EVENT_MAPPING_INDEX = new EventProcessMappingIndexV3Old();
  protected static final EventProcessPublishStateIndexV3Old EVENT_PUBLISH_STATE_INDEX =
    new EventProcessPublishStateIndexV3Old();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(
      Arrays.asList(
        PROCESS_REPORT_INDEX,
        DECISION_REPORT_INDEX,
        EVENT_MAPPING_INDEX,
        EVENT_PUBLISH_STATE_INDEX,
        DECISION_INSTANCE_INDEX
      )
    );
    setMetadataVersion(FROM_VERSION);
  }

}
