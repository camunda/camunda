/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.flownodeinstance;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static io.camunda.it.rdbms.db.fixtures.FlowNodeInstanceFixtures.createAndSaveFlowNodeInstance;
import static io.camunda.it.rdbms.db.fixtures.FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.FlowNodeInstanceSort;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class FlowNodeInstanceIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSaveAndFindFlowNodeInstanceByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var flowNodeInstance = createAndSaveFlowNodeInstance(rdbmsWriter);

    final var actual = reader.findOne(flowNodeInstance.flowNodeInstanceKey()).orElseThrow();
    compareFlowNodeInstance(actual, flowNodeInstance);
  }

  @TestTemplate
  public void shouldFindFlowNodeInstanceByProcessDefinitionId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var flowNodeInstance = createAndSaveFlowNodeInstance(rdbmsWriter);

    final var searchResult =
        reader.search(
            new FlowNodeInstanceDbQuery(
                new FlowNodeInstanceFilter.Builder()
                    .processDefinitionIds(flowNodeInstance.processDefinitionId())
                    .build(),
                FlowNodeInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);

    compareFlowNodeInstance(searchResult.hits().getFirst(), flowNodeInstance);
  }

  @TestTemplate
  public void shouldFindAllFlowNodeInstancePaged(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var processDefinitionId = nextStringId();
    createAndSaveRandomFlowNodeInstances(
        rdbmsWriter, b -> b.processDefinitionId(processDefinitionId));
    final var searchResult =
        reader.search(
            new FlowNodeInstanceDbQuery(
                new FlowNodeInstanceFilter.Builder()
                    .processDefinitionIds(processDefinitionId)
                    .build(),
                FlowNodeInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.hits()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllFlowNodeInstancePageValuesAreNull(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var processDefinitionId = nextStringId();
    createAndSaveRandomFlowNodeInstances(
        rdbmsWriter, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        reader.search(
            new FlowNodeInstanceDbQuery(
                new FlowNodeInstanceFilter.Builder()
                    .processDefinitionIds(processDefinitionId)
                    .build(),
                FlowNodeInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(null).size(null))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.hits()).hasSize(20);
  }

  @TestTemplate
  public void shouldFindFlowNodeInstanceWithFullFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceReader reader = rdbmsService.getFlowNodeInstanceReader();

    createAndSaveRandomFlowNodeInstances(rdbmsWriter);
    final var instance = createAndSaveFlowNodeInstance(rdbmsWriter);

    final var searchResult =
        reader.search(
            new FlowNodeInstanceDbQuery(
                new FlowNodeInstanceFilter.Builder()
                    .flowNodeInstanceKeys(instance.flowNodeInstanceKey())
                    .processInstanceKeys(instance.processInstanceKey())
                    .processDefinitionIds(instance.processDefinitionId())
                    .processDefinitionKeys(instance.processDefinitionKey())
                    .flowNodeIds(instance.flowNodeId())
                    .types(instance.type())
                    .states(instance.state())
                    .tenantIds(instance.tenantId())
                    .treePaths(instance.treePath())
                    .incidentKeys(instance.incidentKey())
                    .build(),
                FlowNodeInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(instance.flowNodeInstanceKey());
  }

  private static void compareFlowNodeInstance(
      final FlowNodeInstanceEntity actual, final FlowNodeInstanceDbModel expected) {
    assertThat(actual)
        .usingRecursiveComparison()
        .ignoringFields(
            "startDate",
            "endDate",
            "incident",
            "incidentKey",
            "treePath",
            "scopeKey",
            "processDefinitionId",
            "bpmnProcessId",
            "flowNodeInstanceKey",
            "key")
        .isEqualTo(expected);

    assertThat(actual.key()).isEqualTo(expected.flowNodeInstanceKey());
    assertThat(actual.bpmnProcessId()).isEqualTo(expected.processDefinitionId());
    assertThat(OffsetDateTime.parse(actual.startDate()))
        .isCloseTo(expected.startDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(OffsetDateTime.parse(actual.endDate()))
        .isCloseTo(expected.endDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
  }
}
