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
import io.camunda.it.rdbms.db.util.RdbmsTestTemplate;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.JobSort;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class JobIT {

  public static final int PARTITION_ID = 0;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @RdbmsTestTemplate
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

  @RdbmsTestTemplate
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

  @RdbmsTestTemplate
  public void shouldSaveUpdateAndFindJobWithLargeErrorMessageByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader jobReader = rdbmsService.getJobReader();

    final var errorMessage = "x".repeat(9000);

    final var original = JobFixtures.createRandomized(b -> b);
    createAndSaveJob(rdbmsWriters, original);
    final var update = original.copy(b -> ((JobDbModel.Builder) b).errorMessage(errorMessage));
    rdbmsWriters.getJobWriter().update(update);
    rdbmsWriters.flush();

    final var instance = jobReader.findOne(original.jobKey()).orElse(null);

    assertThat(instance).isNotNull();
    assertThat(instance.errorMessage().length()).isLessThan(errorMessage.length());
  }

  @RdbmsTestTemplate
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

  @RdbmsTestTemplate
  public void shouldFindJobByPartitionId(final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader jobReader = rdbmsService.getJobReader();

    final String processDefinitionId = JobFixtures.nextStringId();
    final int targetPartitionId = 7;
    createAndSaveRandomJobs(
        rdbmsWriters,
        3,
        b -> b.processDefinitionId(processDefinitionId).partitionId(targetPartitionId));
    createAndSaveRandomJobs(
        rdbmsWriters,
        2,
        b -> b.processDefinitionId(processDefinitionId).partitionId(targetPartitionId + 1));

    // when
    final var searchResult =
        jobReader.search(
            JobQuery.of(
                b ->
                    b.filter(
                            f ->
                                f.processDefinitionIds(processDefinitionId)
                                    .partitionId(targetPartitionId))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    // then
    assertThat(searchResult.total()).isEqualTo(3);
    assertThat(searchResult.items()).hasSize(3);
  }

  @RdbmsTestTemplate
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

  @RdbmsTestTemplate
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

  @RdbmsTestTemplate
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

  @RdbmsTestTemplate
  public void shouldFindAllJobPagedWithHasMoreHits(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader processInstanceReader = rdbmsService.getJobReader();

    final String processDefinitionId = JobFixtures.nextStringId();
    createAndSaveRandomJobs(rdbmsWriters, 120, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        processInstanceReader.search(
            JobQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinitionId))
                        .sort(s -> s.deadline().asc().elementId().asc())
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(100);
    assertThat(searchResult.hasMoreTotalItems()).isEqualTo(true);
    assertThat(searchResult.items()).hasSize(5);
  }

  @RdbmsTestTemplate
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

  @RdbmsTestTemplate
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
        // customHeaders may be null on db record level but defaults to an empty Map on the Entity
        .ignoringFields("endTime", "deadline", "creationTime", "lastUpdateTime", "customHeaders")
        .isEqualTo(original);
    assertThat(instance.jobKey()).isEqualTo(original.jobKey());
    assertThat(instance.processDefinitionId()).isEqualTo(original.processDefinitionId());
    assertThat(instance.deadline())
        .isCloseTo(original.deadline(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
  }

  @RdbmsTestTemplate
  public void shouldUpdateJobWithoutOverwritingNullableFieldsWithNull(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader jobReader = rdbmsService.getJobReader();

    final var original =
        JobFixtures.createRandomized(
            b ->
                b.elementId("original-element-id")
                    .errorMessage("original-error")
                    .errorCode("original-code")
                    .isDenied(true)
                    .deniedReason("original-denied-reason"));
    createAndSaveJob(rdbmsWriters, original);

    // when — update with a partial model where @Nullable and conditionally-set fields are null
    final var update =
        new JobDbModel.Builder()
            .jobKey(original.jobKey())
            .type(original.type())
            .worker(original.worker())
            .state(original.state())
            .kind(original.kind())
            .listenerEventType(original.listenerEventType())
            .retries(original.retries())
            .priority(original.priority())
            .hasFailedWithRetriesLeft(original.hasFailedWithRetriesLeft())
            .processDefinitionId(original.processDefinitionId())
            .processDefinitionKey(original.processDefinitionKey())
            .processInstanceKey(original.processInstanceKey())
            .elementInstanceKey(original.elementInstanceKey())
            .tenantId(original.tenantId())
            .partitionId(original.partitionId())
            .lastUpdateTime(NOW)
            // intentionally omit: elementId, creationTime, errorMessage, errorCode, isDenied,
            // deniedReason, deadline, endTime, rootProcessInstanceKey
            .build();
    rdbmsWriters.getJobWriter().update(update);
    rdbmsWriters.flush();

    // then — fields not carried by the update should retain their original values
    final var stored = jobReader.findOne(original.jobKey()).orElseThrow();
    assertThat(stored.elementId()).isEqualTo("original-element-id");
    assertThat(stored.errorMessage()).isEqualTo("original-error");
    assertThat(stored.errorCode()).isEqualTo("original-code");
    assertThat(stored.isDenied()).isEqualTo(true);
    assertThat(stored.deniedReason()).isEqualTo("original-denied-reason");
    assertThat(stored.deadline())
        .isCloseTo(original.deadline(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(stored.endTime())
        .isCloseTo(original.endTime(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(stored.rootProcessInstanceKey()).isEqualTo(original.rootProcessInstanceKey());
    assertThat(stored.priority()).isEqualTo(original.priority());
  }

  @RdbmsTestTemplate
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

  @RdbmsTestTemplate
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

  @RdbmsTestTemplate
  public void shouldFindJobByPriorityFilter(final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader jobReader = rdbmsService.getJobReader();

    final var processDefinitionKey = nextKey();
    final var target =
        createAndSaveJob(
            rdbmsWriters, b -> b.priority(99).processDefinitionKey(processDefinitionKey));
    for (int i = 0; i < 5; i++) {
      createAndSaveJob(rdbmsWriters, b -> b.priority(5).processDefinitionKey(processDefinitionKey));
    }

    // when / then
    final var exactResult = searchByPriority(jobReader, processDefinitionKey, Operation.eq(99));
    assertThat(exactResult.total()).isEqualTo(1);
    assertThat(exactResult.items().getFirst().jobKey()).isEqualTo(target.jobKey());
    assertThat(exactResult.items().getFirst().priority()).isEqualTo(99);

    // when / then
    final var rangeResult = searchByPriority(jobReader, processDefinitionKey, Operation.gte(95));
    assertThat(rangeResult.total()).isEqualTo(1);
    assertThat(rangeResult.items().getFirst().jobKey()).isEqualTo(target.jobKey());
  }

  @RdbmsTestTemplate
  public void shouldFindJobSortedByPriority(final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader jobReader = rdbmsService.getJobReader();

    final var processDefinitionKey = nextKey();
    final var low =
        createAndSaveJob(
            rdbmsWriters, b -> b.priority(10).processDefinitionKey(processDefinitionKey));
    final var mid =
        createAndSaveJob(
            rdbmsWriters, b -> b.priority(50).processDefinitionKey(processDefinitionKey));
    final var high =
        createAndSaveJob(
            rdbmsWriters, b -> b.priority(90).processDefinitionKey(processDefinitionKey));

    // when / then — descending
    final var descResult = searchSortedByPriority(jobReader, processDefinitionKey, false);
    assertThat(descResult.total()).isEqualTo(3);
    assertThat(descResult.items().stream().map(JobEntity::jobKey))
        .containsExactly(high.jobKey(), mid.jobKey(), low.jobKey());

    // when / then — ascending
    final var ascResult = searchSortedByPriority(jobReader, processDefinitionKey, true);
    assertThat(ascResult.items().stream().map(JobEntity::jobKey))
        .containsExactly(low.jobKey(), mid.jobKey(), high.jobKey());
  }

  @RdbmsTestTemplate
  public void shouldExcludeNullPriorityJobsFromPriorityFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given — a job with NULL priority (pre-8.10) and a job with explicit priority
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader jobReader = rdbmsService.getJobReader();

    final var processDefinitionKey = nextKey();
    createAndSaveJob(
        rdbmsWriters, b -> b.priority(null).processDefinitionKey(processDefinitionKey));
    final var explicitPriorityJob =
        createAndSaveJob(
            rdbmsWriters, b -> b.priority(5).processDefinitionKey(processDefinitionKey));

    // when — filter includes values that NULL would match if treated as 0 (e.g. lte(0))
    final var lteZeroResult = searchByPriority(jobReader, processDefinitionKey, Operation.lte(0));

    // then — the NULL-priority job is excluded; only explicit-priority jobs satisfying the
    // comparison are returned
    assertThat(lteZeroResult.total()).isEqualTo(0);

    // when — filter that the explicit-priority job satisfies
    final var gteOneResult = searchByPriority(jobReader, processDefinitionKey, Operation.gte(1));

    // then — only the explicit-priority job is returned
    assertThat(gteOneResult.total()).isEqualTo(1);
    assertThat(gteOneResult.items().getFirst().jobKey()).isEqualTo(explicitPriorityJob.jobKey());
  }

  @SafeVarargs
  private static SearchQueryResult<JobEntity> searchByPriority(
      final JobDbReader reader, final long processDefinitionKey, final Operation<Integer>... ops) {
    return reader.search(
        JobQuery.of(
            b ->
                b.filter(
                        f ->
                            f.processDefinitionKeys(processDefinitionKey)
                                .priorityOperations(List.of(ops)))
                    .page(p -> p.from(0).size(20))));
  }

  private static SearchQueryResult<JobEntity> searchSortedByPriority(
      final JobDbReader reader, final long processDefinitionKey, final boolean ascending) {
    return reader.search(
        JobQuery.of(
            b ->
                b.filter(f -> f.processDefinitionKeys(processDefinitionKey))
                    .sort(s -> ascending ? s.priority().asc() : s.priority().desc())
                    .page(p -> p.from(0).size(20))));
  }
}
