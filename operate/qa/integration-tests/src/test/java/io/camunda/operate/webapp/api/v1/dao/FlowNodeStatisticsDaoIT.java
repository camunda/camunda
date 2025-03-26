/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.webapps.schema.entities.AbstractExporterEntity.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeStatistics;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class FlowNodeStatisticsDaoIT extends OperateSearchAbstractIT {

  private static final Long PROCESS_INSTANCE_KEY = 2251799813685251L;
  private static final Long PROCESS_DEFINITION_KEY = 2251799813685249L;
  @Autowired private FlowNodeStatisticsDao dao;

  @Autowired
  @Qualifier("operateFlowNodeInstanceTemplate")
  private FlowNodeInstanceTemplate flowNodeInstanceIndex;

  @Override
  public void runAdditionalBeforeAllSetup() throws Exception {
    final String indexName = flowNodeInstanceIndex.getFullQualifiedName();
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685254L)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setFlowNodeId("start")
            .setType(FlowNodeType.START_EVENT)
            .setState(FlowNodeState.COMPLETED)
            .setIncident(false)
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685256L)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setFlowNodeId("ExclusiveGateway_05d8jf3")
            .setType(FlowNodeType.PARALLEL_GATEWAY)
            .setState(FlowNodeState.COMPLETED)
            .setIncident(false)
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685258L)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setFlowNodeId("taskD")
            .setType(FlowNodeType.SERVICE_TASK)
            .setState(FlowNodeState.ACTIVE)
            .setIncident(false)
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685260L)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setFlowNodeId("taskA")
            .setType(FlowNodeType.SERVICE_TASK)
            .setState(FlowNodeState.ACTIVE)
            .setIncident(true)
            .setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-flow*");
  }

  @Test
  public void shouldReturnFlowNodeStatistics() {
    final List<FlowNodeStatistics> flowNodeStatistics =
        dao.getFlowNodeStatisticsForProcessInstance(PROCESS_INSTANCE_KEY);

    assertThat(flowNodeStatistics).hasSize(4);
    assertThat(
            flowNodeStatistics.stream()
                .filter(x -> x.getActivityId().equals("ExclusiveGateway_05d8jf3"))
                .findFirst()
                .orElseThrow()
                .getCompleted())
        .isEqualTo(1);
    assertThat(
            flowNodeStatistics.stream()
                .filter(x -> x.getActivityId().equals("start"))
                .findFirst()
                .orElseThrow()
                .getCompleted())
        .isEqualTo(1);
    assertThat(
            flowNodeStatistics.stream()
                .filter(x -> x.getActivityId().equals("taskA"))
                .findFirst()
                .orElseThrow()
                .getIncidents())
        .isEqualTo(1);
    assertThat(
            flowNodeStatistics.stream()
                .filter(x -> x.getActivityId().equals("taskD"))
                .findFirst()
                .orElseThrow()
                .getActive())
        .isEqualTo(1);
  }
}
