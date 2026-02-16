/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.job;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromResourceIds;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromTenantIds;
import static io.camunda.it.rdbms.db.fixtures.JobFixtures.createAndSaveJob;
import static io.camunda.it.rdbms.db.fixtures.JobFixtures.createAndSaveRandomJobs;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.JobDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.it.rdbms.db.fixtures.JobFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.query.JobQuery;
import io.camunda.search.sort.JobSort;
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
public class JobIT {

  public static final int PARTITION_ID = 0;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindJobByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader processInstanceReader = rdbmsService.getJobReader();

    final var original = JobFixtures.createRandomized(b -> b);
    createAndSaveJob(rdbmsWriters, original);

    final var instance = processInstanceReader.findOne(original.jobKey()).orElse(null);

    compareJob(instance, original);

    // assert that boolean values are not null and have expected values
    assertThat(instance.hasFailedWithRetriesLeft()).isNotNull();
    assertThat(instance.hasFailedWithRetriesLeft()).isFalse();
    assertThat(instance.isDenied()).isNotNull();
    assertThat(instance.isDenied()).isFalse();
  }

  @TestTemplate
  public void shouldSaveAndFindJobWithLargeErrorMessageByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader jobReader = rdbmsService.getJobReader();

    final var original = JobFixtures.createRandomized(b -> b.errorMessage("x".repeat(9000)));
    createAndSaveJob(rdbmsWriters, original);

    final var instance = jobReader.findOne(original.jobKey()).orElse(null);

    assertThat(instance).isNotNull();
    assertThat(instance.errorMessage().length()).isLessThan(original.errorMessage().length());
  }

  @TestTemplate
  public void shouldFindJobByProcessDefinitionId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader processInstanceReader = rdbmsService.getJobReader();

    final var original = JobFixtures.createRandomized(b -> b);
    createAndSaveJob(rdbmsWriters, original);

    final var searchResult =
        processInstanceReader.search(
            JobQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(original.processDefinitionId()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    compareJob(instance, original);
  }

  @TestTemplate
  public void shouldFindJobByAuthorizedResourceId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader processInstanceReader = rdbmsService.getJobReader();

    final var original = JobFixtures.createRandomized(b -> b);
    createAndSaveJob(rdbmsWriters, original);
    createAndSaveRandomJobs(rdbmsWriters);

    final var searchResult =
        processInstanceReader.search(
            JobQuery.of(b -> b),
            resourceAccessChecksFromResourceIds(
                AuthorizationResourceType.PROCESS_DEFINITION, original.processDefinitionId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    compareJob(searchResult.items().getFirst(), original);
  }

  @TestTemplate
  public void shouldFindJobByAuthorizedTenantId(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader processInstanceReader = rdbmsService.getJobReader();

    final var original = JobFixtures.createRandomized(b -> b);
    createAndSaveJob(rdbmsWriters, original);
    createAndSaveRandomJobs(rdbmsWriters);

    final var searchResult =
        processInstanceReader.search(
            JobQuery.of(b -> b), resourceAccessChecksFromTenantIds(original.tenantId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    compareJob(searchResult.items().getFirst(), original);
  }

  @TestTemplate
  public void shouldFindAllJobPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader processInstanceReader = rdbmsService.getJobReader();

    final String processDefinitionId = JobFixtures.nextStringId();
    createAndSaveRandomJobs(rdbmsWriters, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        processInstanceReader.search(
            JobQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinitionId))
                        .sort(s -> s.deadline().asc().elementId().asc())
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindJobWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader processInstanceReader = rdbmsService.getJobReader();

    final var original = JobFixtures.createRandomized(b -> b);
    createAndSaveJob(rdbmsWriters, original);
    createAndSaveRandomJobs(rdbmsWriters);

    final var searchResult =
        processInstanceReader.search(
            JobQuery.of(
                b ->
                    b.filter(
                            f ->
                                f.jobKeys(original.jobKey())
                                    .processInstanceKeys(original.processInstanceKey())
                                    .processDefinitionIds(original.processDefinitionId())
                                    .processDefinitionKeys(original.processDefinitionKey())
                                    .states(original.state().name())
                                    .errorMessages(original.errorMessage())
                                    .elementInstanceKeys(original.elementInstanceKey())
                                    .elementIds(original.elementId())
                                    .jobKeys(original.jobKey())
                                    .tenantIds(original.tenantId()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().jobKey()).isEqualTo(original.jobKey());
  }

  @TestTemplate
  public void shouldFindJobWithSearchAfter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader processInstanceReader = rdbmsService.getJobReader();

    final var processDefinitionKey = nextKey();
    createAndSaveRandomJobs(rdbmsWriters, b -> b.processDefinitionKey(processDefinitionKey));
    final var sort = JobSort.of(s -> s.state().asc().deadline().asc().elementId().desc());
    final var searchResult =
        processInstanceReader.search(
            JobQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(processDefinitionKey))
                        .sort(sort)
                        .page(p -> p.from(0).size(20))));

    final var firstPage =
        processInstanceReader.search(
            JobQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(processDefinitionKey))
                        .sort(sort)
                        .page(p -> p.size(15))));

    final var nextPage =
        processInstanceReader.search(
            JobQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(processDefinitionKey))
                        .sort(sort)
                        .page(p -> p.size(5).after(firstPage.endCursor()))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(15, 20));
  }

  private void compareJob(final JobEntity instance, final JobDbModel original) {
    assertThat(instance).isNotNull();
    assertThat(instance)
        .usingRecursiveComparison()
        // date fields are ignored because different engines produce different precisions
        // e.g., date may look like 2025-11-21T16:02:57.376Z or 2025-11-21T16:02:57.376207580Z
        .ignoringFields("endTime", "deadline", "creationTime", "lastUpdateTime")
        .isEqualTo(original);
    assertThat(instance.jobKey()).isEqualTo(original.jobKey());
    assertThat(instance.processDefinitionId()).isEqualTo(original.processDefinitionId());
    assertThat(instance.deadline())
        .isCloseTo(original.deadline(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
  }

  @TestTemplate
  public void shouldDeleteProcessInstanceRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader reader = rdbmsService.getJobReader();

    final var definition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriters, b -> b);
    final var item1 =
        createAndSaveJob(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item2 =
        createAndSaveJob(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item3 =
        createAndSaveJob(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));

    // when
    final int deleted =
        rdbmsWriters
            .getJobWriter()
            .deleteProcessInstanceRelatedData(List.of(item2.processInstanceKey()), 10);

    // then
    assertThat(deleted).isEqualTo(1);
    final var searchResult =
        reader.search(
            JobQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(definition.processDefinitionKey()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(JobEntity::jobKey))
        .containsExactlyInAnyOrder(item1.jobKey(), item3.jobKey());
  }

  @TestTemplate
  public void shouldDeleteRootProcessInstanceRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader reader = rdbmsService.getJobReader();

    final var definition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriters, b -> b);
    final var item1 =
        createAndSaveJob(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item2 =
        createAndSaveJob(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item3 =
        createAndSaveJob(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));

    // when
    final int deleted =
        rdbmsWriters
            .getJobWriter()
            .deleteRootProcessInstanceRelatedData(List.of(item2.rootProcessInstanceKey()), 10);

    // then
    assertThat(deleted).isEqualTo(1);
    final var searchResult =
        reader.search(
            JobQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(definition.processDefinitionKey()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(JobEntity::jobKey))
        .containsExactlyInAnyOrder(item1.jobKey(), item3.jobKey());
  }
}
