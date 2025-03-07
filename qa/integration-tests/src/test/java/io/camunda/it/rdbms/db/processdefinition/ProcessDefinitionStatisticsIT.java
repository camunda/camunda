/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.processdefinition;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveRandomProcessInstance;
import static org.assertj.core.api.Assertions.*;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.ProcessDefinitionFlowNodeStatisticsEntity;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import java.util.function.Consumer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessDefinitionStatisticsIT {

  public static final int PARTITION_ID = 0;

  @TestTemplate
  public void shouldCollectStatisticsAndApplyFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var processDefinitionReader = rdbmsService.getProcessDefinitionReader();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var pd1 = ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriter, b -> b);
    final var pi1 =
        createAndSaveRandomProcessInstance(
            rdbmsWriter, b -> b.processDefinitionKey(pd1.processDefinitionKey()));
    final var pi2 =
        createAndSaveRandomProcessInstance(
            rdbmsWriter, b -> b.processDefinitionKey(pd1.processDefinitionKey()));
    createAndSaveFlowNodeInstance(
        rdbmsWriter,
        b ->
            b.flowNodeId("node1")
                .processInstanceKey(pi1.processInstanceKey())
                .state(FlowNodeState.ACTIVE),
        2);
    createAndSaveFlowNodeInstance(
        rdbmsWriter,
        b ->
            b.flowNodeId("node1")
                .processInstanceKey(pi2.processInstanceKey())
                .state(FlowNodeState.COMPLETED),
        3);
    createAndSaveFlowNodeInstance(
        rdbmsWriter,
        b ->
            b.flowNodeId("node1")
                .processInstanceKey(pi2.processInstanceKey())
                .state(FlowNodeState.TERMINATED),
        4);
    createAndSaveFlowNodeInstance(
        rdbmsWriter,
        b ->
            b.flowNodeId("node2")
                .processInstanceKey(pi2.processInstanceKey())
                .numSubprocessIncidents(2L)
                .state(FlowNodeState.ACTIVE),
        3);
    createAndSaveFlowNodeInstance(
        rdbmsWriter,
        b ->
            b.flowNodeId("node2")
                .processInstanceKey(pi2.processInstanceKey())
                .numSubprocessIncidents(1L)
                .state(FlowNodeState.TERMINATED));

    // when
    final var actual =
        processDefinitionReader.statistics(
            new ProcessDefinitionStatisticsFilter.Builder(pd1.processDefinitionKey()).build());

    // then
    assertThat(actual).hasSize(2);
    final var node1 =
        actual.stream().filter(s -> s.flowNodeId().equals("node1")).findFirst().orElseThrow();
    assertStatisticsResult(node1, 2L, 4L, 3L, 0L);
    final var node2 =
        actual.stream().filter(s -> s.flowNodeId().equals("node2")).findFirst().orElseThrow();
    assertStatisticsResult(node2, 3L, 1L, 0L, 4L);
  }

  private void createAndSaveFlowNodeInstance(
      final RdbmsWriter rdbmsWriter,
      final Consumer<FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder> fn,
      final int instanceCount) {
    for (int i = 0; i < instanceCount; i++) {
      final var key = nextKey();
      final var builder = new FlowNodeInstanceDbModelBuilder().flowNodeInstanceKey(key);
      fn.accept(builder);
      rdbmsWriter.getFlowNodeInstanceWriter().create(builder.build());
    }
    rdbmsWriter.flush();
  }

  private void createAndSaveFlowNodeInstance(
      final RdbmsWriter rdbmsWriter,
      final Consumer<FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder> fn) {
    createAndSaveFlowNodeInstance(rdbmsWriter, fn, 1);
  }

  private void assertStatisticsResult(
      final ProcessDefinitionFlowNodeStatisticsEntity statistics,
      final long active,
      final long canceled,
      final long completed,
      final long incidents) {
    assertThat(statistics.active()).isEqualTo(active);
    assertThat(statistics.canceled()).isEqualTo(canceled);
    assertThat(statistics.completed()).isEqualTo(completed);
    assertThat(statistics.incidents()).isEqualTo(incidents);
  }
}
