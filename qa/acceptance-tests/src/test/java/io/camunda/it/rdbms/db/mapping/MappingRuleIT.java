/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.mapping;

import static io.camunda.it.rdbms.db.fixtures.MappingRuleFixtures.createAndSaveMapping;
import static io.camunda.it.rdbms.db.fixtures.MappingRuleFixtures.createAndSaveRandomMappings;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.MappingRuleDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.MappingRuleDbModel;
import io.camunda.it.rdbms.db.fixtures.MappingRuleFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.sort.MappingRuleSort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class MappingRuleIT {
  private static final long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSaveAndFindMappingByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);

    final MappingRuleDbModel randomizedMappingRule = MappingRuleFixtures.createRandomized();
    createAndSaveMapping(rdbmsWriter, randomizedMappingRule);

    final var mappingRule =
        rdbmsService
            .getMappingRuleReader()
            .findOne(randomizedMappingRule.mappingRuleId())
            .orElse(null);
    assertThat(mappingRule).isNotNull();
    assertThat(mappingRule).usingRecursiveComparison().isEqualTo(randomizedMappingRule);
  }

  @TestTemplate
  public void shouldDeleteMapping(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);

    // Create and save a mapping
    final MappingRuleDbModel randomizedMappingRule = MappingRuleFixtures.createRandomized();
    createAndSaveMapping(rdbmsWriter, randomizedMappingRule);

    // Verify the mapping is saved
    final var mappingRuleId = randomizedMappingRule.mappingRuleId();
    final var mappingRule = rdbmsService.getMappingRuleReader().findOne(mappingRuleId).orElse(null);
    assertThat(mappingRule).isNotNull();
    assertThat(mappingRule).usingRecursiveComparison().isEqualTo(randomizedMappingRule);

    // Delete the mapping
    final RdbmsWriter writer = rdbmsService.createWriter(1L);
    writer.getMappingRuleWriter().delete(mappingRuleId);
    writer.flush();

    // Verify the mapping is deleted
    final var deletedMappingResult = rdbmsService.getMappingRuleReader().findOne(mappingRuleId);
    assertThat(deletedMappingResult).isEmpty();
  }

  @TestTemplate
  public void shouldFindMappingByClaimName(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);

    // Create and save a mapping
    final MappingRuleDbModel randomizedMapping = MappingRuleFixtures.createRandomized();
    createAndSaveMapping(rdbmsWriter, randomizedMapping);

    // Search for the mapping by claimName
    final var searchResult =
        rdbmsService
            .getMappingRuleReader()
            .search(
                new MappingRuleQuery(
                    new MappingRuleFilter.Builder()
                        .claimName(randomizedMapping.claimName())
                        .build(),
                    MappingRuleSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    // Verify the search result
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    final var instance = searchResult.items().getFirst();
    assertThat(instance).isNotNull();
    assertThat(instance).usingRecursiveComparison().isEqualTo(randomizedMapping);
  }

  @TestTemplate
  public void shouldFindMappingByClaimValue(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);

    // Create and save a mapping rule
    final MappingRuleDbModel randomizedMapping = MappingRuleFixtures.createRandomized();
    createAndSaveMapping(rdbmsWriter, randomizedMapping);

    // Search for the mapping rule by claimValue
    final var searchResult =
        rdbmsService
            .getMappingRuleReader()
            .search(
                new MappingRuleQuery(
                    new MappingRuleFilter.Builder()
                        .claimValue(randomizedMapping.claimValue())
                        .build(),
                    MappingRuleSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    // Verify the search result
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    final var instance = searchResult.items().getFirst();
    assertThat(instance).isNotNull();
    assertThat(instance).usingRecursiveComparison().isEqualTo(randomizedMapping);
  }

  @TestTemplate
  public void shouldFindAllMappingsPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);

    final String claimName = "claimName-" + MappingRuleFixtures.nextStringId();
    createAndSaveRandomMappings(rdbmsWriter, b -> b.claimName(claimName));

    final var searchResult =
        rdbmsService
            .getMappingRuleReader()
            .search(
                new MappingRuleQuery(
                    new MappingRuleFilter.Builder().claimName(claimName).build(),
                    MappingRuleSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindMappingWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final MappingRuleDbReader mappingRuleReader = rdbmsService.getMappingRuleReader();

    final String claimName = "claimName-" + MappingRuleFixtures.nextStringId();
    createAndSaveRandomMappings(rdbmsWriter, b -> b.claimName(claimName));
    final MappingRuleDbModel randomizedMapping =
        MappingRuleFixtures.createRandomized(b -> b.claimName(claimName));
    createAndSaveMapping(rdbmsWriter, randomizedMapping);

    final var searchResult =
        mappingRuleReader.search(
            new MappingRuleQuery(
                new MappingRuleFilter.Builder()
                    .mappingRuleKey(randomizedMapping.mappingRuleKey())
                    .mappingRuleId(randomizedMapping.mappingRuleId())
                    .claimName(randomizedMapping.claimName())
                    .claimValue(randomizedMapping.claimValue())
                    .name(randomizedMapping.name())
                    .build(),
                MappingRuleSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().mappingRuleId())
        .isEqualTo(randomizedMapping.mappingRuleId());
  }
}
