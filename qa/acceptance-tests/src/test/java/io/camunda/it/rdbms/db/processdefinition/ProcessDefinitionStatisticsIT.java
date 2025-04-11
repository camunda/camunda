/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.processdefinition;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static org.assertj.core.api.Assertions.*;

import io.camunda.db.rdbms.read.service.ProcessDefinitionReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessDefinitionStatisticsIT {

  public static final int PARTITION_ID = 0;
  public static final String NODE1 = "node1";
  public static final String NODE2 = "node2";

  @TestTemplate
  public void shouldCollectStatisticsWithEmptyFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var processDefinitionReader = rdbmsService.getProcessDefinitionReader();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var pd1 = ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriter, b -> b);
    createActive(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    createActive(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    createCompleted(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    createCompleted(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    createCompleted(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    createCanceled(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    createCanceled(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    createIncident(rdbmsWriter, pd1.processDefinitionKey(), NODE2);
    createPIAndFNI(
        rdbmsWriter,
        pd1.processDefinitionKey(),
        b -> b.flowNodeId(NODE2).numSubprocessIncidents(1L));

    // when
    final var actual =
        getProcessDefinitionElementStatisticsEntities(
            processDefinitionReader, pd1.processDefinitionKey(), f -> {});

    // then
    assertThat(actual).hasSize(2);
    assertStatisticsResult(actual, NODE1, 2L, 2L, 3L, 0L);
    assertStatisticsResult(actual, NODE2, 0L, 0L, 0L, 2L);
  }

  @TestTemplate
  public void shouldCollectStatisticsAndFilterForProcessInstance(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var processDefinitionReader = rdbmsService.getProcessDefinitionReader();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var pd1 = ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriter, b -> b);
    final var node1Active = createActive(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    createIncident(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    final var node2Incident = createIncident(rdbmsWriter, pd1.processDefinitionKey(), NODE2);

    // when
    final var actual =
        getProcessDefinitionElementStatisticsEntities(
            processDefinitionReader,
            pd1.processDefinitionKey(),
            f -> f.processInstanceKeys(node1Active, node2Incident));

    // then
    assertThat(actual).hasSize(2);
    assertStatisticsResult(actual, NODE1, 1L, 0L, 0L, 0L);
    assertStatisticsResult(actual, NODE2, 0L, 0L, 0L, 1L);
  }

  @TestTemplate
  public void shouldCollectStatisticsCompleted(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var processDefinitionReader = rdbmsService.getProcessDefinitionReader();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var pd1 = ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriter, b -> b);
    createCompleted(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    createCompleted(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    createCompleted(rdbmsWriter, pd1.processDefinitionKey(), NODE2);
    createPIAndFNI(
        rdbmsWriter,
        pd1.processDefinitionKey(),
        b -> b.state(FlowNodeState.COMPLETED).flowNodeId(NODE1).type(FlowNodeType.START_EVENT));

    // when
    final var actual =
        getProcessDefinitionElementStatisticsEntities(
            processDefinitionReader, pd1.processDefinitionKey(), b -> {});

    // then
    assertThat(actual).hasSize(2);
    assertStatisticsResult(actual, NODE1, 0L, 0L, 2L, 0L);
    assertStatisticsResult(actual, NODE2, 0L, 0L, 1L, 0L);
  }

  @TestTemplate
  public void shouldCollectStatisticsCanceled(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var processDefinitionReader = rdbmsService.getProcessDefinitionReader();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var pd1 = ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriter, b -> b);
    createCanceled(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    createCanceled(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    createCanceled(rdbmsWriter, pd1.processDefinitionKey(), NODE2);

    // when
    final var actual =
        getProcessDefinitionElementStatisticsEntities(
            processDefinitionReader, pd1.processDefinitionKey(), b -> {});

    // then
    assertThat(actual).hasSize(2);
    assertStatisticsResult(actual, NODE1, 0L, 2L, 0L, 0L);
    assertStatisticsResult(actual, NODE2, 0L, 1L, 0L, 0L);
  }

  @TestTemplate
  public void shouldCollectStatisticsActive(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var processDefinitionReader = rdbmsService.getProcessDefinitionReader();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var pd1 = ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriter, b -> b);
    createActive(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    createActive(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    createPIAndFNI(
        rdbmsWriter,
        pd1.processDefinitionKey(),
        b -> b.state(FlowNodeState.ACTIVE).flowNodeId(NODE1).incidentKey(2L));

    // when
    final var actual =
        getProcessDefinitionElementStatisticsEntities(
            processDefinitionReader, pd1.processDefinitionKey(), b -> {});

    // then
    assertThat(actual).hasSize(1);
    assertStatisticsResult(actual, NODE1, 2L, 0L, 0L, 1L);
  }

  @TestTemplate
  public void shouldCollectStatisticsIncidents(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var processDefinitionReader = rdbmsService.getProcessDefinitionReader();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var pd1 = ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriter, b -> b);
    createIncident(rdbmsWriter, pd1.processDefinitionKey(), NODE1);
    createIncident(rdbmsWriter, pd1.processDefinitionKey(), NODE2);
    createPIAndFNI(
        rdbmsWriter,
        pd1.processDefinitionKey(),
        b -> b.flowNodeId(NODE1).numSubprocessIncidents(3L));
    createPIAndFNI(
        rdbmsWriter,
        pd1.processDefinitionKey(),
        b -> b.state(FlowNodeState.COMPLETED).flowNodeId(NODE1).numSubprocessIncidents(3L));

    // when
    final var actual =
        getProcessDefinitionElementStatisticsEntities(
            processDefinitionReader, pd1.processDefinitionKey(), b -> {});

    // then
    assertThat(actual).hasSize(2);
    assertStatisticsResult(actual, NODE1, 0L, 0L, 0L, 2L);
    assertStatisticsResult(actual, NODE2, 0L, 0L, 0L, 1L);
  }

  private static List<ProcessFlowNodeStatisticsEntity>
      getProcessDefinitionElementStatisticsEntities(
          final ProcessDefinitionReader processDefinitionReader,
          final Long processDefinitionKey,
          final Consumer<ProcessDefinitionStatisticsFilter.Builder> fn) {
    final var builder = new ProcessDefinitionStatisticsFilter.Builder(processDefinitionKey);
    fn.accept(builder);
    return processDefinitionReader.flowNodeStatistics(builder.build());
  }

  private Long createActive(
      final RdbmsWriter rdbmsWriter, final Long processDefinitionKey, final String elementId) {
    return createPIAndFNI(
        rdbmsWriter,
        processDefinitionKey,
        b -> b.flowNodeId(elementId).state(FlowNodeState.ACTIVE));
  }

  private void createCompleted(
      final RdbmsWriter rdbmsWriter, final Long processDefinitionKey, final String elementId) {
    createPIAndFNI(
        rdbmsWriter,
        processDefinitionKey,
        b -> b.flowNodeId(elementId).state(FlowNodeState.COMPLETED).type(FlowNodeType.END_EVENT));
  }

  private void createCanceled(
      final RdbmsWriter rdbmsWriter, final Long processDefinitionKey, final String elementId) {
    createPIAndFNI(
        rdbmsWriter,
        processDefinitionKey,
        b -> b.flowNodeId(elementId).state(FlowNodeState.TERMINATED));
  }

  private Long createIncident(
      final RdbmsWriter rdbmsWriter, final Long processDefinitionKey, final String elementId) {
    return createPIAndFNI(
        rdbmsWriter,
        processDefinitionKey,
        b -> b.flowNodeId(elementId).state(FlowNodeState.ACTIVE).incidentKey(123L));
  }

  private Long createPIAndFNI(
      final RdbmsWriter rdbmsWriter,
      final Long processDefinitionKey,
      final Consumer<FlowNodeInstanceDbModelBuilder> fniCustomizer) {
    // PI
    final var processInstanceKey = nextKey();
    final var piBuilder =
        new ProcessInstanceDbModel.ProcessInstanceDbModelBuilder()
            .processInstanceKey(processInstanceKey)
            .processDefinitionKey(processDefinitionKey);
    rdbmsWriter.getProcessInstanceWriter().create(piBuilder.build());

    // FNI
    final var fniBuilder =
        new FlowNodeInstanceDbModelBuilder()
            .processInstanceKey(processInstanceKey)
            .processDefinitionKey(processDefinitionKey)
            .flowNodeInstanceKey(nextKey())
            .state(FlowNodeState.ACTIVE);
    if (fniCustomizer != null) {
      fniCustomizer.accept(fniBuilder);
    }
    rdbmsWriter.getFlowNodeInstanceWriter().create(fniBuilder.build());
    rdbmsWriter.flush();
    return processInstanceKey;
  }

  private void assertStatisticsResult(
      final List<ProcessFlowNodeStatisticsEntity> statistics,
      final String elementId,
      final long active,
      final long canceled,
      final long completed,
      final long incidents) {
    final var node =
        statistics.stream().filter(s -> s.flowNodeId().equals(elementId)).findFirst().orElseThrow();
    assertThat(node.active()).isEqualTo(active);
    assertThat(node.canceled()).isEqualTo(canceled);
    assertThat(node.completed()).isEqualTo(completed);
    assertThat(node.incidents()).isEqualTo(incidents);
  }
}
