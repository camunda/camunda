/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34;

import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate33To34.indices.SingleProcessReportIndexV5Old;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;

public class AbstractUpgrade33IT extends AbstractUpgradeIT {
  protected static final String FROM_VERSION = "3.3.0";

  protected static final SingleProcessReportIndexV5Old PROCESS_REPORT_INDEX = new SingleProcessReportIndexV5Old();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(
      Arrays.asList(
        PROCESS_REPORT_INDEX
      )
    );
    setMetadataVersion(FROM_VERSION);
  }

}
