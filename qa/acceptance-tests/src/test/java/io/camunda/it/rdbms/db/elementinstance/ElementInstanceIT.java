/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.elementinstance;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static io.camunda.it.rdbms.db.fixtures.ElementInstanceFixtures.createAndSaveElementInstance;
import static io.camunda.it.rdbms.db.fixtures.ElementInstanceFixtures.createAndSaveRandomElementInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.sort.FlowNodeInstanceSort;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ElementInstanceIT {

  public static final int PARTITION_ID = 0;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindElementInstanceByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var elementInstance = createAndSaveElementInstance(rdbmsWriter);

    final var actual = reader.findOne(elementInstance.flowNodeInstanceKey()).orElseThrow();
    compareElementInstance(actual, elementInstance);
  }

  @TestTemplate
  public void shouldSaveLogAndResolveIncident(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader elementInstanceReader = rdbmsService.getFlowNodeInstanceReader();

    final FlowNodeInstanceDbModel original = createAndSaveElementInstance(rdbmsWriter, b -> b);
    rdbmsWriter.getFlowNodeInstanceWriter().createIncident(original.flowNodeInstanceKey(), 42L);
    rdbmsWriter.flush();

    final var instance = elementInstanceReader.findOne(original.flowNodeInstanceKey()).orElse(null);

    assertThat(instance).isNotNull();
    assertThat(instance.hasIncident()).isTrue();
    assertThat(instance.incidentKey()).isEqualTo(42L);

    rdbmsWriter.getFlowNodeInstanceWriter().resolveIncident(original.flowNodeInstanceKey());
    rdbmsWriter.flush();

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
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var elementInstance = createAndSaveElementInstance(rdbmsWriter);

    final var searchResult =
        reader.search(
            new FlowNodeInstanceQuery(
                new FlowNodeInstanceFilter.Builder()
                    .processDefinitionIds(elementInstance.processDefinitionId())
                    .build(),
                FlowNodeInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    compareElementInstance(searchResult.items().getFirst(), elementInstance);
  }

  @TestTemplate
  public void shouldFindAllElementInstancePaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var processDefinitionId = nextStringId();
    createAndSaveRandomElementInstances(
        rdbmsWriter, b -> b.processDefinitionId(processDefinitionId));
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
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var processDefinitionId = nextStringId();
    createAndSaveRandomElementInstances(
        rdbmsWriter, b -> b.processDefinitionId(processDefinitionId));

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
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader reader = rdbmsService.getFlowNodeInstanceReader();

    createAndSaveRandomElementInstances(rdbmsWriter);
    final var instance = createAndSaveElementInstance(rdbmsWriter);

    final var searchResult =
        reader.search(
            new FlowNodeInstanceQuery(
                new FlowNodeInstanceFilter.Builder()
                    .flowNodeInstanceKeys(instance.flowNodeInstanceKey())
                    .processInstanceKeys(instance.processInstanceKey())
                    .processDefinitionIds(instance.processDefinitionId())
                    .processDefinitionKeys(instance.processDefinitionKey())
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

  private static void compareElementInstance(
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
            "key")
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
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader elementInstanceReader = rdbmsService.getFlowNodeInstanceReader();

    final var processDefinition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriter, b -> b);
    createAndSaveRandomElementInstances(
        rdbmsWriter,
        b ->
            b.processDefinitionKey(processDefinition.processDefinitionKey())
                .processDefinitionId(processDefinition.processDefinitionId()));
    final var sort =
        FlowNodeInstanceSort.of(s -> s.type().asc().tenantId().asc().startDate().desc());
    final var searchResult =
        elementInstanceReader.search(
            FlowNodeInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinition.processDefinitionId()))
                        .sort(sort)));

    final var firstPage =
        elementInstanceReader.search(
            FlowNodeInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinition.processDefinitionId()))
                        .sort(sort)
                        .page(p -> p.size(15))));

    final var nextPage =
        elementInstanceReader.search(
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
  public void shouldCleanup(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader reader = rdbmsService.getFlowNodeInstanceReader();

    final var cleanupDate = NOW.minusDays(1);

    final var definition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriter, b -> b);
    final var item1 =
        createAndSaveElementInstance(
            rdbmsWriter, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item2 =
        createAndSaveElementInstance(
            rdbmsWriter, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item3 =
        createAndSaveElementInstance(
            rdbmsWriter, b -> b.processDefinitionKey(definition.processDefinitionKey()));

    // set cleanup dates
    rdbmsWriter
        .getFlowNodeInstanceWriter()
        .scheduleForHistoryCleanup(item1.processInstanceKey(), NOW);
    rdbmsWriter
        .getFlowNodeInstanceWriter()
        .scheduleForHistoryCleanup(item2.processInstanceKey(), NOW.minusDays(2));
    rdbmsWriter.flush();

    // cleanup
    rdbmsWriter.getFlowNodeInstanceWriter().cleanupHistory(PARTITION_ID, cleanupDate, 10);

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
