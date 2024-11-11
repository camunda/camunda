/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.variables;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.createAndSaveVariable;
import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.prepareRandomVariables;
import static io.camunda.it.rdbms.db.fixtures.VariableFixtures.prepareRandomVariablesAndReturnOne;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.it.rdbms.db.fixtures.VariableFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.search.filter.VariableValueFilter.Builder;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.VariableQuery;
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
    // given 20 random variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    prepareRandomVariables(testApplication);

    // and one variable with a specific name
    final String varName = "var-name-" + nextStringId();
    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.name(varName));
    createAndSaveVariable(rdbmsService, randomizedVariable);

    // when we search for it, then we should find one
    searchAndAssertVariableValueFilters(
        testApplication.getRdbmsService(), randomizedVariable, varName, null);
  }

  @TestTemplate
  public void shouldFindVariableWithNameAndEqValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication);

    // and an eq value filter
    final Operation<String> operation = Operation.eq(randomizedVariable.value());

    // when we search for it, we should find one
    searchAndAssertVariableValueFilter(
        rdbmsService, randomizedVariable, randomizedVariable.name(), operation);
  }

  @TestTemplate
  public void shouldFindVariableWithNameAndEqNumberValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String varName = "var-name-" + nextStringId();
    final var randomizedVariable =
        prepareRandomVariablesAndReturnOne(testApplication, varName, "42");

    // and an eq value filter
    final Operation<String> operation = Operation.eq("42");

    // when we search for it, we should find one
    searchAndAssertVariableValueFilter(
        rdbmsService, randomizedVariable, randomizedVariable.name(), operation);
  }

  @TestTemplate
  public void shouldFindVariableWithNameAndEqBooleanValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String varName = "var-name-" + nextStringId();
    final var randomizedVariable =
        prepareRandomVariablesAndReturnOne(testApplication, varName, "true");

    // and an eq value filter
    final Operation<String> operation = Operation.eq("true");

    // when we search for it, we should find one
    searchAndAssertVariableValueFilter(
        rdbmsService, randomizedVariable, randomizedVariable.name(), operation);
  }

  @TestTemplate
  public void shouldFindVariableWithNameAndNeqValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given 20 random variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    prepareRandomVariables(testApplication);

    // and one variable with a specific name
    final String varName = "var-name-" + nextStringId();
    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.name(varName));
    createAndSaveVariable(rdbmsService, randomizedVariable);

    // and a neq value filter
    final Operation<String> operation = Operation.neq("DEFINITELY NOT");

    // when we search for it, we should find one
    searchAndAssertVariableValueFilter(rdbmsService, randomizedVariable, varName, operation);
  }

  @TestTemplate
  public void shouldFindVariableWithNameAndGtValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String varName = "var-name-" + nextStringId();
    final var randomizedVariable =
        prepareRandomVariablesAndReturnOne(testApplication, varName, "42000");

    // and a gt value filter
    final Operation<String> operation = Operation.gt("40000");

    // when we search for it, we should find one
    searchAndAssertVariableValueFilter(rdbmsService, randomizedVariable, varName, operation);
  }

  @TestTemplate
  public void shouldFindVariableWithNameAndGteValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String varName = "var-name-" + nextStringId();
    final var randomizedVariable =
        prepareRandomVariablesAndReturnOne(testApplication, varName, "42000");

    // and a gte value filter
    final Operation<String> operation = Operation.gte("42000");

    // when we search for it, we should find one
    searchAndAssertVariableValueFilter(rdbmsService, randomizedVariable, varName, operation);
  }

  @TestTemplate
  public void shouldFindVariableWithNameAndLtValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String varName = "var-name-" + nextStringId();
    final var randomizedVariable =
        prepareRandomVariablesAndReturnOne(testApplication, varName, "-42000");

    // and a lt value filter
    final Operation<String> operation = Operation.lt("-40000");

    // when we search for it, we should find one
    searchAndAssertVariableValueFilter(rdbmsService, randomizedVariable, varName, operation);
  }

  @TestTemplate
  public void shouldFindVariableWithNameAndLteValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String varName = "var-name-" + nextStringId();
    final var randomizedVariable =
        prepareRandomVariablesAndReturnOne(testApplication, varName, "-42000");

    // and a lte value filter
    final Operation<String> operation = Operation.lte("-42000");

    // when we search for it, we should find one
    searchAndAssertVariableValueFilter(rdbmsService, randomizedVariable, varName, operation);
  }

  @TestTemplate
  public void shouldFindVariableWithMultipleNamesFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var randomizedVariable = prepareRandomVariablesAndReturnOne(testApplication);

    // and two name filters
    final VariableValueFilter variableValueFilter =
        new Builder().name(randomizedVariable.name()).build();
    final VariableValueFilter otherVariableValueFilter = new Builder().name("not there").build();

    // and two name filters
    final var variableFilter =
        new VariableFilter.Builder()
            .scopeKeys(randomizedVariable.scopeKey())
            .names(randomizedVariable.name(), "not there")
            .build();

    // when we search for it, we should find one
    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    variableFilter,
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(randomizedVariable.key());
    assertThat(searchResult.hits().getFirst().name()).isEqualTo(randomizedVariable.name());
  }

  @TestTemplate
  public void shouldFindVariableWithMultipleNamesAndValuesFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given 21 variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var varName = prepareRandomVariablesAndReturnOne(testApplication).name();
    final VariableDbModel randomizedVariable =
        VariableFixtures.createRandomized(b -> b.name(varName));
    createAndSaveVariable(rdbmsService, randomizedVariable);

    // and two name with value filters
    final var variableFilter =
        new VariableFilter.Builder()
            .names(varName, "not there")
            .values(randomizedVariable.value(), "no")
            .build();

    // when we search for it, we should find one
    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    variableFilter,
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(randomizedVariable.key());
    assertThat(searchResult.hits().getFirst().name()).isEqualTo(randomizedVariable.name());
  }

  private static void searchAndAssertVariableValueFilter(
      final RdbmsService rdbmsService,
      final VariableDbModel variableDbModel,
      final String variableName,
      final Operation<String> operation) {
    searchAndAssertVariableValueFilters(
        rdbmsService, variableDbModel, variableName, List.of(operation));
  }

  private static void searchAndAssertVariableValueFilters(
      final RdbmsService rdbmsService,
      final VariableDbModel variableDbModel,
      final String variableName,
      final List<Operation<String>> operations) {

    final var builder = new VariableFilter.Builder().names(variableName);
    if (operations != null) {
      builder.valueOperations(operations).build();
    }

    final var searchResult =
        rdbmsService
            .getVariableReader()
            .search(
                new VariableQuery(
                    builder.build(),
                    VariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key()).isEqualTo(variableDbModel.key());
    assertThat(searchResult.hits().getFirst().name()).isEqualTo(variableDbModel.name());
  }
}
