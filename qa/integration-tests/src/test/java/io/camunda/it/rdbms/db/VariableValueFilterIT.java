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
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.it.rdbms.db.fixtures.VariableFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.search.filter.VariableValueFilter.Builder;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.VariableSort;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class VariableValueFilterIT {

  @TestTemplate
  public void shouldFindVariableWithNameFilter(final CamundaRdbmsTestApplication testApplication) {
    // GIVEN 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication);

    // AND a name filter
    final VariableValueFilter variableValueFilter =
        new Builder().name(randomizedVariable.name()).build();

    // WHEN we search for it, we should find one
    searchAndAssertVariableValueFilter(rdbmsService, randomizedVariable, variableValueFilter);
  }

  @TestTemplate
  public void shouldFindVariableWithNameAndEqValue(
      final CamundaRdbmsTestApplication testApplication) {
    // GIVEN 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication);

    // AND a name and eq value filter
    final VariableValueFilter variableValueFilter =
        new Builder().name(randomizedVariable.name()).eq(randomizedVariable.value()).build();

    // WHEN we search for it, we should find one
    searchAndAssertVariableValueFilter(rdbmsService, randomizedVariable, variableValueFilter);
  }

  @TestTemplate
  public void shouldFindVariableWithNameAndNeqValue(
      final CamundaRdbmsTestApplication testApplication) {
    // GIVEN 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication);

    // AND a name and eq value filter
    final VariableValueFilter variableValueFilter =
        new Builder().name(randomizedVariable.name()).neq("DEFINITELY NOT").build();

    // WHEN we search for it, we should find one
    searchAndAssertVariableValueFilter(rdbmsService, randomizedVariable, variableValueFilter);
  }

  @TestTemplate
  public void shouldFindVariableWithNameAndGtValue(
      final CamundaRdbmsTestApplication testApplication) {
    // GIVEN 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication, "6");

    // AND a name and eq value filter
    final VariableValueFilter variableValueFilter =
        new Builder().name(randomizedVariable.name()).gt(5).build();

    // WHEN we search for it, we should find one
    searchAndAssertVariableValueFilter(rdbmsService, randomizedVariable, variableValueFilter);
  }

  @TestTemplate
  public void shouldFindVariableWithNameAndGteValue(
      final CamundaRdbmsTestApplication testApplication) {
    // GIVEN 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication, "6");

    // AND a name and eq value filter
    final VariableValueFilter variableValueFilter =
        new Builder().name(randomizedVariable.name()).gte(6).build();

    // WHEN we search for it, we should find one
    searchAndAssertVariableValueFilter(rdbmsService, randomizedVariable, variableValueFilter);
  }

  @TestTemplate
  public void shouldFindVariableWithNameAndLtValue(
      final CamundaRdbmsTestApplication testApplication) {
    // GIVEN 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication, "6");

    // AND a name and eq value filter
    final VariableValueFilter variableValueFilter =
        new Builder().name(randomizedVariable.name()).lt(7).build();

    // WHEN we search for it, we should find one
    searchAndAssertVariableValueFilter(rdbmsService, randomizedVariable, variableValueFilter);
  }

  @TestTemplate
  public void shouldFindVariableWithNameAndLteValue(
      final CamundaRdbmsTestApplication testApplication) {
    // GIVEN 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication, "6");

    // AND a name and eq value filter
    final VariableValueFilter variableValueFilter =
        new Builder().name(randomizedVariable.name()).lte(6).build();

    // WHEN we search for it, we should find one
    searchAndAssertVariableValueFilter(rdbmsService, randomizedVariable, variableValueFilter);
  }

  @TestTemplate
  public void shouldFindVariableWithMultipleNamesFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // GIVEN 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication);

    // AND twp name filters
    final VariableValueFilter variableValueFilter =
        new Builder().name(randomizedVariable.name()).build();
    final VariableValueFilter otherVariableValueFilter = new Builder().name("not there").build();

    // WHEN we search for it, we should find one
    searchAndAssertVariableValueFilter(
        rdbmsService, randomizedVariable, List.of(variableValueFilter, otherVariableValueFilter));
  }

  @TestTemplate
  public void shouldFindVariableWithMultipleNamesAndValuesFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // GIVEN 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication);

    // AND twp name filters
    final VariableValueFilter variableValueFilter =
        new Builder().name(randomizedVariable.name()).eq(randomizedVariable.value()).build();
    final VariableValueFilter otherVariableValueFilter =
        new Builder().name("not there").eq("no").build();

    // WHEN we search for it, we should find one
    searchAndAssertVariableValueFilter(
        rdbmsService, randomizedVariable, List.of(variableValueFilter, otherVariableValueFilter));
  }

  private VariableDbModel prepareRandomVariablesAndReturnOne(
      final CamundaRdbmsTestApplication testApplication) {
    return prepareRandomVariablesAndReturnOne(testApplication, null);
  }

  private VariableDbModel prepareRandomVariablesAndReturnOne(
      final CamundaRdbmsTestApplication testApplication, final String value) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final Long scopeKey = VariableFixtures.nextKey();
    createAndSaveRandomVariables(rdbmsService, scopeKey);
    final VariableDbModel randomizedVariable;
    if (value != null) {
      randomizedVariable =
          VariableFixtures.createRandomized(b -> b.scopeKey(scopeKey).value(value));
    } else {
      randomizedVariable = VariableFixtures.createRandomized(b -> b.scopeKey(scopeKey));
    }
    createAndSaveVariable(rdbmsService, randomizedVariable);

    return randomizedVariable;
  }

  private void searchAndAssertVariableValueFilter(
      final RdbmsService rdbmsService,
      final VariableDbModel variableDbModel,
      final VariableValueFilter variableValueFilter) {
    searchAndAssertVariableValueFilter(rdbmsService, variableDbModel, List.of(variableValueFilter));
  }

  private void searchAndAssertVariableValueFilter(
      final RdbmsService rdbmsService,
      final VariableDbModel variableDbModel,
      final List<VariableValueFilter> variableValueFilter) {
    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableDbQuery(
                    new VariableFilter.Builder()
                        .scopeKeys(variableDbModel.scopeKey())
                        .variable(variableValueFilter)
                        .build(),
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(variableDbModel.key());
    assertThat(searchResult.hits().getFirst().name()).isEqualTo(variableDbModel.name());
  }
}
