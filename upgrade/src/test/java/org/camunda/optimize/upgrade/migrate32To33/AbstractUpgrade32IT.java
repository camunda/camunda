/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate32To33;

import org.assertj.core.util.Lists;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate32To33.indices.SingleDecisionReportIndexV4Old;
import org.camunda.optimize.upgrade.migrate32To33.indices.SingleProcessReportIndexV4Old;
import org.junit.jupiter.api.BeforeEach;

import static org.camunda.optimize.upgrade.main.impl.UpgradeFrom32To33.FROM_VERSION;

public class AbstractUpgrade32IT extends AbstractUpgradeIT {

  private static final SingleProcessReportIndexV4Old SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndexV4Old();
  private static final SingleDecisionReportIndexV4Old SINGLE_DECISION_REPORT_INDEX = new SingleDecisionReportIndexV4Old();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(
      Lists.newArrayList(
        METADATA_INDEX,
        SINGLE_PROCESS_REPORT_INDEX,
        SINGLE_DECISION_REPORT_INDEX
      )
    );
    setMetadataVersion(FROM_VERSION);
  }

}
