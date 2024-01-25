/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeStatistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

public class FlowNodeStatisticsDaoIT extends OperateSearchAbstractIT {

  @Autowired
  private FlowNodeStatisticsDao dao;

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceIndex;

  private static final Long PROCESS_INSTANCE_KEY = 2251799813685251L;
  private static final Long PROCESS_DEFINITION_KEY = 2251799813685249L;

  @Override
  public void runAdditionalBeforeAllSetup() throws Exception {
    String indexName = flowNodeInstanceIndex.getFullQualifiedName();
    testSearchRepository.createOrUpdateDocumentFromObject(indexName, new FlowNodeInstance().setKey(2251799813685254L).setProcessInstanceKey(PROCESS_INSTANCE_KEY)
        .setProcessDefinitionKey(PROCESS_DEFINITION_KEY).setStartDate("2024-01-22T22:42:01.058+0000")
        .setEndDate("2024-01-22T22:42:01.058+0000").setFlowNodeId("start").setType("START_EVENT")
        .setState("COMPLETED").setIncident(false).setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(indexName, new FlowNodeInstance().setKey(2251799813685256L).setProcessInstanceKey(PROCESS_INSTANCE_KEY)
        .setProcessDefinitionKey(PROCESS_DEFINITION_KEY).setStartDate("2024-01-22T22:42:01.058+0000")
        .setEndDate("2024-01-22T22:42:01.058+0000").setFlowNodeId("ExclusiveGateway_05d8jf3").setType("PARALLEL_GATEWAY")
        .setState("COMPLETED").setIncident(false).setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(indexName, new FlowNodeInstance().setKey(2251799813685258L).setProcessInstanceKey(PROCESS_INSTANCE_KEY)
        .setProcessDefinitionKey(PROCESS_DEFINITION_KEY).setStartDate("2024-01-22T22:42:01.058+0000")
        .setEndDate("2024-01-22T22:42:01.058+0000").setFlowNodeId("taskD").setType("SERVICE_TASK")
        .setState("ACTIVE").setIncident(false).setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(indexName, new FlowNodeInstance().setKey(2251799813685260L).setProcessInstanceKey(PROCESS_INSTANCE_KEY)
        .setProcessDefinitionKey(PROCESS_DEFINITION_KEY).setStartDate("2024-01-22T22:42:01.058+0000")
        .setEndDate("2024-01-22T22:42:01.058+0000").setFlowNodeId("taskA").setType("SERVICE_TASK")
        .setState("ACTIVE").setIncident(true).setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-flow*");
  }

  @Test
  public void shouldReturnFlowNodeStatistics() {
    List<FlowNodeStatistics> flowNodeStatistics = dao.getFlowNodeStatisticsForProcessInstance(PROCESS_INSTANCE_KEY);

    assertThat(flowNodeStatistics).hasSize(4);
    assertThat(flowNodeStatistics.stream().filter(x -> x.getActivityId().equals("ExclusiveGateway_05d8jf3")).findFirst().orElseThrow().getCompleted()).isEqualTo(1);
    assertThat(flowNodeStatistics.stream().filter(x -> x.getActivityId().equals("start")).findFirst().orElseThrow().getCompleted()).isEqualTo(1);
    assertThat(flowNodeStatistics.stream().filter(x -> x.getActivityId().equals("taskA")).findFirst().orElseThrow().getIncidents()).isEqualTo(1);
    assertThat(flowNodeStatistics.stream().filter(x -> x.getActivityId().equals("taskD")).findFirst().orElseThrow().getActive()).isEqualTo(1);
  }
}
