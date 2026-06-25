/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.job;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static io.camunda.it.rdbms.db.fixtures.JobFixtures.createAndSaveRandomJobs;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.JobDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.it.rdbms.db.util.RdbmsTestTemplate;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.filter.JobFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.JobQuery;
import io.camunda.search.sort.JobSort;
import io.camunda.search.sort.JobSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class JobSortIT {

  public static final long PARTITION_ID = 0L;

  @RdbmsTestTemplate
  public void shouldSortByJobKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.jobKey().asc(),
        Comparator.comparing(JobEntity::jobKey));
  }

  @RdbmsTestTemplate
  public void shouldSortByJobKeyDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.jobKey().desc(),
        Comparator.comparing(JobEntity::jobKey).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByTypeAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.type().asc(),
        Comparator.comparing(JobEntity::type));
  }

  @RdbmsTestTemplate
  public void shouldSortByTypeDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.type().desc(),
        Comparator.comparing(JobEntity::type).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByWorkerAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.worker().asc(),
        Comparator.comparing(JobEntity::worker));
  }

  @RdbmsTestTemplate
  public void shouldSortByWorkerDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.worker().desc(),
        Comparator.comparing(JobEntity::worker).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByTenantIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().asc(),
        Comparator.comparing(JobEntity::tenantId));
  }

  @RdbmsTestTemplate
  public void shouldSortByTenantIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().desc(),
        Comparator.comparing(JobEntity::tenantId).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByProcessInstanceKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processInstanceKey().asc(),
        Comparator.comparing(JobEntity::processInstanceKey));
  }

  @RdbmsTestTemplate
  public void shouldSortByProcessInstanceKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processInstanceKey().desc(),
        Comparator.comparing(JobEntity::processInstanceKey).reversed());
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<JobSort>> sortBuilder,
      final Comparator<JobEntity> comparator) {
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final JobDbReader reader = rdbmsService.getJobReader();

    final var processDefinitionId = nextStringId();
    createAndSaveRandomJobs(rdbmsWriters, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        reader
            .search(
                new JobQuery(
                    new JobFilter.Builder().processDefinitionIds(processDefinitionId).build(),
                    JobSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b)))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
