/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.variables;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.createAndSaveRandomVariables;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.VariableSort;
import io.camunda.search.sort.VariableSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class VariableSortIT {

  @TestTemplate
  public void shouldSortByVariableKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.variableKey().asc(),
        Comparator.comparing(VariableEntity::variableKey));
  }

  @TestTemplate
  public void shouldSortByVariableKeyDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.variableKey().desc(),
        Comparator.comparing(VariableEntity::variableKey).reversed());
  }

  @TestTemplate
  public void shouldSortByValueAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.value().asc(),
        Comparator.comparing(VariableEntity::value));
  }

  @TestTemplate
  public void shouldSortByValueDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.value().desc(),
        Comparator.comparing(VariableEntity::value).reversed());
  }

  @TestTemplate
  public void shouldSortByProcessInstanceKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processInstanceKey().asc(),
        Comparator.comparing(VariableEntity::processInstanceKey));
  }

  @TestTemplate
  public void shouldSortByProcessInstanceKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processInstanceKey().desc(),
        Comparator.comparing(VariableEntity::processInstanceKey).reversed());
  }

  @TestTemplate
  public void shouldSortByTenantIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().asc(),
        Comparator.comparing(VariableEntity::tenantId));
  }

  @TestTemplate
  public void shouldSortByTenantIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().desc(),
        Comparator.comparing(VariableEntity::tenantId).reversed());
  }

  @TestTemplate
  public void shouldSortByScopeKeyAsc(final CamundaRdbmsTestApplication testApplication) {

    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final var processInstanceKey = nextKey(); // Our Test Scope
    createAndSaveRandomVariables(rdbmsService, b -> b.processInstanceKey(processInstanceKey));

    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    new VariableFilter.Builder().processInstanceKeys(processInstanceKey).build(),
                    VariableSort.of(b -> b.scopeKey().asc()),
                    SearchQueryPage.of(b -> b)))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(Comparator.comparing(VariableEntity::scopeKey));
  }

  @TestTemplate
  public void shouldSortByScopeKeyDesc(final CamundaRdbmsTestApplication testApplication) {

    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final var processInstanceKey = nextKey(); // Our Test Scope
    createAndSaveRandomVariables(rdbmsService, b -> b.processInstanceKey(processInstanceKey));

    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    new VariableFilter.Builder().processInstanceKeys(processInstanceKey).build(),
                    VariableSort.of(b -> b.scopeKey().desc()),
                    SearchQueryPage.of(b -> b)))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult)
        .isSortedAccordingTo(Comparator.comparing(VariableEntity::scopeKey).reversed());
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<VariableSort>> sortBuilder,
      final Comparator<VariableEntity> comparator) {
    final var scopeKey =
        nextKey(); // This is for our test scope, since the DB isn't reset after a test
    createAndSaveRandomVariables(
        rdbmsService, b -> b.scopeKey(scopeKey).value("val-" + nextStringId()));

    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    new VariableFilter.Builder().scopeKeys(scopeKey).build(),
                    VariableSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b)))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
