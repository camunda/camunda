/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import org.assertj.core.util.Lists;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.service.es.schema.index.index.ImportIndexIndex;
import org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.version30.indices.AlertIndexV2;
import org.camunda.optimize.upgrade.version30.indices.ProcessInstanceIndexV4;
import org.camunda.optimize.upgrade.version30.indices.SingleDecisionReportIndexV2;
import org.camunda.optimize.upgrade.version30.indices.SingleProcessReportIndexV2;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractUpgrade30IT extends AbstractUpgradeIT {

  private static final SingleProcessReportIndexV2 SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndexV2();
  private static final SingleDecisionReportIndexV2 SINGLE_DECISION_REPORT_INDEX = new SingleDecisionReportIndexV2();
  private static final CombinedReportIndex COMBINED_REPORT_INDEX = new CombinedReportIndex();
  protected static final ImportIndexIndex IMPORT_INDEX_INDEX = new ImportIndexIndex();
  protected static final TimestampBasedImportIndex TIMESTAMP_BASED_IMPORT_INDEX = new TimestampBasedImportIndex();
  private static final AlertIndexV2 ALERT_INDEX = new AlertIndexV2();
  private static final ProcessInstanceIndexV4 PROCESS_INSTANCE_INDEX = new ProcessInstanceIndexV4();
  private static final EventProcessInstanceIndexV4 EVENT_PROCESS_INSTANCE_INDEX = new EventProcessInstanceIndexV4();
  private static final EventProcessPublishStateIndex EVENT_PROCESS_PUBLISH_STATE_INDEX =
    new EventProcessPublishStateIndex();

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
      EVENT_PROCESS_PUBLISH_STATE_INDEX
    ));
    setMetadataIndexVersion(FROM_VERSION);
  }

}
