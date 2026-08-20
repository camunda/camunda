/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.clustervariables;

import static io.camunda.it.rdbms.db.fixtures.ClusterVariableFixtures.createAndSaveRandomsTenantClusterVariablesWithFixedTenantAndValue;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.filter.ClusterVariableFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.search.sort.ClusterVariableSort;
import io.camunda.search.sort.ClusterVariableSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ClusterVariableSortIT {

  @TestTemplate
  public void shouldSortByValueAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.value().asc(),
        Comparator.comparing(ClusterVariableEntity::value));
  }

  @TestTemplate
  public void shouldSortByValueDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.value().desc(),
        Comparator.comparing(ClusterVariableEntity::value).reversed());
  }

  @TestTemplate
  public void shouldSortByNameAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.name().asc(),
        Comparator.comparing(ClusterVariableEntity::name));
  }

  @TestTemplate
  public void shouldSortByNameDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.name().desc(),
        Comparator.comparing(ClusterVariableEntity::name).reversed());
  }

  @TestTemplate
  public void shouldSortByScopeAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.scope().asc(),
        Comparator.comparing(ClusterVariableEntity::scope));
  }

  @TestTemplate
  public void shouldSortByScopeDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.scope().desc(),
        Comparator.comparing(ClusterVariableEntity::scope).reversed());
  }

  @TestTemplate
  public void shouldSortByTenantIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().asc(),
        Comparator.comparing(ClusterVariableEntity::tenantId));
  }

  @TestTemplate
  public void shouldSortByTenantIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().desc(),
        Comparator.comparing(ClusterVariableEntity::tenantId).reversed());
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<ClusterVariableSort>> sortBuilder,
      final Comparator<ClusterVariableEntity> comparator) {
    final var tenantId = nextStringId();
    createAndSaveRandomsTenantClusterVariablesWithFixedTenantAndValue(
        rdbmsService, tenantId, "val-" + nextStringId());

    final var searchResult =
        rdbmsService
            .getClusterVariableReader()
            .search(
                new ClusterVariableQuery(
                    new ClusterVariableFilter.Builder()
                        .scopes("TENANT")
                        .tenantIds(tenantId)
                        .build(),
                    ClusterVariableSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b)))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
