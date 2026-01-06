/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.mappingrules;

import static io.camunda.it.rdbms.db.fixtures.MappingRuleFixtures.createAndSaveRandomMappingRules;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.MappingRuleFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.sort.MappingRuleSort;
import java.util.Comparator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class MappingRuleSortIT {

  public static final long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSortMappingsByClaimNameAsc(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);

    final String claimName = "claimName-" + MappingRuleFixtures.nextStringId();
    createAndSaveRandomMappingRules(rdbmsWriter, b -> b.claimName(claimName));

    final var searchResult =
        rdbmsService
            .getMappingRuleReader()
            .search(
                new MappingRuleQuery(
                    new MappingRuleFilter.Builder().claimName(claimName).build(),
                    MappingRuleSort.of(b -> b.claimName().asc()),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isSortedAccordingTo(Comparator.comparing(MappingRuleEntity::claimName));
  }

  @TestTemplate
  public void shouldSortMappingsByClaimNameDesc(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);

    final String claimName = "claimName-" + MappingRuleFixtures.nextStringId();
    createAndSaveRandomMappingRules(rdbmsWriter, b -> b.claimName(claimName));

    final var searchResult =
        rdbmsService
            .getMappingRuleReader()
            .search(
                new MappingRuleQuery(
                    new MappingRuleFilter.Builder().claimName(claimName).build(),
                    MappingRuleSort.of(b -> b.claimName().desc()),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isSortedAccordingTo(Comparator.comparing(MappingRuleEntity::claimName).reversed());
  }

  @TestTemplate
  public void shouldSortMappingsByClaimValueAsc(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);

    final String claimName = "claimName-" + MappingRuleFixtures.nextStringId();
    createAndSaveRandomMappingRules(rdbmsWriter, b -> b.claimName(claimName));

    final var searchResult =
        rdbmsService
            .getMappingRuleReader()
            .search(
                new MappingRuleQuery(
                    new MappingRuleFilter.Builder().claimName(claimName).build(),
                    MappingRuleSort.of(b -> b.claimName().asc()),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isSortedAccordingTo(Comparator.comparing(MappingRuleEntity::claimName));
  }

  @TestTemplate
  public void shouldSortMappingsByClaimValueDesc(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);

    final String claimName = "claimName-" + MappingRuleFixtures.nextStringId();
    createAndSaveRandomMappingRules(rdbmsWriter, b -> b.claimName(claimName));

    final var searchResult =
        rdbmsService
            .getMappingRuleReader()
            .search(
                new MappingRuleQuery(
                    new MappingRuleFilter.Builder().claimName(claimName).build(),
                    MappingRuleSort.of(b -> b.claimName().desc()),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isSortedAccordingTo(Comparator.comparing(MappingRuleEntity::claimName).reversed());
  }

  @TestTemplate
  public void shouldSortMappingsByNameValueDesc(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);

    final String name = "name-" + MappingRuleFixtures.nextStringId();
    createAndSaveRandomMappingRules(rdbmsWriter, b -> b.name(name));

    final var searchResult =
        rdbmsService
            .getMappingRuleReader()
            .search(
                new MappingRuleQuery(
                    new MappingRuleFilter.Builder().name(name).build(),
                    MappingRuleSort.of(b -> b.name().desc()),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isSortedAccordingTo(Comparator.comparing(MappingRuleEntity::name).reversed());
  }
}
