/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate37to38;

import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate37to38.indices.PositionBasedImportIndexOld;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

public abstract class AbstractUpgrade37IT extends AbstractUpgradeIT {

  protected static final String FROM_VERSION = "3.7.0";
  protected static final String TO_VERSION = "3.8.0";

  protected static final PositionBasedImportIndexOld POSITION_BASED_INDEX = new PositionBasedImportIndexOld();
  protected static final SingleProcessReportIndex SINGLE_PROCESS_REPORT_INDEX =
    new SingleProcessReportIndex();
  protected static final SingleDecisionReportIndex SINGLE_DECISION_REPORT_INDEX =
    new SingleDecisionReportIndex();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(List.of(
      POSITION_BASED_INDEX,
      SINGLE_PROCESS_REPORT_INDEX,
      SINGLE_DECISION_REPORT_INDEX
    ));
    setMetadataVersion(FROM_VERSION);
  }

}
