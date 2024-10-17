/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.createAndSaveRandomVariables;
import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.createAndSaveVariable;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.domain.VariableDbQuery;
import io.camunda.db.rdbms.read.service.VariableReader;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.it.rdbms.db.fixtures.VariableFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.search.filter.VariableValueFilter.Builder;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.VariableSort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class VariableValueFilterIT {

  @TestTemplate
  public void shouldFindVariableWithNameFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final VariableReader processInstanceReader = rdbmsService.getVariableReader();

    final var randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication);

    final VariableValueFilter variableValueFilter = new Builder().name(randomizedVariable.name()).build();

    final var searchResult =
        processInstanceReader.search(
            new VariableDbQuery(
                new VariableFilter.Builder()
                    .scopeKeys(randomizedVariable.scopeKey())
                    .variable(variableValueFilter)
                    .build(),
                VariableSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(randomizedVariable.key());
    assertThat(searchResult.hits().getFirst().name()).isEqualTo(randomizedVariable.name());
  }

  @TestTemplate
  public void shouldFindVariableWithMultipleNamesFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final VariableReader processInstanceReader = rdbmsService.getVariableReader();

    final var randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication);

    final VariableValueFilter variableValueFilter = new Builder().name(randomizedVariable.name()).build();
    final VariableValueFilter otherVariableValueFilter = new Builder().name("not there").build();

    final var searchResult =
        processInstanceReader.search(
            new VariableDbQuery(
                new VariableFilter.Builder()
                    .scopeKeys(randomizedVariable.scopeKey())
                    .variable(variableValueFilter, otherVariableValueFilter)
                    .build(),
                VariableSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(randomizedVariable.key());
    assertThat(searchResult.hits().getFirst().name()).isEqualTo(randomizedVariable.name());
  }

  @TestTemplate
  public void shouldFindVariableWithMultipleNamesAndValuesFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final VariableReader processInstanceReader = rdbmsService.getVariableReader();

    final var randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication);

    final VariableValueFilter variableValueFilter = new Builder().name(randomizedVariable.name()).eq(randomizedVariable.value()).build();
    final VariableValueFilter otherVariableValueFilter = new Builder().name("not there").eq("no").build();

    final var searchResult =
        processInstanceReader.search(
            new VariableDbQuery(
                new VariableFilter.Builder()
                    .scopeKeys(randomizedVariable.scopeKey())
                    .variable(variableValueFilter, otherVariableValueFilter)
                    .build(),
                VariableSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(randomizedVariable.key());
    assertThat(searchResult.hits().getFirst().name()).isEqualTo(randomizedVariable.name());
  }

  private VariableDbModel prepareRandomVariablesAndReturnOne(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final Long scopeKey = VariableFixtures.nextKey();
    createAndSaveRandomVariables(rdbmsService, scopeKey);
    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.scopeKey(scopeKey));
    createAndSaveVariable(rdbmsService, randomizedVariable);

    return randomizedVariable;
  }
}
