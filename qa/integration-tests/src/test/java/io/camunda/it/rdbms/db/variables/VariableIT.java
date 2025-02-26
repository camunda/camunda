/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.variables;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.generateRandomString;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.createAndSaveVariable;
import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.prepareRandomVariablesAndReturnOne;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.VariableReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel.VariableDbModelBuilder;
import io.camunda.it.rdbms.db.fixtures.VariableFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.VariableSort;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class VariableIT {

  public static final int PARTITION_ID = 0;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindVariableByKey(final CamundaRdbmsTestApplication testApplication) {
    final VariableDbModel randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication);

    final var instance =
        testApplication
            .getRdbmsService()
            .getVariableReader()
            .findOne(randomizedVariable.variableKey());

    assertThat(instance).isNotNull();
    assertVariableDbModelEqualToEntity(randomizedVariable, instance);
    assertThat(instance.fullValue()).isNull();
    assertThat(instance.isPreview()).isFalse();
  }

  @TestTemplate
  public void shouldUpdateAndFindVariableByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final VariableDbModel randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication);

    final var newValue = "new value";
    final VariableDbModel updatedVariable =
        randomizedVariable.copy(b -> ((VariableDbModelBuilder) b).value(newValue));

    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(0L);
    rdbmsWriter.getVariableWriter().update(updatedVariable);
    rdbmsWriter.flush();

    final var instance = rdbmsService.getVariableReader().findOne(randomizedVariable.variableKey());

    assertThat(instance).isNotNull();
    assertVariableDbModelEqualToEntity(updatedVariable, instance);
  }

  @TestTemplate
  public void shouldSaveAndFindBigVariableByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String bigValue = generateRandomString(9000);
    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.value(bigValue));
    createAndSaveVariable(rdbmsService, randomizedVariable);

    final var instance = rdbmsService.getVariableReader().findOne(randomizedVariable.variableKey());

    assertThat(instance).isNotNull();
    assertThat(instance.isPreview()).isTrue();
    assertThat(instance.fullValue()).isEqualTo(bigValue);
    assertThat(instance.value()).hasSizeLessThan(instance.fullValue().length());
    assertThat(instance.isPreview()).isTrue();
  }

  @TestTemplate
  public void shouldFindVariableByProcessInstanceKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final VariableDbModel randomizedVariable = VariableFixtures.createRandomized();
    createAndSaveVariable(rdbmsService, randomizedVariable);

    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    new VariableFilter.Builder()
                        .processInstanceKeys(randomizedVariable.processInstanceKey())
                        .build(),
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    assertThat(instance).isNotNull();
    assertVariableDbModelEqualToEntity(randomizedVariable, instance);
  }

  @TestTemplate
  public void shouldFindAllVariablesPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String varName =
        VariableFixtures.createAndSaveRandomVariablesWithFixedName(rdbmsService).getLast().name();

    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    new VariableFilter.Builder().names(varName).build(),
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllVariablesPageValuesAreNull(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String varName =
        VariableFixtures.createAndSaveRandomVariablesWithFixedName(rdbmsService).getLast().name();

    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    new VariableFilter.Builder().names(varName).build(),
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(null).size(null))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(20);
  }

  @TestTemplate
  public void shouldFindVariableWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final VariableReader variableReader = rdbmsService.getVariableReader();

    final String varName =
        VariableFixtures.createAndSaveRandomVariablesWithFixedName(rdbmsService).getLast().name();
    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.name(varName));
    createAndSaveVariable(rdbmsService, randomizedVariable);

    final var searchResult =
        variableReader.search(
            new VariableQuery(
                new VariableFilter.Builder()
                    .variableKeys(randomizedVariable.variableKey())
                    .processInstanceKeys(randomizedVariable.processInstanceKey())
                    .names(varName)
                    .tenantIds(randomizedVariable.tenantId())
                    .build(),
                VariableSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().variableKey())
        .isEqualTo(randomizedVariable.variableKey());
  }

  @TestTemplate
  public void shouldFindVariableWithSearchAfter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final VariableReader variableReader = rdbmsService.getVariableReader();

    final String varName =
        VariableFixtures.createAndSaveRandomVariablesWithFixedName(rdbmsService).getLast().name();

    final var sort =
        VariableSort.of(s -> s.scopeKey().asc().value().asc().processInstanceKey().desc());
    final var searchResult =
        variableReader.search(
            VariableQuery.of(
                b -> b.filter(f -> f.names(varName)).sort(sort).page(p -> p.from(0).size(20))));

    final var instanceAfter = searchResult.items().get(9);
    final var nextPage =
        variableReader.search(
            VariableQuery.of(
                b ->
                    b.filter(f -> f.names(varName))
                        .sort(sort)
                        .page(
                            p ->
                                p.size(5)
                                    .searchAfter(
                                        new Object[] {
                                          instanceAfter.scopeKey(),
                                          instanceAfter.value(),
                                          instanceAfter.processInstanceKey(),
                                          instanceAfter.variableKey()
                                        }))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(10, 15));
  }

  @TestTemplate
  public void shouldCleanup(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final VariableReader reader = rdbmsService.getVariableReader();

    final var cleanupDate = NOW.minusDays(1);

    final var tenantId = nextStringId();
    final var item1 = createAndSaveVariable(rdbmsService, b -> b.tenantId(tenantId));
    final var item2 = createAndSaveVariable(rdbmsService, b -> b.tenantId(tenantId));
    final var item3 = createAndSaveVariable(rdbmsService, b -> b.tenantId(tenantId));

    // set cleanup dates
    rdbmsWriter.getVariableWriter().scheduleForHistoryCleanup(item1.processInstanceKey(), NOW);
    rdbmsWriter
        .getVariableWriter()
        .scheduleForHistoryCleanup(item2.processInstanceKey(), NOW.minusDays(2));
    rdbmsWriter.flush();

    // cleanup
    rdbmsWriter.getVariableWriter().cleanupHistory(PARTITION_ID, cleanupDate, 10);

    final var searchResult =
        reader.search(
            VariableQuery.of(
                b ->
                    b.filter(f -> f.tenantIds(tenantId))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(VariableEntity::variableKey))
        .containsExactlyInAnyOrder(item1.variableKey(), item3.variableKey());
  }

  private void assertVariableDbModelEqualToEntity(
      final VariableDbModel dbModel, final VariableEntity entity) {
    assertThat(entity)
        .usingRecursiveComparison()
        .ignoringFields("bpmnProcessId", "processDefinitionId")
        .isEqualTo(dbModel);
    assertThat(entity.processDefinitionId()).isEqualTo(dbModel.processDefinitionId());
  }
}
