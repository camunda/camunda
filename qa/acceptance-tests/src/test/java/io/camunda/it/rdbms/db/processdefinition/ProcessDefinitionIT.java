/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.processdefinition;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromResourceIds;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromTenantIds;
import static io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures.createAndSaveProcessDefinition;
import static io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures.createAndSaveProcessDefinitions;
import static io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures.createAndSaveRandomProcessDefinition;
import static io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures.createAndSaveRandomProcessDefinitions;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveRandomProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.read.service.ProcessDefinitionDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionInstanceStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionInstanceVersionStatisticsDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ProcessDefinitionInstanceVersionStatisticsEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.sort.ProcessDefinitionSort;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessDefinitionIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindProcessDefinitionByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionDbReader processDefinitionReader =
        rdbmsService.getProcessDefinitionReader();

    final var processDefinition = ProcessDefinitionFixtures.createRandomized(b -> b);
    createAndSaveProcessDefinition(rdbmsWriters, processDefinition);

    final var instance =
        processDefinitionReader.findOne(processDefinition.processDefinitionKey()).orElse(null);
    assertThat(instance).isNotNull();
    assertThat(instance.processDefinitionKey()).isEqualTo(processDefinition.processDefinitionKey());
    assertThat(instance.processDefinitionId()).isEqualTo(processDefinition.processDefinitionId());
    assertThat(instance.version()).isEqualTo(processDefinition.version());
    assertThat(instance.name()).isEqualTo(processDefinition.name());
    assertThat(instance.resourceName()).isEqualTo(processDefinition.resourceName());
  }

  @TestTemplate
  public void shouldFindProcessInstanceByBpmnProcessId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionDbReader processDefinitionReader =
        rdbmsService.getProcessDefinitionReader();

    final var processDefinition =
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionId("test-process-unique"));
    createAndSaveProcessDefinition(rdbmsWriters, processDefinition);

    final var searchResult =
        processDefinitionReader.search(
            new ProcessDefinitionQuery(
                new ProcessDefinitionFilter.Builder()
                    .processDefinitionIds("test-process-unique")
                    .build(),
                ProcessDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    assertThat(instance.processDefinitionKey()).isEqualTo(processDefinition.processDefinitionKey());
    assertThat(instance.processDefinitionId()).isEqualTo(processDefinition.processDefinitionId());
    assertThat(instance.version()).isEqualTo(processDefinition.version());
    assertThat(instance.name()).isEqualTo(processDefinition.name());
    assertThat(instance.resourceName()).isEqualTo(processDefinition.resourceName());
  }

  @TestTemplate
  public void shouldStoreLongProcessDefinitionId(
      final CamundaRdbmsTestApplication testApplication) {
    final var rdbmsService = testApplication.getRdbmsService();
    final var vendorDatabaseProperties = testApplication.bean(VendorDatabaseProperties.class);
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var processDefinitionReader = rdbmsService.getProcessDefinitionReader();

    final var processDefinitionId =
        RandomStringUtils.insecure()
            .nextAlphanumeric(vendorDatabaseProperties.userCharColumnSize());

    final var processDefinition =
        ProcessDefinitionFixtures.createRandomized(b -> b.processDefinitionId(processDefinitionId));
    createAndSaveProcessDefinition(rdbmsWriter, processDefinition);

    final var searchResult =
        processDefinitionReader.search(
            new ProcessDefinitionQuery(
                new ProcessDefinitionFilter.Builder()
                    .processDefinitionIds(processDefinitionId)
                    .build(),
                ProcessDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().processDefinitionKey())
        .isEqualTo(processDefinition.processDefinitionKey());
  }

  @TestTemplate
  public void shouldFindProcessInstanceByAuthorizationResourceId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionDbReader processDefinitionReader =
        rdbmsService.getProcessDefinitionReader();

    final var processDefinition = createAndSaveRandomProcessDefinition(rdbmsWriters, b -> b);
    createAndSaveRandomProcessDefinitions(rdbmsWriters);

    final var searchResult =
        processDefinitionReader.search(
            ProcessDefinitionQuery.of(b -> b),
            resourceAccessChecksFromResourceIds(
                AuthorizationResourceType.PROCESS_DEFINITION,
                processDefinition.processDefinitionId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().processDefinitionKey())
        .isEqualTo(processDefinition.processDefinitionKey());
  }

  @TestTemplate
  public void shouldFindProcessInstanceByAuthorizationTenantId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionDbReader processDefinitionReader =
        rdbmsService.getProcessDefinitionReader();

    final var processDefinition = createAndSaveRandomProcessDefinition(rdbmsWriters, b -> b);
    createAndSaveRandomProcessDefinitions(rdbmsWriters);

    final var searchResult =
        processDefinitionReader.search(
            ProcessDefinitionQuery.of(b -> b),
            resourceAccessChecksFromTenantIds(processDefinition.tenantId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().processDefinitionKey())
        .isEqualTo(processDefinition.processDefinitionKey());
  }

  @TestTemplate
  public void shouldFindAllProcessDefinitionPaged(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionDbReader processDefinitionReader =
        rdbmsService.getProcessDefinitionReader();

    final String processDefinitionId = ProcessDefinitionFixtures.nextStringId();
    createAndSaveRandomProcessDefinitions(
        rdbmsWriters, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        processDefinitionReader.search(
            new ProcessDefinitionQuery(
                new ProcessDefinitionFilter.Builder()
                    .processDefinitionIds(processDefinitionId)
                    .build(),
                ProcessDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllProcessDefinitionPagedWithHasMoreHits(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionDbReader processDefinitionReader =
        rdbmsService.getProcessDefinitionReader();

    final String processDefinitionId = ProcessDefinitionFixtures.nextStringId();
    createAndSaveRandomProcessDefinitions(
        rdbmsWriters, 120, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        processDefinitionReader.search(
            new ProcessDefinitionQuery(
                new ProcessDefinitionFilter.Builder()
                    .processDefinitionIds(processDefinitionId)
                    .build(),
                ProcessDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(100);
    assertThat(searchResult.hasMoreTotalItems()).isEqualTo(true);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllProcessInstancePageValuesAreNull(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionDbReader processDefinitionReader =
        rdbmsService.getProcessDefinitionReader();

    createAndSaveRandomProcessDefinitions(rdbmsWriters);

    final var searchResult =
        processDefinitionReader.search(
            new ProcessDefinitionQuery(
                new ProcessDefinitionFilter.Builder().build(),
                ProcessDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(null).size(null))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isGreaterThanOrEqualTo(20);
    assertThat(searchResult.items()).hasSizeGreaterThanOrEqualTo(20);
  }

  @TestTemplate
  public void shouldFindProcessInstanceWithFullFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionDbReader processDefinitionReader =
        rdbmsService.getProcessDefinitionReader();

    final var processDefinition = ProcessDefinitionFixtures.createRandomized(b -> b);
    createAndSaveRandomProcessDefinitions(rdbmsWriters);
    createAndSaveProcessDefinition(rdbmsWriters, processDefinition);

    final var searchResult =
        processDefinitionReader.search(
            new ProcessDefinitionQuery(
                new ProcessDefinitionFilter.Builder()
                    .processDefinitionKeys(processDefinition.processDefinitionKey())
                    .processDefinitionIds(processDefinition.processDefinitionId())
                    .names(processDefinition.name())
                    .resourceNames(processDefinition.resourceName())
                    .versions(processDefinition.version())
                    .versionTags(processDefinition.versionTag())
                    .tenantIds(processDefinition.tenantId())
                    .build(),
                ProcessDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().processDefinitionKey())
        .isEqualTo(processDefinition.processDefinitionKey());
  }

  @TestTemplate
  public void shouldFindProcessDefinitionsWithSearchAfter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionDbReader processDefinitionReader =
        rdbmsService.getProcessDefinitionReader();

    createAndSaveRandomProcessDefinitions(rdbmsWriters, b -> b.versionTag("search-after-123456"));
    final var sort =
        ProcessDefinitionSort.of(s -> s.name().asc().version().asc().tenantId().desc());
    final var searchResult =
        processDefinitionReader.search(
            ProcessDefinitionQuery.of(
                b ->
                    b.filter(f -> f.versionTags("search-after-123456"))
                        .sort(sort)
                        .page(p -> p.from(0).size(20))));

    final var firstPage =
        processDefinitionReader.search(
            ProcessDefinitionQuery.of(
                b ->
                    b.filter(f -> f.versionTags("search-after-123456"))
                        .sort(sort)
                        .page(p -> p.size(15))));

    final var nextPage =
        processDefinitionReader.search(
            ProcessDefinitionQuery.of(
                b ->
                    b.filter(f -> f.versionTags("search-after-123456"))
                        .sort(sort)
                        .page(p -> p.size(5).after(firstPage.endCursor()))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(15, 20));
  }

  @TestTemplate
  public void shouldFindProcessDefinitionInstanceStatistics(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionInstanceStatisticsDbReader processDefinitionInstanceStatisticsDbReader =
        rdbmsService.getProcessDefinitionInstanceStatisticsReader();

    final var processDefinition =
        createAndSaveProcessDefinition(
            rdbmsWriters, b -> b.name("proc-1-name").processDefinitionId("proc-1-id").version(1));
    createAndSaveRandomProcessInstance(
        rdbmsWriters,
        b ->
            b.processDefinitionId(processDefinition.processDefinitionId())
                .state(ProcessInstanceState.ACTIVE)
                .processDefinitionKey(processDefinition.processDefinitionKey())
                .version(1)
                .tenantId(processDefinition.tenantId()));

    createAndSaveRandomProcessInstance(
        rdbmsWriters,
        b ->
            b.processDefinitionId(processDefinition.processDefinitionId())
                .state(ProcessInstanceState.ACTIVE)
                .processDefinitionKey(processDefinition.processDefinitionKey())
                .version(1)
                .tenantId(processDefinition.tenantId())
                .numIncidents(1));

    final var searchResult =
        processDefinitionInstanceStatisticsDbReader.aggregate(
            ProcessDefinitionInstanceStatisticsQuery.of(
                b ->
                    b.filter(
                        f -> f.processDefinitionIds(processDefinition.processDefinitionId()))));

    assertThat(searchResult).isNotNull();
    final var statistics =
        searchResult.items().stream()
            .filter(f -> "proc-1-id".equals(f.processDefinitionId()))
            .toList()
            .getFirst();
    assertThat(statistics.processDefinitionId()).isEqualTo(processDefinition.processDefinitionId());
    assertThat(statistics.latestProcessDefinitionName()).isEqualTo(processDefinition.name());
    assertThat(statistics.hasMultipleVersions()).isFalse();
    assertThat(statistics.tenantId()).isEqualTo(processDefinition.tenantId());
    assertThat(statistics.activeInstancesWithIncidentCount()).isEqualTo(1);
    assertThat(statistics.activeInstancesWithoutIncidentCount()).isEqualTo(1);
  }

  @TestTemplate
  public void shouldFindProcessDefinitionInstanceVersionStatistics(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionInstanceVersionStatisticsDbReader
        processDefinitionInstanceVersionStatisticsDbReader =
            rdbmsService.getProcessDefinitionInstanceVersionStatisticsReader();

    final var processDefinitionV1 =
        createAndSaveProcessDefinition(
            rdbmsWriters, b -> b.name("proc-v1-name").version(1).processDefinitionId("proc-2-id"));
    final var processDefinitionV2 =
        createAndSaveProcessDefinition(
            rdbmsWriters, b -> b.name("proc-v2-name").version(2).processDefinitionId("proc-2-id"));

    createAndSaveRandomProcessInstance(
        rdbmsWriters,
        b ->
            b.processDefinitionId(processDefinitionV1.processDefinitionId())
                .processDefinitionKey(processDefinitionV1.processDefinitionKey())
                .state(ProcessInstanceState.ACTIVE)
                .version(1)
                .tenantId(processDefinitionV1.tenantId()));

    createAndSaveRandomProcessInstance(
        rdbmsWriters,
        b ->
            b.processDefinitionId(processDefinitionV2.processDefinitionId())
                .processDefinitionKey(processDefinitionV2.processDefinitionKey())
                .state(ProcessInstanceState.ACTIVE)
                .version(2)
                .tenantId(processDefinitionV2.tenantId())
                .numIncidents(1));

    final var searchResult =
        processDefinitionInstanceVersionStatisticsDbReader.aggregate(
            ProcessDefinitionInstanceVersionStatisticsQuery.of(
                b ->
                    b.filter(
                        f -> f.processDefinitionId(processDefinitionV1.processDefinitionId()))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items())
        .extracting(
            ProcessDefinitionInstanceVersionStatisticsEntity::processDefinitionId,
            ProcessDefinitionInstanceVersionStatisticsEntity::processDefinitionKey,
            ProcessDefinitionInstanceVersionStatisticsEntity::processDefinitionVersion,
            ProcessDefinitionInstanceVersionStatisticsEntity::processDefinitionName,
            ProcessDefinitionInstanceVersionStatisticsEntity::tenantId,
            ProcessDefinitionInstanceVersionStatisticsEntity::activeInstancesWithoutIncidentCount,
            ProcessDefinitionInstanceVersionStatisticsEntity::activeInstancesWithIncidentCount)
        .containsExactlyInAnyOrder(
            tuple(
                processDefinitionV1.processDefinitionId(),
                processDefinitionV1.processDefinitionKey(),
                1,
                processDefinitionV1.name(),
                processDefinitionV1.tenantId(),
                1L,
                0L),
            tuple(
                processDefinitionV2.processDefinitionId(),
                processDefinitionV2.processDefinitionKey(),
                2,
                processDefinitionV2.name(),
                processDefinitionV2.tenantId(),
                0L,
                1L));
  }

  @TestTemplate
  public void shouldDeleteProcessDefinitionsByKey(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionDbReader processDefinitionReader =
        rdbmsService.getProcessDefinitionReader();

    final var processDefinition1 = ProcessDefinitionFixtures.createRandomized(b -> b);
    final var processDefinition2 = ProcessDefinitionFixtures.createRandomized(b -> b);
    createAndSaveProcessDefinitions(rdbmsWriters, List.of(processDefinition1, processDefinition2));
    final var searchResult =
        processDefinitionReader.search(
            new ProcessDefinitionQuery(
                new ProcessDefinitionFilter.Builder()
                    .processDefinitionKeys(
                        processDefinition1.processDefinitionKey(),
                        processDefinition2.processDefinitionKey())
                    .build(),
                ProcessDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));
    assertThat(searchResult.total()).isEqualTo(2);

    // when
    rdbmsWriters
        .getProcessDefinitionWriter()
        .deleteByKeys(
            List.of(
                processDefinition1.processDefinitionKey(),
                processDefinition2.processDefinitionKey()));

    // then
    final var resultAfterDeletion =
        processDefinitionReader.search(
            new ProcessDefinitionQuery(
                new ProcessDefinitionFilter.Builder()
                    .processDefinitionKeys(
                        processDefinition1.processDefinitionKey(),
                        processDefinition2.processDefinitionKey())
                    .build(),
                ProcessDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));
    assertThat(resultAfterDeletion.total()).isZero();
  }
}
