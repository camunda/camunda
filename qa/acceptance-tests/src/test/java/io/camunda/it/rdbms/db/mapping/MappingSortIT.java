/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.mapping;

import static io.camunda.it.rdbms.db.fixtures.MappingFixtures.createAndSaveRandomMappings;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.it.rdbms.db.fixtures.MappingFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.filter.MappingFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.sort.MappingSort;
import java.util.Comparator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class MappingSortIT {

  @TestTemplate
  public void shouldSortMappingsByClaimNameAsc(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String claimName = "claimName-" + MappingFixtures.nextStringId();
    createAndSaveRandomMappings(rdbmsService, b -> b.claimName(claimName));

    final var searchResult =
        rdbmsService
            .getMappingReader()
            .search(
                new MappingQuery(
                    new MappingFilter.Builder().claimName(claimName).build(),
                    MappingSort.of(b -> b.claimName().asc()),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isSortedAccordingTo(Comparator.comparing(MappingEntity::claimName));
  }

  @TestTemplate
  public void shouldSortMappingsByClaimNameDesc(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String claimName = "claimName-" + MappingFixtures.nextStringId();
    createAndSaveRandomMappings(rdbmsService, b -> b.claimName(claimName));

    final var searchResult =
        rdbmsService
            .getMappingReader()
            .search(
                new MappingQuery(
                    new MappingFilter.Builder().claimName(claimName).build(),
                    MappingSort.of(b -> b.claimName().desc()),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isSortedAccordingTo(Comparator.comparing(MappingEntity::claimName).reversed());
  }

  @TestTemplate
  public void shouldSortMappingsByClaimValueAsc(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String claimName = "claimName-" + MappingFixtures.nextStringId();
    createAndSaveRandomMappings(rdbmsService, b -> b.claimName(claimName));

    final var searchResult =
        rdbmsService
            .getMappingReader()
            .search(
                new MappingQuery(
                    new MappingFilter.Builder().claimName(claimName).build(),
                    MappingSort.of(b -> b.claimName().asc()),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isSortedAccordingTo(Comparator.comparing(MappingEntity::claimName));
  }

  @TestTemplate
  public void shouldSortMappingsByClaimValueDesc(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String claimName = "claimName-" + MappingFixtures.nextStringId();
    createAndSaveRandomMappings(rdbmsService, b -> b.claimName(claimName));

    final var searchResult =
        rdbmsService
            .getMappingReader()
            .search(
                new MappingQuery(
                    new MappingFilter.Builder().claimName(claimName).build(),
                    MappingSort.of(b -> b.claimName().desc()),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isSortedAccordingTo(Comparator.comparing(MappingEntity::claimName).reversed());
  }

  @TestTemplate
  public void shouldSortMappingsByNameValueDesc(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String name = "name-" + MappingFixtures.nextStringId();
    createAndSaveRandomMappings(rdbmsService, b -> b.name(name));

    final var searchResult =
        rdbmsService
            .getMappingReader()
            .search(
                new MappingQuery(
                    new MappingFilter.Builder().name(name).build(),
                    MappingSort.of(b -> b.name().desc()),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isSortedAccordingTo(Comparator.comparing(MappingEntity::name).reversed());
  }
}
