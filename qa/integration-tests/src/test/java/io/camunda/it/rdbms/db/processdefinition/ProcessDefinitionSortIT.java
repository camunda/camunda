/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.processdefinition;

import static io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures.createAndSaveRandomProcessDefinitions;
import static io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.ProcessDefinitionReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.sort.ProcessDefinitionSort;
import io.camunda.search.sort.ProcessDefinitionSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessDefinitionSortIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSortByProcessDefinitionIdAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processDefinitionId().asc(),
        Comparator.comparing(ProcessDefinitionEntity::processDefinitionId));
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionIdDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processDefinitionId().desc(),
        Comparator.comparing(ProcessDefinitionEntity::processDefinitionId).reversed());
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processDefinitionKey().asc(),
        Comparator.comparing(ProcessDefinitionEntity::processDefinitionKey));
  }

  @TestTemplate
  public void shouldSortByProcessDefinitionKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processDefinitionKey().desc(),
        Comparator.comparing(ProcessDefinitionEntity::processDefinitionKey).reversed());
  }

  @TestTemplate
  public void shouldSortByNameAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.name().asc(),
        Comparator.comparing(ProcessDefinitionEntity::name));
  }

  @TestTemplate
  public void shouldSortByNameDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.name().desc(),
        Comparator.comparing(ProcessDefinitionEntity::name).reversed());
  }

  @TestTemplate
  public void shouldSortByResourceNameAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.resourceName().asc(),
        Comparator.comparing(ProcessDefinitionEntity::resourceName));
  }

  @TestTemplate
  public void shouldSortByResourceNameDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.resourceName().desc(),
        Comparator.comparing(ProcessDefinitionEntity::resourceName).reversed());
  }

  @TestTemplate
  public void shouldSortByVersionAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.version().asc(),
        Comparator.comparing(ProcessDefinitionEntity::version));
  }

  @TestTemplate
  public void shouldSortByVersionDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.version().desc(),
        Comparator.comparing(ProcessDefinitionEntity::version).reversed());
  }

  @TestTemplate
  public void shouldSortByTenantIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().asc(),
        Comparator.comparing(ProcessDefinitionEntity::tenantId));
  }

  @TestTemplate
  public void shouldSortByTenantIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().desc(),
        Comparator.comparing(ProcessDefinitionEntity::tenantId).reversed());
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<ProcessDefinitionSort>> sortBuilder,
      final Comparator<ProcessDefinitionEntity> comparator) {
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionReader reader = rdbmsService.getProcessDefinitionReader();

    final var versionTag = nextStringId();
    createAndSaveRandomProcessDefinitions(rdbmsWriter, b -> b.versionTag(versionTag));

    final var searchResult =
        reader
            .search(
                new ProcessDefinitionQuery(
                    new ProcessDefinitionFilter.Builder().versionTags(versionTag).build(),
                    ProcessDefinitionSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b)))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
