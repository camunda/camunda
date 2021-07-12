/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate34To35;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate34To35.indices.DecisionDefinitionIndexV4Old;
import org.camunda.optimize.upgrade.migrate34To35.indices.EventProcessDefinitionIndexV3Old;
import org.camunda.optimize.upgrade.migrate34To35.indices.EventProcessInstanceIndexV6Old;
import org.camunda.optimize.upgrade.migrate34To35.indices.ProcessDefinitionIndexV4Old;
import org.camunda.optimize.upgrade.migrate34To35.indices.ProcessInstanceIndexV6Old;
import org.camunda.optimize.upgrade.migrate34To35.indices.SingleDecisionReportIndexV6Old;
import org.camunda.optimize.upgrade.migrate34To35.indices.SingleProcessReportIndexV6Old;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;

public class AbstractUpgrade34IT extends AbstractUpgradeIT {
  protected static final String FROM_VERSION = "3.4.0";
  protected static final String EVENT_PROCESS_INSTANCE_INDEX_ID_1 = "firsteventprocessinstanceindexid";
  protected static final String EVENT_PROCESS_INSTANCE_INDEX_ID_2 = "secondeventprocessinstanceindexid";
  protected static final String PROCESS_INSTANCE_INDEX_ID_1 = "firstprocessinstanceindex";
  protected static final String PROCESS_INSTANCE_INDEX_ID_2 = "secondprocessinstanceindex";

  protected static final ProcessDefinitionIndexV4Old PROCESS_DEFINITION_INDEX = new ProcessDefinitionIndexV4Old();
  protected static final DecisionDefinitionIndexV4Old DECISION_DEFINITION_INDEX = new DecisionDefinitionIndexV4Old();
  protected static final EventProcessDefinitionIndexV3Old EVENT_PROCESS_DEFINITION_INDEX =
    new EventProcessDefinitionIndexV3Old();
  protected static final EventProcessInstanceIndexV6Old EVENT_PROCESS_INSTANCE_INDEX_1 =
    new EventProcessInstanceIndexV6Old(EVENT_PROCESS_INSTANCE_INDEX_ID_1);
  protected static final EventProcessInstanceIndexV6Old EVENT_PROCESS_INSTANCE_INDEX_2 =
    new EventProcessInstanceIndexV6Old(EVENT_PROCESS_INSTANCE_INDEX_ID_2);
  protected static final ProcessInstanceIndexV6Old PROCESS_INSTANCE_INDEX_1 =
    new ProcessInstanceIndexV6Old(PROCESS_INSTANCE_INDEX_ID_1);
  protected static final ProcessInstanceIndexV6Old PROCESS_INSTANCE_INDEX_2 =
    new ProcessInstanceIndexV6Old(PROCESS_INSTANCE_INDEX_ID_2);
  protected static final SingleProcessReportIndexV6Old SINGLE_PROCESS_REPORT_INDEX =
    new SingleProcessReportIndexV6Old();
  protected static final SingleDecisionReportIndexV6Old SINGLE_DECISION_REPORT_INDEX =
    new SingleDecisionReportIndexV6Old();
  protected static final DashboardIndex DASHBOARD_INDEX = new DashboardIndex();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(
      Arrays.asList(
        PROCESS_DEFINITION_INDEX,
        DECISION_DEFINITION_INDEX,
        EVENT_PROCESS_DEFINITION_INDEX,
        SINGLE_PROCESS_REPORT_INDEX,
        SINGLE_DECISION_REPORT_INDEX,
        DASHBOARD_INDEX
      )
    );
    createIndicesWithAdditionalReadOnlyAliases(
      ImmutableMap.of(
        PROCESS_INSTANCE_INDEX_1, Collections.singleton(PROCESS_INSTANCE_MULTI_ALIAS),
        PROCESS_INSTANCE_INDEX_2, Collections.singleton(PROCESS_INSTANCE_MULTI_ALIAS),
        EVENT_PROCESS_INSTANCE_INDEX_1, Collections.singleton(PROCESS_INSTANCE_MULTI_ALIAS),
        EVENT_PROCESS_INSTANCE_INDEX_2, Collections.singleton(PROCESS_INSTANCE_MULTI_ALIAS)
      )
    );
    setMetadataVersion(FROM_VERSION);
  }

  protected boolean indexExists(final IndexMappingCreator index) {
    final GetIndexRequest request = new GetIndexRequest(indexNameService.getOptimizeIndexNameWithVersion(index));
    try {
      return prefixAwareClient.exists(request);
    } catch (IOException e) {
      final String message = String.format(
        "Could not check if [%s] index exists.", index.getIndexName()
      );
      throw new OptimizeRuntimeException(message, e);
    }
  }

}
