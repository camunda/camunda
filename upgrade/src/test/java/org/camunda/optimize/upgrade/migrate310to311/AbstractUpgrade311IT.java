/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate310to311;

import lombok.SneakyThrows;
import org.camunda.optimize.service.es.schema.index.CollectionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.index.PositionBasedImportIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate310to311.indices.CombinedDecisionReportIndexV4;
import org.camunda.optimize.upgrade.migrate310to311.indices.DashboardIndexV7;
import org.camunda.optimize.upgrade.migrate310to311.indices.SingleDecisionReportIndexV9;
import org.camunda.optimize.upgrade.migrate310to311.indices.SingleProcessReportIndexV10;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;

public abstract class AbstractUpgrade311IT extends AbstractUpgradeIT {

  protected static final String FROM_VERSION = "3.10.0";
  protected static final String TO_VERSION = "3.11.0";
  protected final SingleProcessReportIndexV10 SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndexV10();
  protected final SingleDecisionReportIndexV9 SINGLE_DECISION_REPORT_INDEX = new SingleDecisionReportIndexV9();
  protected final CombinedDecisionReportIndexV4 COMBINED_REPORT_INDEX = new CombinedDecisionReportIndexV4();
  protected final DashboardIndexV7 DASHBOARD_INDEX = new DashboardIndexV7();
  protected final ProcessDefinitionIndex PROCESS_DEFINITION_INDEX = new ProcessDefinitionIndex();
  protected final EventProcessDefinitionIndex EVENT_PROCESS_DEFINITION_INDEX = new EventProcessDefinitionIndex();
  protected final ProcessInstanceIndex PROCESS_INSTANCE_INDEX_REVIEW_INVOICE = new ProcessInstanceIndex("reviewinvoice");
  protected final ProcessInstanceIndex PROCESS_INSTANCE_INDEX_ONLY_INCIDENTS_PROCESS = new ProcessInstanceIndex(
    "onlyincidentsprocess");
  protected final ProcessInstanceIndex PROCESS_INSTANCE_INDEX_ALWAYS_COMPLETING_PROCESS = new ProcessInstanceIndex(
    "always-completing-process");
  protected final CollectionIndex COLLECTION_INDEX = new CollectionIndex();
  protected final PositionBasedImportIndex POSITION_BASED_IMPORT_INDEX = new PositionBasedImportIndex();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(List.of(
      SINGLE_PROCESS_REPORT_INDEX,
      SINGLE_DECISION_REPORT_INDEX,
      COMBINED_REPORT_INDEX,
      DASHBOARD_INDEX,
      PROCESS_DEFINITION_INDEX,
      EVENT_PROCESS_DEFINITION_INDEX,
      PROCESS_INSTANCE_INDEX_REVIEW_INVOICE,
      PROCESS_INSTANCE_INDEX_ONLY_INCIDENTS_PROCESS,
      PROCESS_INSTANCE_INDEX_ALWAYS_COMPLETING_PROCESS,
      COLLECTION_INDEX,
      POSITION_BASED_IMPORT_INDEX
    ));
    addProcessInstanceMultiAlias();
    setMetadataVersion(FROM_VERSION);
  }

  @SneakyThrows
  private void addProcessInstanceMultiAlias() {
    final IndicesAliasesRequest indicesAliasesRequest = new IndicesAliasesRequest();
    final IndicesAliasesRequest.AliasActions aliasAction =
      new IndicesAliasesRequest.AliasActions(IndicesAliasesRequest.AliasActions.Type.ADD)
        .indices(
          indexNameService.getOptimizeIndexNameWithVersion(PROCESS_INSTANCE_INDEX_REVIEW_INVOICE),
          indexNameService.getOptimizeIndexNameWithVersion(PROCESS_INSTANCE_INDEX_ONLY_INCIDENTS_PROCESS),
          indexNameService.getOptimizeIndexNameWithVersion(PROCESS_INSTANCE_INDEX_ALWAYS_COMPLETING_PROCESS)
        )
        .writeIndex(false)
        .aliases(indexNameService.getOptimizeIndexAliasForIndex(PROCESS_INSTANCE_MULTI_ALIAS));
    indicesAliasesRequest.addAliasAction(aliasAction);
    prefixAwareClient.getHighLevelClient().indices().updateAliases(indicesAliasesRequest, prefixAwareClient.requestOptions());
  }

  protected void performUpgrade() {
    final List<UpgradePlan> upgradePlans =
      new UpgradePlanRegistry(upgradeDependencies).getSequentialUpgradePlansToTargetVersion(TO_VERSION);
    upgradePlans.forEach(plan -> upgradeProcedure.performUpgrade(plan));
  }

}
