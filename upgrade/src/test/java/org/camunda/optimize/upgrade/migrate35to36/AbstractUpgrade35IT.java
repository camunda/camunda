/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate35to36;

import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate35to36.indices.EventProcessInstanceIndexV7Old;
import org.camunda.optimize.upgrade.migrate35to36.indices.ProcessInstanceIndexV7Old;
import org.camunda.optimize.upgrade.migrate35to36.indices.SingleDecisionReportIndexV7Old;
import org.camunda.optimize.upgrade.migrate35to36.indices.SingleProcessReportIndexV7Old;
import org.elasticsearch.common.collect.List;
import org.junit.jupiter.api.BeforeEach;

import java.util.Collections;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;

public class AbstractUpgrade35IT extends AbstractUpgradeIT {
  protected static final String FROM_VERSION = "3.5.0";
  protected static final String EVENT_PROCESS_INSTANCE_INDEX_ID_1 = "eventprocess_1";
  protected static final String EVENT_PROCESS_INSTANCE_INDEX_ID_2 = "eventprocess_2";
  protected static final String PROCESS_INSTANCE_INDEX_ID_1 = "process_1";
  protected static final String PROCESS_INSTANCE_INDEX_ID_2 = "process_2";

  protected static final EventProcessInstanceIndexV7Old EVENT_PROCESS_INSTANCE_INDEX_1 =
    new EventProcessInstanceIndexV7Old(EVENT_PROCESS_INSTANCE_INDEX_ID_1);
  protected static final EventProcessInstanceIndexV7Old EVENT_PROCESS_INSTANCE_INDEX_2 =
    new EventProcessInstanceIndexV7Old(EVENT_PROCESS_INSTANCE_INDEX_ID_2);
  protected static final ProcessInstanceIndexV7Old PROCESS_INSTANCE_INDEX_1 =
    new ProcessInstanceIndexV7Old(PROCESS_INSTANCE_INDEX_ID_1);
  protected static final ProcessInstanceIndexV7Old PROCESS_INSTANCE_INDEX_2 =
    new ProcessInstanceIndexV7Old(PROCESS_INSTANCE_INDEX_ID_2);
  protected static final SingleProcessReportIndexV7Old SINGLE_PROCESS_REPORT_INDEX =
    new SingleProcessReportIndexV7Old();
  protected static final SingleDecisionReportIndexV7Old SINGLE_DECISION_REPORT_INDEX =
    new SingleDecisionReportIndexV7Old();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(List.of(
      SINGLE_PROCESS_REPORT_INDEX,
      SINGLE_DECISION_REPORT_INDEX
    ));
    createIndicesWithAdditionalReadOnlyAliases(Map.of(
      PROCESS_INSTANCE_INDEX_1, Collections.singleton(PROCESS_INSTANCE_MULTI_ALIAS),
      PROCESS_INSTANCE_INDEX_2, Collections.singleton(PROCESS_INSTANCE_MULTI_ALIAS),
      EVENT_PROCESS_INSTANCE_INDEX_1, Collections.singleton(PROCESS_INSTANCE_MULTI_ALIAS),
      EVENT_PROCESS_INSTANCE_INDEX_2, Collections.singleton(PROCESS_INSTANCE_MULTI_ALIAS)
    ));
    setMetadataVersion(FROM_VERSION);
  }
}
