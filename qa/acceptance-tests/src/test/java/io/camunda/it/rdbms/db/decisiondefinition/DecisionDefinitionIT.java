/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.decisiondefinition;

import static io.camunda.it.rdbms.db.fixtures.DecisionDefinitionFixtures.createAndSaveDecisionDefinition;
import static io.camunda.it.rdbms.db.fixtures.DecisionDefinitionFixtures.createAndSaveRandomDecisionDefinitions;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.DecisionDefinitionDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.fixtures.CommonFixtures;
import io.camunda.it.rdbms.db.fixtures.DecisionDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.sort.DecisionDefinitionSort;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class DecisionDefinitionIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionDefinitionDbReader decisionDefinitionReader =
        rdbmsService.getDecisionDefinitionReader();

    final var decisionDefinition = DecisionDefinitionFixtures.createRandomized(b -> b);
    createAndSaveDecisionDefinition(rdbmsWriters, decisionDefinition);

    final var instance =
        decisionDefinitionReader.findOne(decisionDefinition.decisionDefinitionKey()).orElse(null);
    assertThat(instance).isNotNull();
    assertThat(instance.decisionDefinitionKey())
        .isEqualTo(decisionDefinition.decisionDefinitionKey());
    assertThat(instance.version()).isEqualTo(decisionDefinition.version());
    assertThat(instance.name()).isEqualTo(decisionDefinition.name());
    assertThat(instance.decisionDefinitionId())
        .isEqualTo(decisionDefinition.decisionDefinitionId());
    assertThat(instance.decisionRequirementsId())
        .isEqualTo(decisionDefinition.decisionRequirementsId());
    assertThat(instance.decisionRequirementsKey())
        .isEqualTo(decisionDefinition.decisionRequirementsKey());
    assertThat(instance.decisionRequirementsName())
        .isEqualTo(decisionDefinition.decisionRequirementsName());
    assertThat(instance.decisionRequirementsVersion())
        .isEqualTo(decisionDefinition.decisionRequirementsVersion());
  }

  @TestTemplate
  public void shouldFindByBpmnProcessId(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionDefinitionDbReader decisionDefinitionReader =
        rdbmsService.getDecisionDefinitionReader();

    final var decisionDefinition =
        DecisionDefinitionFixtures.createRandomized(
            b -> b.decisionDefinitionId("test-process-unique"));
    createAndSaveDecisionDefinition(rdbmsWriters, decisionDefinition);

    final var searchResult =
        decisionDefinitionReader.search(
            new DecisionDefinitionQuery(
                new DecisionDefinitionFilter.Builder()
                    .decisionDefinitionIds("test-process-unique")
                    .build(),
                DecisionDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    assertThat(instance.decisionDefinitionKey())
        .isEqualTo(decisionDefinition.decisionDefinitionKey());
    assertThat(instance.version()).isEqualTo(decisionDefinition.version());
    assertThat(instance.name()).isEqualTo(decisionDefinition.name());
    assertThat(instance.decisionDefinitionId())
        .isEqualTo(decisionDefinition.decisionDefinitionId());
    assertThat(instance.decisionRequirementsId())
        .isEqualTo(decisionDefinition.decisionRequirementsId());
    assertThat(instance.decisionRequirementsKey())
        .isEqualTo(decisionDefinition.decisionRequirementsKey());
    assertThat(instance.decisionRequirementsName())
        .isEqualTo(decisionDefinition.decisionRequirementsName());
    assertThat(instance.decisionRequirementsVersion())
        .isEqualTo(decisionDefinition.decisionRequirementsVersion());
  }

  @TestTemplate
  public void shouldFindByAuthorizedResourceId(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionDefinitionDbReader decisionDefinitionReader =
        rdbmsService.getDecisionDefinitionReader();

    final var decisionDefinition = DecisionDefinitionFixtures.createRandomized(b -> b);
    createAndSaveDecisionDefinition(rdbmsWriters, decisionDefinition);
    createAndSaveRandomDecisionDefinitions(rdbmsWriters);

    final var searchResult =
        decisionDefinitionReader.search(
            DecisionDefinitionQuery.of(b -> b),
            CommonFixtures.resourceAccessChecksFromResourceIds(
                AuthorizationResourceType.DECISION_DEFINITION,
                decisionDefinition.decisionDefinitionId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();
    assertThat(instance.decisionDefinitionKey())
        .isEqualTo(decisionDefinition.decisionDefinitionKey());
  }

  @TestTemplate
  public void shouldFindByAuthorizedTenantId(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionDefinitionDbReader decisionDefinitionReader =
        rdbmsService.getDecisionDefinitionReader();

    final var decisionDefinition = DecisionDefinitionFixtures.createRandomized(b -> b);
    createAndSaveDecisionDefinition(rdbmsWriters, decisionDefinition);
    createAndSaveRandomDecisionDefinitions(rdbmsWriters);

    final var searchResult =
        decisionDefinitionReader.search(
            DecisionDefinitionQuery.of(b -> b),
            CommonFixtures.resourceAccessChecksFromTenantIds(decisionDefinition.tenantId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();
    assertThat(instance.decisionDefinitionKey())
        .isEqualTo(decisionDefinition.decisionDefinitionKey());
  }

  @TestTemplate
  public void shouldFindAllPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionDefinitionDbReader decisionDefinitionReader =
        rdbmsService.getDecisionDefinitionReader();

    final String decisionDefinitionId = DecisionDefinitionFixtures.nextStringId();
    createAndSaveRandomDecisionDefinitions(
        rdbmsWriters, b -> b.decisionDefinitionId(decisionDefinitionId));

    final var searchResult =
        decisionDefinitionReader.search(
            new DecisionDefinitionQuery(
                new DecisionDefinitionFilter.Builder()
                    .decisionDefinitionIds(decisionDefinitionId)
                    .build(),
                DecisionDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllPagedWithHasMoreHits(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionDefinitionDbReader decisionDefinitionReader =
        rdbmsService.getDecisionDefinitionReader();

    final String decisionDefinitionId = DecisionDefinitionFixtures.nextStringId();
    createAndSaveRandomDecisionDefinitions(
        rdbmsWriters, 120, b -> b.decisionDefinitionId(decisionDefinitionId));

    final var searchResult =
        decisionDefinitionReader.search(
            new DecisionDefinitionQuery(
                new DecisionDefinitionFilter.Builder()
                    .decisionDefinitionIds(decisionDefinitionId)
                    .build(),
                DecisionDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(100);
    assertThat(searchResult.hasMoreTotalItems()).isEqualTo(true);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllPageValuesAreNull(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionDefinitionDbReader decisionDefinitionReader =
        rdbmsService.getDecisionDefinitionReader();

    createAndSaveRandomDecisionDefinitions(rdbmsWriters);

    final var searchResult =
        decisionDefinitionReader.search(
            new DecisionDefinitionQuery(
                new DecisionDefinitionFilter.Builder().build(),
                DecisionDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(null).size(null))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isGreaterThanOrEqualTo(20);
    assertThat(searchResult.items()).hasSizeGreaterThanOrEqualTo(20);
  }

  @TestTemplate
  public void shouldFindWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionDefinitionDbReader decisionDefinitionReader =
        rdbmsService.getDecisionDefinitionReader();

    final var decisionDefinition = DecisionDefinitionFixtures.createRandomized(b -> b);
    createAndSaveRandomDecisionDefinitions(rdbmsWriters);
    createAndSaveDecisionDefinition(rdbmsWriters, decisionDefinition);

    final var searchResult =
        decisionDefinitionReader.search(
            new DecisionDefinitionQuery(
                new DecisionDefinitionFilter.Builder()
                    .decisionDefinitionKeys(decisionDefinition.decisionDefinitionKey())
                    .decisionDefinitionIds(decisionDefinition.decisionDefinitionId())
                    .names(decisionDefinition.name())
                    .versions(decisionDefinition.version())
                    .tenantIds(decisionDefinition.tenantId())
                    .decisionRequirementsIds(decisionDefinition.decisionRequirementsId())
                    .decisionRequirementsKeys(decisionDefinition.decisionRequirementsKey())
                    .decisionRequirementsNames(decisionDefinition.decisionRequirementsName())
                    .decisionRequirementsVersions(decisionDefinition.decisionRequirementsVersion())
                    .build(),
                DecisionDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().decisionDefinitionKey())
        .isEqualTo(decisionDefinition.decisionDefinitionKey());
  }

  @TestTemplate
  public void shouldFindWithSearchAfter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionDefinitionDbReader decisionDefinitionReader =
        rdbmsService.getDecisionDefinitionReader();

    createAndSaveRandomDecisionDefinitions(rdbmsWriters, b -> b.tenantId("search-after-123456"));
    final var sort =
        DecisionDefinitionSort.of(s -> s.name().asc().version().asc().tenantId().desc());
    final var searchResult =
        decisionDefinitionReader.search(
            DecisionDefinitionQuery.of(
                b -> b.filter(f -> f.tenantIds("search-after-123456")).sort(sort)));

    final var firstPage =
        decisionDefinitionReader.search(
            DecisionDefinitionQuery.of(
                b ->
                    b.filter(f -> f.tenantIds("search-after-123456"))
                        .sort(sort)
                        .page(p -> p.size(15))));

    final var nextPage =
        decisionDefinitionReader.search(
            DecisionDefinitionQuery.of(
                b ->
                    b.filter(f -> f.tenantIds("search-after-123456"))
                        .sort(sort)
                        .page(p -> p.size(5).after(firstPage.endCursor()))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(15, 20));
  }
}
