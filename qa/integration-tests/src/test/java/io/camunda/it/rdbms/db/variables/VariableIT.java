/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.variables;

import static io.camunda.db.rdbms.write.domain.VariableDbModel.DEFAULT_VARIABLE_SIZE_THRESHOLD;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.generateRandomString;
import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.createAndSaveVariable;
import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.prepareRandomVariablesAndReturnOne;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.VariableReader;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.it.rdbms.db.fixtures.VariableFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.VariableSort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class VariableIT {

  @TestTemplate
  public void shouldSaveAndFindVariableByKey(final CamundaRdbmsTestApplication testApplication) {
    final VariableDbModel randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication);

    final var instance =
        testApplication.getRdbmsService().getVariableReader().findOne(randomizedVariable.key());

    assertThat(instance).isNotNull();
    assertThat(instance).usingRecursiveComparison().isEqualTo(randomizedVariable);
    assertThat(instance.fullValue()).isNull();
    assertThat(instance.isPreview()).isFalse();
  }

  @TestTemplate
  public void shouldSaveAndFindBigVariableByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String bigValue = generateRandomString(DEFAULT_VARIABLE_SIZE_THRESHOLD + 1);
    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.value(bigValue));
    createAndSaveVariable(rdbmsService, randomizedVariable);

    final var instance = rdbmsService.getVariableReader().findOne(randomizedVariable.key());

    assertThat(instance).isNotNull();
    assertThat(instance).usingRecursiveComparison().isEqualTo(randomizedVariable);
    assertThat(instance.fullValue()).isEqualTo(bigValue);
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
    assertThat(searchResult.hits()).hasSize(1);

    final var instance = searchResult.hits().getFirst();

    assertThat(instance).isNotNull();
    assertThat(instance).usingRecursiveComparison().isEqualTo(randomizedVariable);
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
                    new VariableFilter.Builder().variable(varName).build(),
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.hits()).hasSize(5);
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
                    new VariableFilter.Builder().variable(varName).build(),
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(null).size(null))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.hits()).hasSize(20);
  }

  @TestTemplate
  public void shouldFindVariableWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final VariableReader processInstanceReader = rdbmsService.getVariableReader();

    final String varName =
        VariableFixtures.createAndSaveRandomVariablesWithFixedName(rdbmsService).getLast().name();
    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.name(varName));
    createAndSaveVariable(rdbmsService, randomizedVariable);

    final var searchResult =
        processInstanceReader.search(
            new VariableQuery(
                new VariableFilter.Builder()
                    .variableKeys(randomizedVariable.key())
                    .processInstanceKeys(randomizedVariable.processInstanceKey())
                    .variable(varName)
                    .tenantIds(randomizedVariable.tenantId())
                    .build(),
                VariableSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(randomizedVariable.key());
  }
}
