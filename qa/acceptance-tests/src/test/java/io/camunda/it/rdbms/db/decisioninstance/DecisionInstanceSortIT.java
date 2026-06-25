/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.decisioninstance;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures.createAndSaveRandomDecisionInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.DecisionInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.fixtures.DecisionDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.it.rdbms.db.util.RdbmsTestTemplate;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.sort.DecisionInstanceSort;
import io.camunda.search.sort.DecisionInstanceSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class DecisionInstanceSortIT {

  public static final Long PARTITION_ID = 0L;

  @RdbmsTestTemplate
  public void shouldSortByDecisionInstanceIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionInstanceId().asc(),
        Comparator.comparing(DecisionInstanceEntity::decisionInstanceId));
  }

  @RdbmsTestTemplate
  public void shouldSortByDecisionInstanceIdDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionInstanceId().desc(),
        Comparator.comparing(DecisionInstanceEntity::decisionInstanceId).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByDecisionInstanceKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionInstanceKey().asc(),
        Comparator.comparing(DecisionInstanceEntity::decisionInstanceKey));
  }

  @RdbmsTestTemplate
  public void shouldSortByDecisionInstanceKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionInstanceKey().desc(),
        Comparator.comparing(DecisionInstanceEntity::decisionInstanceKey).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByProcessInstanceKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processInstanceKey().desc(),
        Comparator.comparing(DecisionInstanceEntity::processInstanceKey).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByProcessInstanceKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processInstanceKey().asc(),
        Comparator.comparing(DecisionInstanceEntity::processInstanceKey));
  }

  @RdbmsTestTemplate
  public void shouldSortByFlowNodeInstanceKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.flowNodeInstanceKey().desc(),
        Comparator.comparing(DecisionInstanceEntity::flowNodeInstanceKey).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByElementInstanceKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.flowNodeInstanceKey().asc(),
        Comparator.comparing(DecisionInstanceEntity::flowNodeInstanceKey));
  }

  @RdbmsTestTemplate
  public void shouldSortByDecisionDefinitionKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    // TODO this makes no sense since builder.decisionDefinitionKey sets the field to "decisionId"
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionDefinitionId().asc(),
        Comparator.comparing(DecisionInstanceEntity::decisionDefinitionId));
  }

  @RdbmsTestTemplate
  public void shouldSortByDecisionDefinitionKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionDefinitionKey().desc(),
        Comparator.comparing(DecisionInstanceEntity::decisionDefinitionKey).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByDecisionDefinitionName(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionDefinitionName().asc(),
        Comparator.comparing(DecisionInstanceEntity::decisionDefinitionName));
  }

  @RdbmsTestTemplate
  public void shouldSortByDecisionDefinitionVersion(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionDefinitionVersion().asc(),
        Comparator.comparing(DecisionInstanceEntity::decisionDefinitionVersion));
  }

  @RdbmsTestTemplate
  public void shouldSortByEvaluationDate(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.evaluationDate().asc(),
        Comparator.comparing(DecisionInstanceEntity::evaluationDate));
  }

  @RdbmsTestTemplate
  public void shouldSortByEvaluationDateDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.evaluationDate().desc(),
        Comparator.comparing(DecisionInstanceEntity::evaluationDate).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByState(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.state().asc(),
        Comparator.comparing(DecisionInstanceEntity::state));
  }

  @RdbmsTestTemplate
  public void shouldSortByRootDecisionDefinitionKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.rootDecisionDefinitionKey().asc(),
        Comparator.comparing(DecisionInstanceEntity::rootDecisionDefinitionKey));
  }

  @RdbmsTestTemplate
  public void shouldSortByRootDecisionDefinitionKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.rootDecisionDefinitionKey().desc(),
        Comparator.comparing(DecisionInstanceEntity::rootDecisionDefinitionKey).reversed());
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<DecisionInstanceSort>> sortBuilder,
      final Comparator<DecisionInstanceEntity> comparator) {
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader reader = rdbmsService.getDecisionInstanceReader();

    final var decisionDefinition =
        DecisionDefinitionFixtures.createAndSaveDecisionDefinition(rdbmsWriters, b -> b);
    final var processDefinitionKey = nextKey();
    createAndSaveRandomDecisionInstances(
        rdbmsWriters,
        b ->
            b.processDefinitionKey(processDefinitionKey)
                .decisionDefinitionId(decisionDefinition.decisionDefinitionId())
                .decisionDefinitionKey(decisionDefinition.decisionDefinitionKey()));

    final var searchResult =
        reader
            .search(
                new DecisionInstanceQuery(
                    new DecisionInstanceFilter.Builder()
                        .processDefinitionKeys(processDefinitionKey)
                        .build(),
                    DecisionInstanceSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b),
                    null))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
