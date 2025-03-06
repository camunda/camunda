/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.mapping;

import static io.camunda.it.rdbms.db.fixtures.MappingFixtures.createAndSaveMapping;
import static io.camunda.it.rdbms.db.fixtures.MappingFixtures.createAndSaveRandomMappings;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.MappingReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.MappingDbModel;
import io.camunda.it.rdbms.db.fixtures.MappingFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.filter.MappingFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.sort.MappingSort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class MappingIT {

  @TestTemplate
  public void shouldSaveAndFindMappingByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final MappingDbModel randomizedMapping = MappingFixtures.createRandomized();
    createAndSaveMapping(rdbmsService, randomizedMapping);

    final var mapping =
        rdbmsService.getMappingReader().findOne(randomizedMapping.mappingKey()).orElse(null);
    assertThat(mapping).isNotNull();
    assertThat(mapping).usingRecursiveComparison().isEqualTo(randomizedMapping);
  }

  @TestTemplate
  public void shouldDeleteMapping(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // Create and save a mapping
    final MappingDbModel randomizedMapping = MappingFixtures.createRandomized();
    createAndSaveMapping(rdbmsService, randomizedMapping);

    // Verify the mapping is saved
    final Long mappingKey = randomizedMapping.mappingKey();
    final var mapping = rdbmsService.getMappingReader().findOne(mappingKey).orElse(null);
    assertThat(mapping).isNotNull();
    assertThat(mapping).usingRecursiveComparison().isEqualTo(randomizedMapping);

    // Delete the mapping
    final RdbmsWriter writer = rdbmsService.createWriter(1L);
    writer.getMappingWriter().delete(mappingKey);
    writer.flush();

    // Verify the mapping is deleted
    final var deletedMappingResult = rdbmsService.getMappingReader().findOne(mappingKey);
    assertThat(deletedMappingResult).isEmpty();
  }

  @TestTemplate
  public void shouldFindMappingByClaimName(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // Create and save a mapping
    final MappingDbModel randomizedMapping = MappingFixtures.createRandomized();
    createAndSaveMapping(rdbmsService, randomizedMapping);

    // Search for the mapping by claimName
    final var searchResult =
        rdbmsService
            .getMappingReader()
            .search(
                new MappingQuery(
                    new MappingFilter.Builder().claimName(randomizedMapping.claimName()).build(),
                    MappingSort.of(b -> b),
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

    // Create and save a mapping
    final MappingDbModel randomizedMapping = MappingFixtures.createRandomized();
    createAndSaveMapping(rdbmsService, randomizedMapping);

    // Search for the mapping by claimValue
    final var searchResult =
        rdbmsService
            .getMappingReader()
            .search(
                new MappingQuery(
                    new MappingFilter.Builder().claimValue(randomizedMapping.claimValue()).build(),
                    MappingSort.of(b -> b),
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

    final String claimName = "claimName-" + MappingFixtures.nextStringId();
    createAndSaveRandomMappings(rdbmsService, b -> b.claimName(claimName));

    final var searchResult =
        rdbmsService
            .getMappingReader()
            .search(
                new MappingQuery(
                    new MappingFilter.Builder().claimName(claimName).build(),
                    MappingSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindMappingWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final MappingReader mappingReader = rdbmsService.getMappingReader();

    final String claimName = "claimName-" + MappingFixtures.nextStringId();
    createAndSaveRandomMappings(rdbmsService, b -> b.claimName(claimName));
    final MappingDbModel randomizedMapping =
        MappingFixtures.createRandomized(b -> b.claimName(claimName));
    createAndSaveMapping(rdbmsService, randomizedMapping);

    final var searchResult =
        mappingReader.search(
            new MappingQuery(
                new MappingFilter.Builder()
                    .mappingId(randomizedMapping.id())
                    .claimName(randomizedMapping.claimName())
                    .claimValue(randomizedMapping.claimValue())
                    .name(randomizedMapping.name())
                    .build(),
                MappingSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().id())
        .isEqualTo(randomizedMapping.id());
  }
}
