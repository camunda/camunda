/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.flownodeinstance;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromResourceIds;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromTenantIds;
import static io.camunda.it.rdbms.db.fixtures.FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.it.rdbms.db.fixtures.FlowNodeInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.sort.FlowNodeInstanceSort;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class FlowNodeInstanceIT {

  public static final int PARTITION_ID = 0;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindFlowNodeInstanceByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var flowNodeInstance =
        FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstance(rdbmsWriters);

    final var actual = reader.findOne(flowNodeInstance.flowNodeInstanceKey()).orElseThrow();
    compareFlowNodeInstance(actual, flowNodeInstance);
  }

  @TestTemplate
  public void shouldSaveLogAndResolveIncident(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader elementInstanceReader = rdbmsService.getFlowNodeInstanceReader();

    final FlowNodeInstanceDbModel original =
        FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstance(rdbmsWriters, b -> b);
    rdbmsWriters.getFlowNodeInstanceWriter().createIncident(original.flowNodeInstanceKey(), 42L);
    rdbmsWriters.flush();

    final var instance = elementInstanceReader.findOne(original.flowNodeInstanceKey()).orElse(null);

    assertThat(instance).isNotNull();
    assertThat(instance.hasIncident()).isTrue();
    assertThat(instance.incidentKey()).isEqualTo(42L);

    rdbmsWriters.getFlowNodeInstanceWriter().resolveIncident(original.flowNodeInstanceKey());
    rdbmsWriters.flush();

    final var resolvedInstance =
        elementInstanceReader.findOne(original.flowNodeInstanceKey()).orElse(null);

    assertThat(resolvedInstance).isNotNull();
    assertThat(resolvedInstance.hasIncident()).isFalse();
    assertThat(resolvedInstance.incidentKey()).isNull();
  }

  @TestTemplate
  public void shouldFindElementInstanceByProcessDefinitionId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var flowNodeInstance =
        FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstance(rdbmsWriters);

    final var searchResult =
        reader.search(
            new FlowNodeInstanceQuery(
                new FlowNodeInstanceFilter.Builder()
                    .processDefinitionIds(flowNodeInstance.processDefinitionId())
                    .build(),
                FlowNodeInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    compareFlowNodeInstance(searchResult.items().getFirst(), flowNodeInstance);
  }

  @TestTemplate
  public void shouldFindElementInstanceByAuthorizedResourceId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var flowNodeInstance =
        FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstance(rdbmsWriters);
    FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstances(rdbmsWriters);

    final var searchResult =
        reader.search(
            FlowNodeInstanceQuery.of(b -> b),
            resourceAccessChecksFromResourceIds(
                AuthorizationResourceType.PROCESS_DEFINITION,
                flowNodeInstance.processDefinitionId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    compareFlowNodeInstance(searchResult.items().getFirst(), flowNodeInstance);
  }

  @TestTemplate
  public void shouldFindElementInstanceByAuthorizedTenantId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var flowNodeInstance =
        FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstance(rdbmsWriters);
    FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstances(rdbmsWriters);

    final var searchResult =
        reader.search(
            FlowNodeInstanceQuery.of(b -> b),
            resourceAccessChecksFromTenantIds(flowNodeInstance.tenantId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    compareFlowNodeInstance(searchResult.items().getFirst(), flowNodeInstance);
  }

  @TestTemplate
  public void shouldFindAllElementInstancePaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var processDefinitionId = nextStringId();
    createAndSaveRandomFlowNodeInstances(
        rdbmsWriters, b -> b.processDefinitionId(processDefinitionId));
    final var searchResult =
        reader.search(
            new FlowNodeInstanceQuery(
                new FlowNodeInstanceFilter.Builder()
                    .processDefinitionIds(processDefinitionId)
                    .build(),
                FlowNodeInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllElementInstancePageValuesAreNull(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var processDefinitionId = nextStringId();
    createAndSaveRandomFlowNodeInstances(
        rdbmsWriters, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        reader.search(
            new FlowNodeInstanceQuery(
                new FlowNodeInstanceFilter.Builder()
                    .processDefinitionIds(processDefinitionId)
                    .build(),
                FlowNodeInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(null).size(null))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(20);
  }

  @TestTemplate
  public void shouldFindElementInstanceWithFullFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader reader = rdbmsService.getFlowNodeInstanceReader();

    FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstances(rdbmsWriters);
    final var instance = FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstance(rdbmsWriters);

    final var searchResult =
        reader.search(
            new FlowNodeInstanceQuery(
                new FlowNodeInstanceFilter.Builder()
                    .flowNodeInstanceKeys(instance.flowNodeInstanceKey())
                    .processInstanceKeys(instance.processInstanceKey())
                    .processDefinitionIds(instance.processDefinitionId())
                    .processDefinitionKeys(instance.processDefinitionKey())
                    .elementInstanceScopeKeys(instance.flowNodeScopeKey())
                    .flowNodeIds(instance.flowNodeId())
                    .types(instance.type())
                    .states(instance.state().name())
                    .tenantIds(instance.tenantId())
                    .treePaths(instance.treePath())
                    .incidentKeys(instance.incidentKey())
                    .build(),
                FlowNodeInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().flowNodeInstanceKey())
        .isEqualTo(instance.flowNodeInstanceKey());
  }

  private static void compareFlowNodeInstance(
      final FlowNodeInstanceEntity actual, final FlowNodeInstanceDbModel expected) {
    assertThat(actual)
        .usingRecursiveComparison()
        .ignoringFields(
            "startDate",
            "endDate",
            "hasIncident",
            "incidentKey",
            "treePath",
            "processDefinitionId",
            "bpmnProcessId",
            "flowNodeInstanceKey",
            "key",
            "level")
        .isEqualTo(expected);

    assertThat(actual.flowNodeInstanceKey()).isEqualTo(expected.flowNodeInstanceKey());
    assertThat(actual.processDefinitionId()).isEqualTo(expected.processDefinitionId());
    assertThat(actual.startDate())
        .isCloseTo(expected.startDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(actual.endDate())
        .isCloseTo(expected.endDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
  }

  @TestTemplate
  public void shouldFindElementInstanceWithSearchAfter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader flowNodeInstanceReader =
        rdbmsService.getFlowNodeInstanceReader();

    final var processDefinition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriters, b -> b);
    createAndSaveRandomFlowNodeInstances(
        rdbmsWriters,
        b ->
            b.processDefinitionKey(processDefinition.processDefinitionKey())
                .processDefinitionId(processDefinition.processDefinitionId()));
    final var sort =
        FlowNodeInstanceSort.of(s -> s.type().asc().tenantId().asc().startDate().desc());
    final var searchResult =
        flowNodeInstanceReader.search(
            FlowNodeInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinition.processDefinitionId()))
                        .sort(sort)));

    final var firstPage =
        flowNodeInstanceReader.search(
            FlowNodeInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinition.processDefinitionId()))
                        .sort(sort)
                        .page(p -> p.size(15))));

    final var nextPage =
        flowNodeInstanceReader.search(
            FlowNodeInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinition.processDefinitionId()))
                        .sort(sort)
                        .page(p -> p.size(5).after(firstPage.endCursor()))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(15, 20));
  }

  @TestTemplate
  public void shouldDeleteProcessInstanceRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var definition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriters, b -> b);
    final var item1 =
        FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstance(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item2 =
        FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstance(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item3 =
        FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstance(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));

    // when
    final int deleted =
        rdbmsWriters
            .getFlowNodeInstanceWriter()
            .deleteProcessInstanceRelatedData(List.of(item2.processInstanceKey()), 10);

    // then
    assertThat(deleted).isEqualTo(1);
    final var searchResult =
        reader.search(
            FlowNodeInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(definition.processDefinitionKey()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(FlowNodeInstanceEntity::flowNodeInstanceKey))
        .containsExactlyInAnyOrder(item1.flowNodeInstanceKey(), item3.flowNodeInstanceKey());
  }

  @TestTemplate
  public void shouldDeleteRootProcessInstanceRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var definition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriters, b -> b);
    final var item1 =
        FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstance(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item2 =
        FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstance(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item3 =
        FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstance(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));

    // when
    final int deleted =
        rdbmsWriters
            .getFlowNodeInstanceWriter()
            .deleteRootProcessInstanceRelatedData(List.of(item2.rootProcessInstanceKey()), 10);

    // then
    assertThat(deleted).isEqualTo(1);
    final var searchResult =
        reader.search(
            FlowNodeInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(definition.processDefinitionKey()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(FlowNodeInstanceEntity::flowNodeInstanceKey))
        .containsExactlyInAnyOrder(item1.flowNodeInstanceKey(), item3.flowNodeInstanceKey());
  }
}
