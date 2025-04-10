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
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.entities.SequenceFlow;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.entities.SequenceFlowEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SequenceFlowDaoIT extends OperateSearchAbstractIT {

  @Autowired private SequenceFlowDao dao;

  @Autowired private SequenceFlowTemplate sequenceFlowIndex;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    final String indexName = sequenceFlowIndex.getFullQualifiedName();
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new SequenceFlowEntity()
            .setId("2251799813685253_sequenceFlow_01")
            .setActivityId("sequenceFlow_01")
            .setProcessInstanceKey(2251799813685253L)
            .setTenantId(DEFAULT_TENANT_ID));
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new SequenceFlowEntity()
            .setId("2251799813685253_sequenceFlow_02")
            .setActivityId("sequenceFlow_02")
            .setProcessInstanceKey(2251799813685253L)
            .setTenantId(DEFAULT_TENANT_ID));
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new SequenceFlowEntity()
            .setId("2251799813685253_sequenceFlow_03")
            .setActivityId("sequenceFlow_03")
            .setProcessInstanceKey(2251799813685253L)
            .setTenantId(DEFAULT_TENANT_ID));
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new SequenceFlowEntity()
            .setId("2251799813685253_sequenceFlow_04")
            .setActivityId("sequenceFlow_04")
            .setProcessInstanceKey(2251799813685253L)
            .setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-sequence*");
  }

  @Test
  public void shouldReturnSequenceFlows() {
    final Results<SequenceFlow> sequenceFlowResults = dao.search(new Query<>());

    assertThat(sequenceFlowResults.getItems()).hasSize(4);
    assertThat(sequenceFlowResults.getItems())
        .extracting(SequenceFlow.ACTIVITY_ID)
        .containsExactlyInAnyOrder(
            "sequenceFlow_01", "sequenceFlow_02", "sequenceFlow_03", "sequenceFlow_04");
  }

  @Test
  public void shouldFilterSequenceFlows() {
    final Results<SequenceFlow> sequenceFlowResults =
        dao.search(
            new Query<SequenceFlow>()
                .setFilter(new SequenceFlow().setActivityId("sequenceFlow_01")));

    assertThat(sequenceFlowResults.getItems()).hasSize(1);

    assertThat(sequenceFlowResults.getItems().get(0).getActivityId()).isEqualTo("sequenceFlow_01");
  }

  @Test
  public void shouldSortSequenceFlowsDesc() {
    final Results<SequenceFlow> sequenceFlowResults =
        dao.search(
            new Query<SequenceFlow>()
                .setSort(Query.Sort.listOf(SequenceFlow.ACTIVITY_ID, Query.Sort.Order.DESC)));

    assertThat(sequenceFlowResults.getItems()).hasSize(4);
    assertThat(sequenceFlowResults.getItems().get(0).getActivityId()).isEqualTo("sequenceFlow_04");
    assertThat(sequenceFlowResults.getItems().get(1).getActivityId()).isEqualTo("sequenceFlow_03");
    assertThat(sequenceFlowResults.getItems().get(2).getActivityId()).isEqualTo("sequenceFlow_02");
    assertThat(sequenceFlowResults.getItems().get(3).getActivityId()).isEqualTo("sequenceFlow_01");
  }

  @Test
  public void shouldSortSequenceFlowsAsc() {
    final Results<SequenceFlow> sequenceFlowResults =
        dao.search(
            new Query<SequenceFlow>()
                .setSort(Query.Sort.listOf(SequenceFlow.ACTIVITY_ID, Query.Sort.Order.ASC)));

    assertThat(sequenceFlowResults.getItems()).hasSize(4);
    assertThat(sequenceFlowResults.getItems().get(0).getActivityId()).isEqualTo("sequenceFlow_01");
    assertThat(sequenceFlowResults.getItems().get(1).getActivityId()).isEqualTo("sequenceFlow_02");
    assertThat(sequenceFlowResults.getItems().get(2).getActivityId()).isEqualTo("sequenceFlow_03");
    assertThat(sequenceFlowResults.getItems().get(3).getActivityId()).isEqualTo("sequenceFlow_04");
  }

  @Test
  public void shouldPageSequenceFlows() {
    Results<SequenceFlow> sequenceFlowResults =
        dao.search(
            new Query<SequenceFlow>()
                .setSort(Query.Sort.listOf(SequenceFlow.ACTIVITY_ID, Query.Sort.Order.DESC))
                .setSize(3));

    assertThat(sequenceFlowResults.getItems()).hasSize(3);
    assertThat(sequenceFlowResults.getTotal()).isEqualTo(4);

    assertThat(sequenceFlowResults.getItems().get(0).getActivityId()).isEqualTo("sequenceFlow_04");
    assertThat(sequenceFlowResults.getItems().get(1).getActivityId()).isEqualTo("sequenceFlow_03");
    assertThat(sequenceFlowResults.getItems().get(2).getActivityId()).isEqualTo("sequenceFlow_02");

    final Object[] searchAfter = sequenceFlowResults.getSortValues();
    assertThat(String.valueOf(sequenceFlowResults.getItems().get(2).getActivityId()))
        .isEqualTo(String.valueOf(searchAfter[0]));

    sequenceFlowResults =
        dao.search(
            new Query<SequenceFlow>()
                .setSort(Query.Sort.listOf(SequenceFlow.ACTIVITY_ID, Query.Sort.Order.DESC))
                .setSize(3)
                .setSearchAfter(searchAfter));

    assertThat(sequenceFlowResults.getItems()).hasSize(1);
    assertThat(sequenceFlowResults.getTotal()).isEqualTo(4);

    assertThat(sequenceFlowResults.getItems().get(0).getActivityId()).isEqualTo("sequenceFlow_01");
  }
}
