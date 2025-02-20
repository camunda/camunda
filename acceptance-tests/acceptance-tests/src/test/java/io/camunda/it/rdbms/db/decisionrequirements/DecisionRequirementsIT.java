/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.decisionrequirements;

import static io.camunda.it.rdbms.db.fixtures.DecisionRequirementsFixtures.createAndSaveDecisionRequirement;
import static io.camunda.it.rdbms.db.fixtures.DecisionRequirementsFixtures.createAndSaveRandomDecisionRequirements;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.DecisionRequirementsReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.DecisionRequirementsFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.result.DecisionRequirementsQueryResultConfig;
import io.camunda.search.sort.DecisionRequirementsSort;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class DecisionRequirementsIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final DecisionRequirementsReader decisionRequirementsReader =
        rdbmsService.getDecisionRequirementsReader();

    final var decisionRequirements = DecisionRequirementsFixtures.createRandomized(b -> b);
    createAndSaveDecisionRequirement(rdbmsWriter, decisionRequirements);

    final var instance =
        decisionRequirementsReader
            .findOne(decisionRequirements.decisionRequirementsKey())
            .orElse(null);

    assertThat(instance).isNotNull();
    assertThat(instance.decisionRequirementsKey())
        .isEqualTo(decisionRequirements.decisionRequirementsKey());
    assertThat(instance.version()).isEqualTo(decisionRequirements.version());
    assertThat(instance.name()).isEqualTo(decisionRequirements.name());
    assertThat(instance.decisionRequirementsId())
        .isEqualTo(decisionRequirements.decisionRequirementsId());
    assertThat(instance.resourceName()).isEqualTo(decisionRequirements.resourceName());
    assertThat(instance.xml()).isEqualTo(decisionRequirements.xml());
  }

  @TestTemplate
  public void shouldFindById(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final DecisionRequirementsReader decisionRequirementsReader =
        rdbmsService.getDecisionRequirementsReader();

    final var decisionRequirements =
        DecisionRequirementsFixtures.createRandomized(
            b -> b.decisionRequirementsId("test-process-unique"));
    createAndSaveDecisionRequirement(rdbmsWriter, decisionRequirements);

    final var searchResult =
        decisionRequirementsReader.search(
            new DecisionRequirementsQuery(
                new DecisionRequirementsFilter.Builder()
                    .decisionRequirementsIds("test-process-unique")
                    .build(),
                DecisionRequirementsSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10)),
                DecisionRequirementsQueryResultConfig.of(b -> b.includeXml(true))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    assertThat(instance.decisionRequirementsKey())
        .isEqualTo(decisionRequirements.decisionRequirementsKey());
    assertThat(instance.version()).isEqualTo(decisionRequirements.version());
    assertThat(instance.name()).isEqualTo(decisionRequirements.name());
    assertThat(instance.decisionRequirementsId())
        .isEqualTo(decisionRequirements.decisionRequirementsId());
    assertThat(instance.resourceName()).isEqualTo(decisionRequirements.resourceName());
    assertThat(instance.xml()).isEqualTo(decisionRequirements.xml());
  }

  @TestTemplate
  public void shouldFindAllPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final DecisionRequirementsReader decisionRequirementsReader =
        rdbmsService.getDecisionRequirementsReader();

    final String decisionRequirementsId = DecisionRequirementsFixtures.nextStringId();
    createAndSaveRandomDecisionRequirements(
        rdbmsWriter, b -> b.decisionRequirementsId(decisionRequirementsId));

    final var searchResult =
        decisionRequirementsReader.search(
            new DecisionRequirementsQuery(
                new DecisionRequirementsFilter.Builder()
                    .decisionRequirementsIds(decisionRequirementsId)
                    .build(),
                DecisionRequirementsSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5)),
                null));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);

    // by default XML is not filled
    assertThat(searchResult.items())
        .allSatisfy(
            item -> {
              assertThat(item.xml()).isNull();
            });
  }

  @TestTemplate
  public void shouldFindWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final DecisionRequirementsReader decisionRequirementsReader =
        rdbmsService.getDecisionRequirementsReader();

    final var decisionRequirements = DecisionRequirementsFixtures.createRandomized(b -> b);
    createAndSaveRandomDecisionRequirements(rdbmsWriter);
    createAndSaveDecisionRequirement(rdbmsWriter, decisionRequirements);

    final var searchResult =
        decisionRequirementsReader.search(
            new DecisionRequirementsQuery(
                new DecisionRequirementsFilter.Builder()
                    .decisionRequirementsKeys(decisionRequirements.decisionRequirementsKey())
                    .decisionRequirementsIds(decisionRequirements.decisionRequirementsId())
                    .names(decisionRequirements.name())
                    .versions(decisionRequirements.version())
                    .tenantIds(decisionRequirements.tenantId())
                    .decisionRequirementsIds(decisionRequirements.decisionRequirementsId())
                    .decisionRequirementsKeys(decisionRequirements.decisionRequirementsKey())
                    .build(),
                DecisionRequirementsSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5)),
                null));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().decisionRequirementsKey())
        .isEqualTo(decisionRequirements.decisionRequirementsKey());
  }

  @TestTemplate
  public void shouldFindWithSearchAfter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final DecisionRequirementsReader decisionRequirementsReader =
        rdbmsService.getDecisionRequirementsReader();

    createAndSaveRandomDecisionRequirements(rdbmsWriter, b -> b.tenantId("search-after-123456"));
    final var sort =
        DecisionRequirementsSort.of(s -> s.name().asc().version().asc().tenantId().desc());
    final var searchResult =
        decisionRequirementsReader.search(
            DecisionRequirementsQuery.of(
                b ->
                    b.filter(f -> f.tenantIds("search-after-123456"))
                        .sort(sort)
                        .page(p -> p.from(0).size(20))));

    final var instanceAfter = searchResult.items().get(9);
    final var nextPage =
        decisionRequirementsReader.search(
            DecisionRequirementsQuery.of(
                b ->
                    b.filter(f -> f.tenantIds("search-after-123456"))
                        .sort(sort)
                        .page(
                            p ->
                                p.size(5)
                                    .searchAfter(
                                        new Object[] {
                                          instanceAfter.name(),
                                          instanceAfter.version(),
                                          instanceAfter.tenantId(),
                                          instanceAfter.decisionRequirementsKey()
                                        }))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(10, 15));
  }
}
