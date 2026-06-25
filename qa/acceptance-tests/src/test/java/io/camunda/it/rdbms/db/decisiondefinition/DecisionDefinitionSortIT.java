/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.decisiondefinition;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.DecisionDefinitionFixtures.createAndSaveRandomDecisionDefinitions;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.DecisionDefinitionDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.it.rdbms.db.util.RdbmsTestTemplate;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.sort.DecisionDefinitionSort;
import io.camunda.search.sort.DecisionDefinitionSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class DecisionDefinitionSortIT {

  public static final Long PARTITION_ID = 0L;

  @RdbmsTestTemplate
  public void shouldSortByDecisionDefinitionIdAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionDefinitionId().asc(),
        Comparator.comparing(DecisionDefinitionEntity::decisionDefinitionId));
  }

  @RdbmsTestTemplate
  public void shouldSortByDecisionDefinitionIdDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionDefinitionId().desc(),
        Comparator.comparing(DecisionDefinitionEntity::decisionDefinitionId).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByDecisionDefinitionKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionDefinitionKey().asc(),
        Comparator.comparing(DecisionDefinitionEntity::decisionDefinitionKey));
  }

  @RdbmsTestTemplate
  public void shouldSortByDecisionDefinitionKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionDefinitionKey().desc(),
        Comparator.comparing(DecisionDefinitionEntity::decisionDefinitionKey).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByNameAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.name().asc(),
        Comparator.comparing(DecisionDefinitionEntity::name));
  }

  @RdbmsTestTemplate
  public void shouldSortByNameDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.name().desc(),
        Comparator.comparing(DecisionDefinitionEntity::name).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByRequirementsIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionRequirementsId().asc(),
        Comparator.comparing(DecisionDefinitionEntity::decisionRequirementsId));
  }

  @RdbmsTestTemplate
  public void shouldSortByRequirementsIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionRequirementsId().desc(),
        Comparator.comparing(DecisionDefinitionEntity::decisionRequirementsId).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByVersionAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.version().asc(),
        Comparator.comparing(DecisionDefinitionEntity::version));
  }

  @RdbmsTestTemplate
  public void shouldSortByVersionDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.version().desc(),
        Comparator.comparing(DecisionDefinitionEntity::version).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByRequirementsNameAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionRequirementsName().asc(),
        Comparator.comparing(DecisionDefinitionEntity::decisionRequirementsName));
  }

  @RdbmsTestTemplate
  public void shouldSortByRequirementsNameDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionRequirementsName().desc(),
        Comparator.comparing(DecisionDefinitionEntity::decisionRequirementsName).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByRequirementsVersionAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionRequirementsVersion().asc(),
        Comparator.comparing(DecisionDefinitionEntity::decisionRequirementsVersion));
  }

  @RdbmsTestTemplate
  public void shouldSortByRequirementsVersionDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionRequirementsVersion().desc(),
        Comparator.comparing(DecisionDefinitionEntity::decisionRequirementsVersion).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByTenantIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().asc(),
        Comparator.comparing(DecisionDefinitionEntity::tenantId));
  }

  @RdbmsTestTemplate
  public void shouldSortByTenantIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().desc(),
        Comparator.comparing(DecisionDefinitionEntity::tenantId).reversed());
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<DecisionDefinitionSort>> sortBuilder,
      final Comparator<DecisionDefinitionEntity> comparator) {
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionDefinitionDbReader reader = rdbmsService.getDecisionDefinitionReader();

    final var requirementsKey = nextKey();
    createAndSaveRandomDecisionDefinitions(
        rdbmsWriters, b -> b.decisionRequirementsKey(requirementsKey));

    final var searchResult =
        reader
            .search(
                new DecisionDefinitionQuery(
                    new DecisionDefinitionFilter.Builder()
                        .decisionRequirementsKeys(requirementsKey)
                        .build(),
                    DecisionDefinitionSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b)))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
