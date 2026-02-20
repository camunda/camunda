/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.mappingrules;

import static io.camunda.it.rdbms.db.fixtures.MappingRuleFixtures.createAndSaveMappingRule;
import static io.camunda.it.rdbms.db.fixtures.MappingRuleFixtures.createAndSaveRandomMappingRules;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.MappingRuleDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.MappingRuleDbModel;
import io.camunda.db.rdbms.write.domain.MappingRuleDbModel.MappingRuleDbModelBuilder;
import io.camunda.it.rdbms.db.fixtures.CommonFixtures;
import io.camunda.it.rdbms.db.fixtures.MappingRuleFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.search.filter.MappingRuleFilter.Claim;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.sort.MappingRuleSort;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class MappingRuleIT {
  private static final long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSaveAndFindMappingRuleByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);

    final MappingRuleDbModel randomizedMappingRule = MappingRuleFixtures.createRandomized();
    createAndSaveMappingRule(rdbmsWriters, randomizedMappingRule);

    final var mappingRule =
        rdbmsService
            .getMappingRuleReader()
            .findOne(randomizedMappingRule.mappingRuleId())
            .orElse(null);
    assertThat(mappingRule).isNotNull();
    assertThat(mappingRule).usingRecursiveComparison().isEqualTo(randomizedMappingRule);
  }

  @TestTemplate
  public void shouldDeleteMappingRule(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);

    // Create and save a mapping rule
    final MappingRuleDbModel randomizedMappingRule = MappingRuleFixtures.createRandomized();
    createAndSaveMappingRule(rdbmsWriters, randomizedMappingRule);

    // Verify the mapping rule is saved
    final var mappingRuleId = randomizedMappingRule.mappingRuleId();
    final var mappingRule = rdbmsService.getMappingRuleReader().findOne(mappingRuleId).orElse(null);
    assertThat(mappingRule).isNotNull();
    assertThat(mappingRule).usingRecursiveComparison().isEqualTo(randomizedMappingRule);

    // Delete the mapping rule
    final RdbmsWriters writer = rdbmsService.createWriter(1L);
    writer.getMappingRuleWriter().delete(mappingRuleId);
    writer.flush();

    // Verify the mapping rule is deleted
    final var deletedMappingRuleResult = rdbmsService.getMappingRuleReader().findOne(mappingRuleId);
    assertThat(deletedMappingRuleResult).isEmpty();
  }

  @TestTemplate
  public void shouldFindMappingRuleByClaimName(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);

    // Create and save a mapping rule
    final MappingRuleDbModel randomizedMappingRule = MappingRuleFixtures.createRandomized();
    createAndSaveMappingRule(rdbmsWriters, randomizedMappingRule);

    // Search for the mapping rule by claimName
    final var searchResult =
        rdbmsService
            .getMappingRuleReader()
            .search(
                new MappingRuleQuery(
                    new MappingRuleFilter.Builder()
                        .claimName(randomizedMappingRule.claimName())
                        .build(),
                    MappingRuleSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    // Verify the search result
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    final var instance = searchResult.items().getFirst();
    assertThat(instance).isNotNull();
    assertThat(instance).usingRecursiveComparison().isEqualTo(randomizedMappingRule);
  }

  @TestTemplate
  public void shouldFindMappingRuleByClaimValue(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);

    // Create and save a mapping rule
    final MappingRuleDbModel randomizedMappingRule = MappingRuleFixtures.createRandomized();
    createAndSaveMappingRule(rdbmsWriters, randomizedMappingRule);

    // Search for the mapping rule by claimValue
    final var searchResult =
        rdbmsService
            .getMappingRuleReader()
            .search(
                new MappingRuleQuery(
                    new MappingRuleFilter.Builder()
                        .claimValue(randomizedMappingRule.claimValue())
                        .build(),
                    MappingRuleSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    // Verify the search result
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    final var instance = searchResult.items().getFirst();
    assertThat(instance).isNotNull();
    assertThat(instance).usingRecursiveComparison().isEqualTo(randomizedMappingRule);
  }

  @TestTemplate
  public void shouldFindMappingRuleByClaims(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);

    // Create and save a mapping rule
    final MappingRuleDbModel mappingRule1 = MappingRuleFixtures.createRandomized();
    createAndSaveMappingRule(rdbmsWriters, mappingRule1);
    final MappingRuleDbModel mappingRule2 = MappingRuleFixtures.createRandomized();
    createAndSaveMappingRule(rdbmsWriters, mappingRule2);

    // Search for the mapping rule by claimValue
    final var searchResult =
        rdbmsService
            .getMappingRuleReader()
            .search(
                new MappingRuleQuery(
                    new MappingRuleFilter.Builder()
                        .claims(
                            List.of(
                                new Claim(mappingRule1.claimName(), mappingRule1.claimValue()),
                                new Claim(mappingRule2.claimName(), mappingRule2.claimValue())))
                        .build(),
                    MappingRuleSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    // Verify the search result
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(MappingRuleEntity::mappingRuleId))
        .containsExactly(mappingRule1.mappingRuleId(), mappingRule2.mappingRuleId());
  }

  @TestTemplate
  public void shouldFindMappingRuleByAuthorizationResourceId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);

    // Create and save a mapping rule
    final MappingRuleDbModel randomizedMappingRule = MappingRuleFixtures.createRandomized();
    createAndSaveMappingRule(rdbmsWriters, randomizedMappingRule);
    createAndSaveRandomMappingRules(rdbmsWriters, b -> b);

    // Search for the mapping rule by claimValue
    final var searchResult =
        rdbmsService
            .getMappingRuleReader()
            .search(
                MappingRuleQuery.of(b -> b),
                CommonFixtures.resourceAccessChecksFromResourceIds(
                    AuthorizationResourceType.MAPPING_RULE, randomizedMappingRule.mappingRuleId()));

    // Verify the search result
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    final var instance = searchResult.items().getFirst();
    assertThat(instance).isNotNull();
    assertThat(instance).usingRecursiveComparison().isEqualTo(randomizedMappingRule);
  }

  @TestTemplate
  public void shouldFindAllMappingRulesPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);

    final String claimName = "claimName-" + MappingRuleFixtures.nextStringId();
    createAndSaveRandomMappingRules(rdbmsWriters, b -> b.claimName(claimName));

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
  public void shouldFindAllMappingRulesPagedWithHasMoreHits(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);

    final String claimName = "claimName-" + MappingRuleFixtures.nextStringId();
    createAndSaveRandomMappingRules(rdbmsWriters, 120, b -> b.claimName(claimName));

    final var searchResult =
        rdbmsService
            .getMappingRuleReader()
            .search(
                new MappingRuleQuery(
                    new MappingRuleFilter.Builder().claimName(claimName).build(),
                    MappingRuleSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(100);
    assertThat(searchResult.hasMoreTotalItems()).isEqualTo(true);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindMappingRuleWithFullFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final MappingRuleDbReader mappingRuleReader = rdbmsService.getMappingRuleReader();

    final String claimName = "claimName-" + MappingRuleFixtures.nextStringId();
    createAndSaveRandomMappingRules(rdbmsWriters, b -> b.claimName(claimName));
    final MappingRuleDbModel randomizedMappingRule =
        MappingRuleFixtures.createRandomized(b -> b.claimName(claimName));
    createAndSaveMappingRule(rdbmsWriters, randomizedMappingRule);

    final var searchResult =
        mappingRuleReader.search(
            new MappingRuleQuery(
                new MappingRuleFilter.Builder()
                    .mappingRuleKey(randomizedMappingRule.mappingRuleKey())
                    .mappingRuleId(randomizedMappingRule.mappingRuleId())
                    .claimName(randomizedMappingRule.claimName())
                    .claimValue(randomizedMappingRule.claimValue())
                    .name(randomizedMappingRule.name())
                    .build(),
                MappingRuleSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().mappingRuleId())
        .isEqualTo(randomizedMappingRule.mappingRuleId());
  }

  @TestTemplate
  public void shouldUpdateMappingRule(final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final MappingRuleDbModel randomizedMappingRule = MappingRuleFixtures.createRandomized();
    final var mappingRuleId = randomizedMappingRule.mappingRuleId();
    createAndSaveMappingRule(rdbmsWriters, randomizedMappingRule);

    // when
    final RdbmsWriters writer = rdbmsService.createWriter(1L);
    final var updatedMappingRule =
        new MappingRuleDbModelBuilder()
            .mappingRuleId(randomizedMappingRule.mappingRuleId())
            .name("Updated Name")
            .claimName("Updated Claim Name")
            .claimValue("Updated Claim Value")
            .build();
    writer.getMappingRuleWriter().update(updatedMappingRule);
    writer.flush();

    // then
    final var mappingRule = rdbmsService.getMappingRuleReader().findOne(mappingRuleId);
    assertThat(mappingRule)
        .isPresent()
        .get()
        .returns(randomizedMappingRule.mappingRuleKey(), m -> m.mappingRuleKey())
        .returns(randomizedMappingRule.mappingRuleId(), m -> m.mappingRuleId())
        .returns("Updated Name", m -> m.name())
        .returns("Updated Claim Name", m -> m.claimName())
        .returns("Updated Claim Value", m -> m.claimValue());
  }
}
