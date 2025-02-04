/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.authorization;

import static io.camunda.it.rdbms.db.fixtures.AuthorizationFixtures.createAndSaveRandomAuthorizations;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.AuthorizationReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.sort.AuthorizationSort;
import io.camunda.search.sort.AuthorizationSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AuthorizationSortIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSortByOwnerKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.ownerId().asc(),
        Comparator.comparing(AuthorizationEntity::ownerId));
  }

  @TestTemplate
  public void shouldSortByOwnerKeyDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.ownerId().desc(),
        Comparator.comparing(AuthorizationEntity::ownerId).reversed());
  }

  @TestTemplate
  public void shouldSortByOwnerTypeAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.ownerType().asc(),
        Comparator.comparing(AuthorizationEntity::ownerType));
  }

  @TestTemplate
  public void shouldSortByOwnerTypeDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.ownerType().desc(),
        Comparator.comparing(AuthorizationEntity::ownerType).reversed());
  }

  @TestTemplate
  public void shouldSortByResourceTypeAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.resourceType().asc(),
        Comparator.comparing(AuthorizationEntity::resourceType));
  }

  @TestTemplate
  public void shouldSortByResourceTypeDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.resourceType().desc(),
        Comparator.comparing(AuthorizationEntity::resourceType).reversed());
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<AuthorizationSort>> sortBuilder,
      final Comparator<AuthorizationEntity> comparator) {
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final AuthorizationReader reader = rdbmsService.getAuthorizationReader();

    final var requirementsKey = nextKey();
    createAndSaveRandomAuthorizations(rdbmsWriter, b -> b);

    final var searchResult =
        reader
            .search(
                new AuthorizationQuery(
                    new AuthorizationFilter.Builder().build(),
                    AuthorizationSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b)))
            .items();

    //    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
