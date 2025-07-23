/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.sequenceflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.SequenceFlowDbModel;
import io.camunda.db.rdbms.write.domain.SequenceFlowDbModel.Builder;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.query.SequenceFlowQuery;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class SequenceFlowIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldCreateSequenceFlow(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var sequenceFlowReader = rdbmsService.getSequenceFlowReader();
    final var sequenceFlowWriter = rdbmsWriter.getSequenceFlowWriter();

    // when
    sequenceFlowWriter.create(
        new SequenceFlowDbModel.Builder()
            .flowNodeId("node1")
            .processInstanceKey(1L)
            .processDefinitionKey(11L)
            .processDefinitionId("bpmn1")
            .tenantId("tenant1")
            .partitionId(PARTITION_ID.intValue())
            .historyCleanupDate(NOW)
            .build());
    rdbmsWriter.flush();

    // then
    final var items =
        sequenceFlowReader
            .search(SequenceFlowQuery.of(q -> q.filter(f -> f.processInstanceKey(1L))))
            .items();
    assertThat(items)
        .containsExactly(
            new SequenceFlowEntity.Builder()
                .sequenceFlowId("1_node1")
                .flowNodeId("node1")
                .processInstanceKey(1L)
                .processDefinitionKey(11L)
                .processDefinitionId("bpmn1")
                .tenantId("tenant1")
                .build());
  }

  @TestTemplate
  public void shouldSearchSequenceFlow(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var sequenceFlowReader = rdbmsService.getSequenceFlowReader();
    final var sequenceFlowWriter = rdbmsWriter.getSequenceFlowWriter();
    sequenceFlowWriter.create(
        new Builder()
            .flowNodeId("node21")
            .processInstanceKey(21L)
            .processDefinitionKey(11L)
            .processDefinitionId("bpmn21")
            .tenantId("tenant1")
            .partitionId(PARTITION_ID.intValue())
            .historyCleanupDate(NOW)
            .build());
    sequenceFlowWriter.create(
        new Builder()
            .flowNodeId("node22")
            .processInstanceKey(22L)
            .processDefinitionKey(11L)
            .processDefinitionId("bpmn22")
            .tenantId("tenant1")
            .partitionId(PARTITION_ID.intValue())
            .historyCleanupDate(NOW)
            .build());
    rdbmsWriter.flush();

    // when
    final var actual =
        sequenceFlowReader
            .search(SequenceFlowQuery.of(q -> q.filter(f -> f.processInstanceKey(22L))))
            .items();

    // then
    assertThat(actual)
        .containsExactly(
            new SequenceFlowEntity.Builder()
                .sequenceFlowId("22_node22")
                .flowNodeId("node22")
                .processInstanceKey(22L)
                .processDefinitionKey(11L)
                .processDefinitionId("bpmn22")
                .tenantId("tenant1")
                .build());
  }

  @TestTemplate
  public void shouldCreateSequenceFlowIfNotExists(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var sequenceFlowReader = rdbmsService.getSequenceFlowReader();
    final var sequenceFlowWriter = rdbmsWriter.getSequenceFlowWriter();

    // when
    sequenceFlowWriter.createIfNotExists(
        new SequenceFlowDbModel.Builder()
            .flowNodeId("node3")
            .processInstanceKey(3L)
            .processDefinitionKey(11L)
            .processDefinitionId("bpmn3")
            .tenantId("tenant1")
            .partitionId(PARTITION_ID.intValue())
            .historyCleanupDate(NOW)
            .build());
    rdbmsWriter.flush();

    // then
    final var items =
        sequenceFlowReader
            .search(SequenceFlowQuery.of(q -> q.filter(f -> f.processInstanceKey(3L))))
            .items();
    assertThat(items)
        .containsExactly(
            new SequenceFlowEntity.Builder()
                .sequenceFlowId("3_node3")
                .flowNodeId("node3")
                .processInstanceKey(3L)
                .processDefinitionKey(11L)
                .processDefinitionId("bpmn3")
                .tenantId("tenant1")
                .build());
  }

  @TestTemplate
  public void shouldNotCreateSequenceFlowIfExists(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var sequenceFlowReader = rdbmsService.getSequenceFlowReader();
    final var sequenceFlowWriter = rdbmsWriter.getSequenceFlowWriter();
    final var dbModel =
        new Builder()
            .flowNodeId("node4")
            .processInstanceKey(4L)
            .processDefinitionKey(11L)
            .processDefinitionId("bpmn4")
            .tenantId("tenant1")
            .partitionId(PARTITION_ID.intValue())
            .historyCleanupDate(NOW)
            .build();
    sequenceFlowWriter.create(dbModel);
    rdbmsWriter.flush();

    // when
    sequenceFlowWriter.createIfNotExists(dbModel);
    rdbmsWriter.flush();

    // then
    final var items =
        sequenceFlowReader
            .search(SequenceFlowQuery.of(q -> q.filter(f -> f.processInstanceKey(4L))))
            .items();
    assertThat(items)
        .containsExactly(
            new SequenceFlowEntity.Builder()
                .sequenceFlowId("4_node4")
                .flowNodeId("node4")
                .processInstanceKey(4L)
                .processDefinitionKey(11L)
                .processDefinitionId("bpmn4")
                .tenantId("tenant1")
                .build());
  }

  @TestTemplate
  public void shouldDeleteSequenceFlow(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var sequenceFlowReader = rdbmsService.getSequenceFlowReader();
    final var sequenceFlowWriter = rdbmsWriter.getSequenceFlowWriter();

    final var dbModel =
        new Builder()
            .flowNodeId("nodeDel")
            .processInstanceKey(99L)
            .processDefinitionKey(9L)
            .processDefinitionId("bpmnDel")
            .tenantId("tenantX")
            .partitionId(PARTITION_ID.intValue())
            .historyCleanupDate(NOW)
            .build();
    sequenceFlowWriter.create(dbModel);
    rdbmsWriter.flush();

    assertThat(
            sequenceFlowReader
                .search(SequenceFlowQuery.of(q -> q.filter(f -> f.processInstanceKey(99L))))
                .items())
        .isNotEmpty();

    // when
    sequenceFlowWriter.delete(dbModel);
    rdbmsWriter.flush();

    // then
    final var itemsAfterDelete =
        sequenceFlowReader
            .search(SequenceFlowQuery.of(q -> q.filter(f -> f.processInstanceKey(99L))))
            .items();
    assertThat(itemsAfterDelete).isEmpty();
  }
}
