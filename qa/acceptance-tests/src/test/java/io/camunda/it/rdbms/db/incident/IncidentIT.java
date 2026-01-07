/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.incident;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromResourceIds;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromTenantIds;
import static io.camunda.it.rdbms.db.fixtures.IncidentFixtures.createAndSaveIncident;
import static io.camunda.it.rdbms.db.fixtures.IncidentFixtures.createAndSaveRandomIncidents;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.IncidentDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.it.rdbms.db.fixtures.IncidentFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.sort.IncidentSort;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class IncidentIT {

  public static final int PARTITION_ID = 0;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindIncidentByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final IncidentDbReader incidentReader = rdbmsService.getIncidentReader();

    final var original = IncidentFixtures.createRandomized(b -> b);
    createAndSaveIncident(rdbmsWriters, original);

    final var instance = incidentReader.findOne(original.incidentKey()).orElse(null);

    compareIncident(instance, original);
  }

  @TestTemplate
  public void shouldSaveAndFindIncidentWithLargeErrorMessageByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final IncidentDbReader incidentReader = rdbmsService.getIncidentReader();

    final var original = IncidentFixtures.createRandomized(b -> b.errorMessage("x".repeat(9000)));
    createAndSaveIncident(rdbmsWriters, original);

    final var instance = incidentReader.findOne(original.incidentKey()).orElse(null);

    assertThat(instance).isNotNull();
    assertThat(instance.errorMessage().length()).isLessThan(original.errorMessage().length());
  }

  @TestTemplate
  public void shouldSaveAndResolveIncident(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final IncidentDbReader incidentReader = rdbmsService.getIncidentReader();

    final var original = IncidentFixtures.createRandomized(b -> b);
    createAndSaveIncident(rdbmsWriters, original);
    rdbmsWriters.getIncidentWriter().resolve(original.incidentKey());
    rdbmsWriters.flush();

    final var instance = incidentReader.findOne(original.incidentKey()).orElse(null);

    assertThat(instance).isNotNull();
    assertThat(instance.state()).isEqualTo(IncidentEntity.IncidentState.RESOLVED);
    assertThat(instance.errorMessage()).isNull();
  }

  @TestTemplate
  public void shouldFindIncidentByBpmnProcessId(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final IncidentDbReader incidentReader = rdbmsService.getIncidentReader();

    final var original = IncidentFixtures.createRandomized(b -> b);
    createAndSaveIncident(rdbmsWriters, original);

    final var searchResult =
        incidentReader.search(
            IncidentQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(original.processDefinitionId()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    compareIncident(instance, original);
  }

  @TestTemplate
  public void shouldFindIncidentByAuthorizedResourceId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final IncidentDbReader incidentReader = rdbmsService.getIncidentReader();

    final var original = IncidentFixtures.createRandomized(b -> b);
    createAndSaveIncident(rdbmsWriters, original);
    createAndSaveRandomIncidents(rdbmsWriters);

    final var searchResult =
        incidentReader.search(
            IncidentQuery.of(b -> b),
            resourceAccessChecksFromResourceIds(
                AuthorizationResourceType.PROCESS_DEFINITION, original.processDefinitionId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    compareIncident(searchResult.items().getFirst(), original);
  }

  @TestTemplate
  public void shouldFindIncidentByAuthorizedTenantId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final IncidentDbReader incidentReader = rdbmsService.getIncidentReader();

    final var original = IncidentFixtures.createRandomized(b -> b);
    createAndSaveIncident(rdbmsWriters, original);
    createAndSaveRandomIncidents(rdbmsWriters);

    final var searchResult =
        incidentReader.search(
            IncidentQuery.of(b -> b), resourceAccessChecksFromTenantIds(original.tenantId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    compareIncident(searchResult.items().getFirst(), original);
  }

  @TestTemplate
  public void shouldFindAllIncidentPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final IncidentDbReader incidentReader = rdbmsService.getIncidentReader();

    final String processDefinitionId = IncidentFixtures.nextStringId();
    createAndSaveRandomIncidents(rdbmsWriters, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        incidentReader.search(
            IncidentQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinitionId))
                        .sort(s -> s.creationTime().asc().flowNodeId().asc())
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindIncidentWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final IncidentDbReader incidentReader = rdbmsService.getIncidentReader();

    final var original = IncidentFixtures.createRandomized(b -> b);
    createAndSaveIncident(rdbmsWriters, original);
    createAndSaveRandomIncidents(rdbmsWriters);

    final var searchResult =
        incidentReader.search(
            IncidentQuery.of(
                b ->
                    b.filter(
                            f ->
                                f.incidentKeys(original.incidentKey())
                                    .processInstanceKeys(original.processInstanceKey())
                                    .processDefinitionIds(original.processDefinitionId())
                                    .processDefinitionKeys(original.processDefinitionKey())
                                    .states(original.state().name())
                                    .errorTypes(original.errorType().name())
                                    .errorMessages(original.errorMessage())
                                    .errorMessageHashes(original.errorMessageHash())
                                    .flowNodeInstanceKeys(original.flowNodeInstanceKey())
                                    .flowNodeIds(original.flowNodeId())
                                    .jobKeys(original.jobKey())
                                    .tenantIds(original.tenantId())
                                    .creationTimeOperations(
                                        Operation.gt(original.creationDate().minusSeconds(1)),
                                        Operation.lt(original.creationDate().plusSeconds(1))))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().incidentKey()).isEqualTo(original.incidentKey());
  }

  @TestTemplate
  public void shouldFindIncidentWithSearchAfter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final IncidentDbReader incidentReader = rdbmsService.getIncidentReader();

    final var processDefinitionKey = nextKey();
    createAndSaveRandomIncidents(rdbmsWriters, b -> b.processDefinitionKey(processDefinitionKey));
    final var sort = IncidentSort.of(s -> s.state().asc().creationTime().asc().flowNodeId().desc());
    final var searchResult =
        incidentReader.search(
            IncidentQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(processDefinitionKey))
                        .sort(sort)
                        .page(p -> p.from(0).size(20))));

    final var firstPage =
        incidentReader.search(
            IncidentQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(processDefinitionKey))
                        .sort(sort)
                        .page(p -> p.size(15))));

    final var nextPage =
        incidentReader.search(
            IncidentQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(processDefinitionKey))
                        .sort(sort)
                        .page(p -> p.size(5).after(firstPage.endCursor()))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(15, 20));
  }

  private void compareIncident(final IncidentEntity instance, final IncidentDbModel original) {
    assertThat(instance).isNotNull();
    assertThat(instance)
        .usingRecursiveComparison()
        .ignoringFields("bpmnProcessId", "key", "creationTime", "treePath")
        .isEqualTo(original);
    assertThat(instance.incidentKey()).isEqualTo(original.incidentKey());
    assertThat(instance.processDefinitionId()).isEqualTo(original.processDefinitionId());
    assertThat(instance.creationTime())
        .isCloseTo(original.creationDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
  }

  @TestTemplate
  public void shouldCleanup(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final IncidentDbReader reader = rdbmsService.getIncidentReader();

    final var cleanupDate = NOW.minusDays(1);

    final var definition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriters, b -> b);
    final var item1 =
        createAndSaveIncident(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item2 =
        createAndSaveIncident(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item3 =
        createAndSaveIncident(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));

    // set cleanup dates
    rdbmsWriters.getIncidentWriter().scheduleForHistoryCleanup(item1.processInstanceKey(), NOW);
    rdbmsWriters
        .getIncidentWriter()
        .scheduleForHistoryCleanup(item2.processInstanceKey(), NOW.minusDays(2));
    rdbmsWriters.flush();

    // cleanup
    rdbmsWriters.getIncidentWriter().cleanupHistory(PARTITION_ID, cleanupDate, 10);

    final var searchResult =
        reader.search(
            IncidentQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(definition.processDefinitionKey()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(IncidentEntity::incidentKey))
        .containsExactlyInAnyOrder(item1.incidentKey(), item3.incidentKey());
  }
}
