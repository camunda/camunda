/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate35to36;

import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate35to36.indices.DashboardIndexV4Old;
import org.camunda.optimize.upgrade.migrate35to36.indices.SingleDecisionReportIndexV7Old;
import org.camunda.optimize.upgrade.migrate35to36.indices.SingleProcessReportIndexV7Old;
import org.elasticsearch.common.collect.List;
import org.junit.jupiter.api.BeforeEach;

public class AbstractUpgrade36IT extends AbstractUpgradeIT {
  protected static final String FROM_VERSION = "3.6.0";

  protected static final SingleProcessReportIndexV7Old SINGLE_PROCESS_REPORT_INDEX =
    new SingleProcessReportIndexV7Old();
  protected static final SingleDecisionReportIndexV7Old SINGLE_DECISION_REPORT_INDEX =
    new SingleDecisionReportIndexV7Old();
  protected static final DashboardIndexV4Old DASHBOARD_INDEX = new DashboardIndexV4Old();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(List.of(
      SINGLE_PROCESS_REPORT_INDEX,
      SINGLE_DECISION_REPORT_INDEX,
      DASHBOARD_INDEX
    ));
    setMetadataVersion(FROM_VERSION);
  }
}
