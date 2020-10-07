/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate31To32;

import com.google.common.collect.Lists;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate31To32.indices.AlertIndexV3Old;
import org.camunda.optimize.upgrade.migrate31To32.indices.EventProcessInstanceIndexV5Old;
import org.camunda.optimize.upgrade.migrate31To32.indices.ProcessInstanceIndexV5Old;
import org.camunda.optimize.upgrade.migrate31To32.indices.SingleDecisionReportIndexV3Old;
import org.camunda.optimize.upgrade.migrate31To32.indices.SingleProcessReportIndexV3Old;
import org.junit.jupiter.api.BeforeEach;

import static org.camunda.optimize.upgrade.main.impl.UpgradeFrom31To32.FROM_VERSION;

public abstract class AbstractUpgrade31IT extends AbstractUpgradeIT {
  public static final String EVENT_PROCESS_ID = "dd1bfc87-6187-4470-a289-25da2455968f";

  private static final AlertIndexV3Old ALERT_INDEX = new AlertIndexV3Old();
  private static final SingleProcessReportIndexV3Old SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndexV3Old();
  private static final SingleDecisionReportIndexV3Old SINGLE_DECISION_REPORT_INDEX = new SingleDecisionReportIndexV3Old();
  private static final ProcessInstanceIndexV5Old PROCESS_INSTANCE_INDEX = new ProcessInstanceIndexV5Old();
  private static final EventProcessInstanceIndexV5Old EVENT_PROCESS_INSTANCE_INDEX =
    new EventProcessInstanceIndexV5Old(EVENT_PROCESS_ID);

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(
      Lists.newArrayList(
        METADATA_INDEX,
        ALERT_INDEX,
        SINGLE_PROCESS_REPORT_INDEX,
        SINGLE_DECISION_REPORT_INDEX,
        PROCESS_INSTANCE_INDEX,
        EVENT_PROCESS_INSTANCE_INDEX
      )
    );
    setMetadataVersion(FROM_VERSION);
  }
}
