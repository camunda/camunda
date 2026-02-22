/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.processinstance;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromTenantIds;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveProcessInstance;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveRandomProcessInstance;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveRandomProcessInstances;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveRandomRootProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.it.rdbms.db.fixtures.CommonFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.sort.ProcessInstanceSort;
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
public class ProcessInstanceIT {

  public static final int PARTITION_ID = 0;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindProcessInstanceByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final Long processInstanceKey = nextKey();
    final long rootProcessInstanceKey = nextKey();
    createAndSaveProcessInstance(
        rdbmsWriters,
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processInstanceKey(processInstanceKey)
                    .rootProcessInstanceKey(rootProcessInstanceKey)
                    .processDefinitionId("test-process")
                    .processDefinitionKey(1337L)
                    .state(ProcessInstanceState.ACTIVE)
                    .startDate(NOW)
                    .parentProcessInstanceKey(-1L)
                    .parentElementInstanceKey(-1L)
                    .version(1)));

    final var instance = processInstanceReader.findOne(processInstanceKey).orElse(null);

    assertThat(instance).isNotNull();
    assertThat(instance.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(instance.rootProcessInstanceKey()).isEqualTo(rootProcessInstanceKey);
    assertThat(instance.processDefinitionId()).isEqualTo("test-process");
    assertThat(instance.processDefinitionKey()).isEqualTo(1337L);
    assertThat(instance.state()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(instance.startDate())
        .isCloseTo(NOW, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.parentProcessInstanceKey()).isEqualTo(-1L);
    assertThat(instance.parentFlowNodeInstanceKey()).isEqualTo(-1L);
    assertThat(instance.processDefinitionVersion()).isEqualTo(1);
    assertThat(instance.hasIncident()).isFalse();
  }

  @TestTemplate
  public void shouldSaveLogAndResolveIncident(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final ProcessInstanceDbModel original = ProcessInstanceFixtures.createRandomized(b -> b);
    createAndSaveProcessInstance(rdbmsWriters, original);
    rdbmsWriters.getProcessInstanceWriter().createIncident(original.processInstanceKey());
    rdbmsWriters.flush();

    final var instance = processInstanceReader.findOne(original.processInstanceKey()).orElse(null);

    assertThat(instance).isNotNull();
    assertThat(instance.hasIncident()).isTrue();

    rdbmsWriters.getProcessInstanceWriter().resolveIncident(original.processInstanceKey());
    rdbmsWriters.flush();

    final var resolvedInstance =
        processInstanceReader.findOne(original.processInstanceKey()).orElse(null);

    assertThat(resolvedInstance).isNotNull();
    assertThat(resolvedInstance.hasIncident()).isFalse();
  }

  @TestTemplate
  public void shouldFindProcessInstanceByBpmnProcessId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final Long processInstanceKey = nextKey();
    createAndSaveProcessInstance(
        rdbmsWriters,
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processInstanceKey(processInstanceKey)
                    .processDefinitionId("test-process-unique")
                    .processDefinitionKey(1338L)
                    .state(ProcessInstanceState.ACTIVE)
                    .startDate(NOW)
                    .parentProcessInstanceKey(-1L)
                    .parentElementInstanceKey(-1L)
                    .version(1)));

    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds("test-process-unique"))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    assertThat(instance.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(instance.processDefinitionId()).isEqualTo("test-process-unique");
    assertThat(instance.processDefinitionKey()).isEqualTo(1338L);
    assertThat(instance.state()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(instance.startDate())
        .isCloseTo(NOW, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.parentProcessInstanceKey()).isEqualTo(-1L);
    assertThat(instance.parentFlowNodeInstanceKey()).isEqualTo(-1L);
    assertThat(instance.processDefinitionVersion()).isEqualTo(1);
  }

  @TestTemplate
  public void shouldFindProcessInstanceByAuthorizationResourceId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final var processInstance = createAndSaveRandomProcessInstance(rdbmsWriters, b -> b);
    createAndSaveRandomProcessInstances(rdbmsWriters);

    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(b -> b),
            CommonFixtures.resourceAccessChecksFromResourceIds(
                AuthorizationResourceType.PROCESS_DEFINITION,
                processInstance.processDefinitionId()));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().processInstanceKey())
        .isEqualTo(processInstance.processInstanceKey());
  }

  @TestTemplate
  public void shouldFindProcessInstanceByAuthorizationTenantId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final var processInstance = createAndSaveRandomProcessInstance(rdbmsWriters, b -> b);
    createAndSaveRandomProcessInstances(rdbmsWriters);

    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(b -> b),
            resourceAccessChecksFromTenantIds(processInstance.tenantId()));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().processInstanceKey())
        .isEqualTo(processInstance.processInstanceKey());
  }

  @TestTemplate
  public void shouldFindProcessInstanceWithIncidents(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final Long processDefinitionKey = nextKey();
    createAndSaveRandomProcessInstances(
        rdbmsWriters, b -> b.processDefinitionKey(processDefinitionKey));
    final var incidentPI1 =
        createAndSaveRandomProcessInstance(
            rdbmsWriters, b -> b.processDefinitionKey(processDefinitionKey).numIncidents(1));
    final var incidentPI2 =
        createAndSaveRandomProcessInstance(
            rdbmsWriters, b -> b.processDefinitionKey(processDefinitionKey).numIncidents(2));

    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(processDefinitionKey).hasIncident(true))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);

    assertThat(searchResult.items().stream().map(ProcessInstanceEntity::processInstanceKey))
        .containsExactlyInAnyOrder(
            incidentPI1.processInstanceKey(), incidentPI2.processInstanceKey());
  }

  @TestTemplate
  public void shouldFindAllProcessInstancePaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final String processDefinitionId = ProcessInstanceFixtures.nextStringId();
    createAndSaveRandomProcessInstances(
        rdbmsWriters, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinitionId))
                        .sort(s -> s.startDate().asc().processDefinitionName().asc())
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllProcessInstancePagedWithHasMoreHits(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final String processDefinitionId = ProcessInstanceFixtures.nextStringId();
    createAndSaveRandomProcessInstances(
        rdbmsWriters, 101, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinitionId))
                        .sort(s -> s.startDate().asc().processDefinitionName().asc())
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(100);
    assertThat(searchResult.hasMoreTotalItems()).isEqualTo(true);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindProcessInstanceWithFullFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final Long processInstanceKey = nextKey();
    createAndSaveRandomProcessInstances(rdbmsWriters);
    createAndSaveProcessInstance(
        rdbmsWriters,
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processInstanceKey(processInstanceKey)
                    .processDefinitionId("test-process")
                    .processDefinitionKey(1337L)
                    .state(ProcessInstanceState.ACTIVE)
                    .startDate(NOW)
                    .endDate(NOW)
                    .parentProcessInstanceKey(-1L)
                    .parentElementInstanceKey(-1L)
                    .version(1)
                    .partitionId(123456)));

    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(
                            f ->
                                f.processInstanceKeys(processInstanceKey)
                                    .processDefinitionIds("test-process")
                                    .processDefinitionKeys(1337L)
                                    .states(ProcessInstanceState.ACTIVE.name())
                                    .parentProcessInstanceKeys(-1L)
                                    .parentFlowNodeInstanceKeys(-1L)
                                    .partitionId(123456))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().processInstanceKey()).isEqualTo(processInstanceKey);
  }

  @TestTemplate
  public void shouldFindProcessInstanceWithSearchAfter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final var processDefinition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriters, b -> b);
    createAndSaveRandomProcessInstances(
        rdbmsWriters,
        b ->
            b.processDefinitionKey(processDefinition.processDefinitionKey())
                .processDefinitionId(processDefinition.processDefinitionId()));
    final var sort =
        ProcessInstanceSort.of(
            s ->
                s.processDefinitionName()
                    .asc()
                    .processDefinitionVersion()
                    .asc()
                    .startDate()
                    .desc());
    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinition.processDefinitionId()))
                        .sort(sort)
                        .page(p -> p.from(0).size(20))));

    final var firstPage =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinition.processDefinitionId()))
                        .sort(sort)
                        .page(p -> p.from(0).size(10))));

    final var nextPage =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinition.processDefinitionId()))
                        .sort(sort)
                        .page(p -> p.size(10).after(firstPage.endCursor()))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(10);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(10, 20));
  }

  @TestTemplate
  public void shouldDeleteProcessByKey(final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final var cleanupDate = NOW.minusDays(1);

    final var processDefinition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriters, b -> b);
    final var pi1 =
        createAndSaveRandomProcessInstance(
            rdbmsWriters,
            b ->
                b.processDefinitionKey(processDefinition.processDefinitionKey())
                    .partitionId(PARTITION_ID));
    final var pi2 =
        createAndSaveRandomProcessInstance(
            rdbmsWriters,
            b ->
                b.processDefinitionKey(processDefinition.processDefinitionKey())
                    .partitionId(PARTITION_ID));
    final var pi3 =
        createAndSaveRandomProcessInstance(
            rdbmsWriters,
            b ->
                b.processDefinitionKey(processDefinition.processDefinitionKey())
                    .partitionId(PARTITION_ID));

    // when
    final int deleted =
        rdbmsWriters.getProcessInstanceWriter().deleteByKeys(List.of(pi2.processInstanceKey()));

    // then
    assertThat(deleted).isEqualTo(1);
    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(processDefinition.processDefinitionKey()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(ProcessInstanceEntity::processInstanceKey))
        .containsExactlyInAnyOrder(pi1.processInstanceKey(), pi3.processInstanceKey());
  }

  @TestTemplate
  public void shouldDeleteChildrenByRootProcessInstances(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessInstanceDbReader processInstanceReader = rdbmsService.getProcessInstanceReader();

    final var processDefinition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriters, b -> b);

    final long rootProcessInstanceKey = nextKey();
    final var rootProcessInstance =
        createAndSaveRandomProcessInstance(
            rdbmsWriters,
            b ->
                b.processDefinitionKey(processDefinition.processDefinitionKey())
                    .partitionId(PARTITION_ID)
                    .processInstanceKey(rootProcessInstanceKey)
                    .rootProcessInstanceKey(rootProcessInstanceKey));
    final var pi1 =
        createAndSaveRandomProcessInstance(
            rdbmsWriters,
            b ->
                b.processDefinitionKey(processDefinition.processDefinitionKey())
                    .rootProcessInstanceKey(rootProcessInstanceKey)
                    .partitionId(PARTITION_ID));
    final var pi2 =
        createAndSaveRandomProcessInstance(
            rdbmsWriters,
            b ->
                b.processDefinitionKey(processDefinition.processDefinitionKey())
                    .rootProcessInstanceKey(rootProcessInstanceKey)
                    .partitionId(PARTITION_ID));
    final var pi3 =
        createAndSaveRandomProcessInstance(
            rdbmsWriters,
            b ->
                b.processDefinitionKey(processDefinition.processDefinitionKey())
                    .partitionId(PARTITION_ID));

    // when
    final int deleted =
        rdbmsWriters
            .getProcessInstanceWriter()
            .deleteChildrenByRootProcessInstances(List.of(rootProcessInstanceKey), 10);

    // then
    assertThat(deleted).isEqualTo(2);
    final var searchResult =
        processInstanceReader.search(
            ProcessInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(processDefinition.processDefinitionKey()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(ProcessInstanceEntity::processInstanceKey))
        .containsExactlyInAnyOrder(rootProcessInstanceKey, pi3.processInstanceKey());
  }

  @TestTemplate
  public void shouldSelectRootExpiredRootProcessInstances(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var partitionId = (int) (Math.random() * 1000);

    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(partitionId);

    final var cleanupDate = NOW;

    // Create process instances with different cleanup dates
    final var pi1 =
        createAndSaveRandomRootProcessInstance(rdbmsWriters, b -> b.partitionId(partitionId));
    final var pi2 =
        createAndSaveRandomRootProcessInstance(rdbmsWriters, b -> b.partitionId(partitionId));
    final var pi3 =
        createAndSaveRandomRootProcessInstance(rdbmsWriters, b -> b.partitionId(partitionId));

    // pi1: expired (cleanup date in the past)
    rdbmsWriters
        .getProcessInstanceWriter()
        .scheduleForHistoryCleanup(pi1.processInstanceKey(), NOW.minusDays(2));
    // pi2: not expired (cleanup date in the future)
    rdbmsWriters
        .getProcessInstanceWriter()
        .scheduleForHistoryCleanup(pi2.processInstanceKey(), NOW.plusDays(2));
    // pi3: expired (cleanup date in the past)
    rdbmsWriters
        .getProcessInstanceWriter()
        .scheduleForHistoryCleanup(pi3.processInstanceKey(), NOW.minusDays(1));
    rdbmsWriters.flush();

    // Select expired process instances
    final var expiredPIs =
        rdbmsService
            .getProcessInstanceReader()
            .selectExpiredRootProcessInstances(partitionId, cleanupDate, 10);

    assertThat(expiredPIs).hasSize(2);
    assertThat(expiredPIs)
        .containsExactlyInAnyOrder(pi1.processInstanceKey(), pi3.processInstanceKey());
  }

  @TestTemplate
  public void shouldSelectExpiredRootProcessInstancesWithLimit(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var partitionId = (int) (Math.random() * 1000);
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(partitionId);

    final var cleanupDate = NOW;

    // Create 5 expired process instances
    final var pi1 =
        createAndSaveRandomRootProcessInstance(rdbmsWriters, b -> b.partitionId(partitionId));
    final var pi2 =
        createAndSaveRandomRootProcessInstance(rdbmsWriters, b -> b.partitionId(partitionId));
    final var pi3 =
        createAndSaveRandomRootProcessInstance(rdbmsWriters, b -> b.partitionId(partitionId));
    final var pi4 =
        createAndSaveRandomRootProcessInstance(rdbmsWriters, b -> b.partitionId(partitionId));
    final var pi5 =
        createAndSaveRandomRootProcessInstance(rdbmsWriters, b -> b.partitionId(partitionId));

    // All expired
    rdbmsWriters
        .getProcessInstanceWriter()
        .scheduleForHistoryCleanup(pi1.processInstanceKey(), NOW.minusDays(5));
    rdbmsWriters
        .getProcessInstanceWriter()
        .scheduleForHistoryCleanup(pi2.processInstanceKey(), NOW.minusDays(4));
    rdbmsWriters
        .getProcessInstanceWriter()
        .scheduleForHistoryCleanup(pi3.processInstanceKey(), NOW.minusDays(3));
    rdbmsWriters
        .getProcessInstanceWriter()
        .scheduleForHistoryCleanup(pi4.processInstanceKey(), NOW.minusDays(2));
    rdbmsWriters
        .getProcessInstanceWriter()
        .scheduleForHistoryCleanup(pi5.processInstanceKey(), NOW.minusDays(1));
    rdbmsWriters.flush();

    // Select with limit of 3
    final var expiredPIs =
        rdbmsService
            .getProcessInstanceReader()
            .selectExpiredRootProcessInstances(partitionId, cleanupDate, 3);

    assertThat(expiredPIs).hasSize(3);
    assertThat(expiredPIs)
        .containsAnyOf(
            pi1.processInstanceKey(),
            pi2.processInstanceKey(),
            pi3.processInstanceKey(),
            pi4.processInstanceKey(),
            pi5.processInstanceKey());
  }

  @TestTemplate
  public void shouldNotSelectExpiredRootProcessInstancesFromDifferentPartition(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var partitionId = (int) (Math.random() * 1000);
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(partitionId);

    final var cleanupDate = NOW;

    // Create process instances in different partitions
    final var pi1 =
        createAndSaveRandomRootProcessInstance(rdbmsWriters, b -> b.partitionId(partitionId));
    final var pi2 =
        createAndSaveRandomRootProcessInstance(rdbmsWriters, b -> b.partitionId(PARTITION_ID));

    // Both expired
    rdbmsWriters
        .getProcessInstanceWriter()
        .scheduleForHistoryCleanup(pi1.processInstanceKey(), NOW.minusDays(1));
    rdbmsWriters
        .getProcessInstanceWriter()
        .scheduleForHistoryCleanup(pi2.processInstanceKey(), NOW.minusDays(1));
    rdbmsWriters.flush();

    // Select expired process instances for partitionId only
    final var expiredPIs =
        rdbmsService
            .getProcessInstanceReader()
            .selectExpiredRootProcessInstances(partitionId, cleanupDate, 10);

    assertThat(expiredPIs).hasSize(1);
    assertThat(expiredPIs).containsExactly(pi1.processInstanceKey());
  }

  @TestTemplate
  public void shouldNotSelectRootProcessInstancesWithoutCleanupDate(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var partitionId = (int) (Math.random() * 1000);
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(partitionId);

    final var cleanupDate = NOW;

    // Create process instances
    final var pi1 =
        createAndSaveRandomRootProcessInstance(rdbmsWriters, b -> b.partitionId(partitionId));
    final var pi2 =
        createAndSaveRandomRootProcessInstance(rdbmsWriters, b -> b.partitionId(partitionId));

    // pi1: has cleanup date (expired)
    rdbmsWriters
        .getProcessInstanceWriter()
        .scheduleForHistoryCleanup(pi1.processInstanceKey(), NOW.minusDays(1));
    // pi2: no cleanup date set
    rdbmsWriters.flush();

    // Select expired process instances
    final var expiredPIs =
        rdbmsService
            .getProcessInstanceReader()
            .selectExpiredRootProcessInstances(partitionId, cleanupDate, 10);

    assertThat(expiredPIs).hasSize(1);
    assertThat(expiredPIs).containsExactly(pi1.processInstanceKey());
  }

  @TestTemplate
  public void shouldNotScheduleNonRootProcessInstancesForCleanupByThemselves(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var partitionId = (int) (Math.random() * 1000);
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(partitionId);

    final var cleanupDate = NOW;

    // Create non-root process instances
    final var pi1 =
        createAndSaveRandomProcessInstance(rdbmsWriters, b -> b.partitionId(partitionId));

    // should update nothing
    rdbmsWriters
        .getProcessInstanceWriter()
        .scheduleForHistoryCleanup(pi1.processInstanceKey(), NOW.minusDays(1));

    rdbmsWriters.flush();

    // Select expired process instances
    final var expiredPIs =
        rdbmsService
            .getProcessInstanceReader()
            .selectExpiredRootProcessInstances(partitionId, cleanupDate, 10);

    assertThat(expiredPIs).hasSize(0);
  }
}
