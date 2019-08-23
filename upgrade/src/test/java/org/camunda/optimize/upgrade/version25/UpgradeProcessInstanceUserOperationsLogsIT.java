/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version25;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom25To26;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.version25.indexes.Version25CollectionIndex;
import org.camunda.optimize.upgrade.version25.indexes.Version25ProcessInstanceIndex;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

public class UpgradeProcessInstanceUserOperationsLogsIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.5.0";

  private static final DecisionDefinitionIndex DECISION_DEFINITION_INDEX_OBJECT = new DecisionDefinitionIndex();
  private static final SingleDecisionReportIndex SINGLE_DECISION_REPORT_INDEX = new SingleDecisionReportIndex();
  private static final SingleProcessReportIndex SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndex();
  private static final Version25ProcessInstanceIndex PROCESS_INSTANCE_INDEX = new Version25ProcessInstanceIndex();
  private static final Version25CollectionIndex COLLECTION_INDEX = new Version25CollectionIndex();

  private static final String PROC_INST_COMPLETED_WITH_UNCLAIMED_USER_TASK = "c42b9f63-c34a-11e9-938d-0242ac170003";

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_INDEX,
      DECISION_DEFINITION_INDEX_OBJECT,
      SINGLE_DECISION_REPORT_INDEX,
      SINGLE_PROCESS_REPORT_INDEX,
      PROCESS_INSTANCE_INDEX,
      COLLECTION_INDEX
    ));

    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/process_instance/25-process-instance-bulk");
  }

  @Test
  public void userOperationsAreRemovedFromUserTasks() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final GetResponse getResponse = getProcessInstance(PROC_INST_COMPLETED_WITH_UNCLAIMED_USER_TASK);
    assertThat(getResponse.isExists(), is(true));
    assertThat(getResponse.getSourceAsString(), not(containsString("userOperations")));
  }

  @SneakyThrows
  private GetResponse getProcessInstance(final String id) {
    return prefixAwareClient.get(
      new GetRequest(
        ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME,
        ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME,
        id
      ),
      RequestOptions.DEFAULT
    );
  }

}
