/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.decisionrequirements;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static io.camunda.it.rdbms.db.fixtures.DecisionRequirementsFixtures.createAndSaveRandomDecisionRequirements;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.DecisionRequirementsReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.sort.DecisionRequirementsSort;
import io.camunda.search.sort.DecisionRequirementsSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class DecisionRequirementsSortIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSortByDecisionRequirementsIdAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionRequirementsId().asc(),
        Comparator.comparing(DecisionRequirementsEntity::decisionRequirementsId));
  }

  @TestTemplate
  public void shouldSortByDecisionRequirementsIdDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionRequirementsId().desc(),
        Comparator.comparing(DecisionRequirementsEntity::decisionRequirementsId).reversed());
  }

  @TestTemplate
  public void shouldSortByDecisionRequirementsKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionRequirementsKey().asc(),
        Comparator.comparing(DecisionRequirementsEntity::decisionRequirementsKey));
  }

  @TestTemplate
  public void shouldSortByDecisionRequirementsKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionRequirementsKey().desc(),
        Comparator.comparing(DecisionRequirementsEntity::decisionRequirementsKey).reversed());
  }

  @TestTemplate
  public void shouldSortByNameAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.name().asc(),
        Comparator.comparing(DecisionRequirementsEntity::name));
  }

  @TestTemplate
  public void shouldSortByNameDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.name().desc(),
        Comparator.comparing(DecisionRequirementsEntity::name).reversed());
  }

  @TestTemplate
  public void shouldSortByVersionAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.version().asc(),
        Comparator.comparing(DecisionRequirementsEntity::version));
  }

  @TestTemplate
  public void shouldSortByVersionDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.version().desc(),
        Comparator.comparing(DecisionRequirementsEntity::version).reversed());
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<DecisionRequirementsSort>> sortBuilder,
      final Comparator<DecisionRequirementsEntity> comparator) {
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final DecisionRequirementsReader reader = rdbmsService.getDecisionRequirementsReader();

    final var tenantId = nextStringId();
    createAndSaveRandomDecisionRequirements(rdbmsWriter, b -> b.tenantId(tenantId));

    final var searchResult =
        reader
            .search(
                new DecisionRequirementsQuery(
                    new DecisionRequirementsFilter.Builder().tenantIds(tenantId).build(),
                    DecisionRequirementsSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b),
                    null))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
