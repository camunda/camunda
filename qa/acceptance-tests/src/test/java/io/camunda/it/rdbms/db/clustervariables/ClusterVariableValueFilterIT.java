/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.clustervariables;

import static io.camunda.it.rdbms.db.fixtures.ClusterVariableFixtures.createAndSaveRandomsTenantClusterVariablesWithFixedTenantId;
import static io.camunda.it.rdbms.db.fixtures.ClusterVariableFixtures.createRandomTenantClusterVariable;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.generateRandomString;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.domain.ClusterVariableDbModel;
import io.camunda.db.rdbms.write.domain.ClusterVariableDbModel.ClusterVariableDbModelBuilder;
import io.camunda.it.rdbms.db.fixtures.ClusterVariableFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.entities.ClusterVariableScope;
import io.camunda.search.filter.ClusterVariableFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.search.sort.ClusterVariableSort;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ClusterVariableValueFilterIT {

  @TestTemplate
  public void shouldFindClusterVariableWithNameFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given 20 random tenant variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    createAndSaveRandomsTenantClusterVariablesWithFixedTenantId(rdbmsService, tenantId);

    // and one variable with a specific name
    final String varName = "var-name-" + nextStringId();
    final ClusterVariableDbModel randomizedVariable =
        createRandomTenantClusterVariable(generateRandomString(50));
    final ClusterVariableDbModel variableWithFixedName =
        randomizedVariable.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .name(varName)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, variableWithFixedName);

    // when we search for it, then we should find one
    searchAndAssertClusterVariableValueFilters(
        testApplication.getRdbmsService(), variableWithFixedName, varName, tenantId, null);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithNameAndEqValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final String valueForOne = "value-42000";
    final ClusterVariableDbModel randomizedVariable =
        createRandomTenantClusterVariable(valueForOne);
    final ClusterVariableDbModel variableWithFixedName =
        randomizedVariable.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .name(varName)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, variableWithFixedName);

    // and an eq value filter
    final Operation<String> operation = Operation.eq(valueForOne);

    // when we search for it, we should find one
    searchAndAssertClusterVariableValueFilter(
        rdbmsService, variableWithFixedName, varName, tenantId, operation);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithNameAndLikeValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "someName";
    final String valueWithPattern = "variable-42-value";
    final ClusterVariableDbModel randomizedVariable =
        createRandomTenantClusterVariable(valueWithPattern);
    final ClusterVariableDbModel variableWithFixedName =
        randomizedVariable.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .name(varName)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, variableWithFixedName);

    // and a like value filter
    final Operation<String> operation = Operation.like("*able-42-v?l*");

    // when we search for it, we should find one
    searchAndAssertClusterVariableValueFilter(
        rdbmsService, variableWithFixedName, varName, tenantId, operation);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithNameAndEqNumberValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final String numberValue = "42000";
    final ClusterVariableDbModel randomizedVariable =
        createRandomTenantClusterVariable(numberValue);
    final ClusterVariableDbModel variableWithFixedName =
        randomizedVariable.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .name(varName)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, variableWithFixedName);

    // and an eq value filter
    final Operation<String> operation = Operation.eq("42000");

    // when we search for it, we should find one
    searchAndAssertClusterVariableValueFilter(
        rdbmsService, variableWithFixedName, varName, tenantId, operation);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithNameAndEqBooleanValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final String boolValue = "true";
    final ClusterVariableDbModel randomizedVariable = createRandomTenantClusterVariable(boolValue);
    final ClusterVariableDbModel variableWithFixedName =
        randomizedVariable.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .name(varName)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, variableWithFixedName);

    // and an eq value filter
    final Operation<String> operation = Operation.eq("true");

    // when we search for it, we should find one
    searchAndAssertClusterVariableValueFilter(
        rdbmsService, variableWithFixedName, varName, tenantId, operation);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithNameAndNeqValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given 20 random variables
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    createAndSaveRandomsTenantClusterVariablesWithFixedTenantId(rdbmsService, tenantId);

    // and one variable with a specific name
    final String varName = "var-name-" + nextStringId();
    final ClusterVariableDbModel randomizedVariable =
        createRandomTenantClusterVariable(generateRandomString(50));
    final ClusterVariableDbModel variableWithFixedName =
        randomizedVariable.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .name(varName)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, variableWithFixedName);

    // and a neq value filter
    final Operation<String> operation = Operation.neq("DEFINITELY NOT");

    // when we search for it, we should find one
    searchAndAssertClusterVariableValueFilter(
        rdbmsService, variableWithFixedName, varName, tenantId, operation);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithNameAndGtValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final String numValue = "42000";
    final ClusterVariableDbModel randomizedVariable = createRandomTenantClusterVariable(numValue);
    final ClusterVariableDbModel variableWithFixedName =
        randomizedVariable.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .name(varName)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, variableWithFixedName);

    // and a gt value filter
    final Operation<String> operation = Operation.gt("40000");

    // when we search for it, we should find one
    searchAndAssertClusterVariableValueFilter(
        rdbmsService, variableWithFixedName, varName, tenantId, operation);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithNameAndGteValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final String numValue = "42000";
    final ClusterVariableDbModel randomizedVariable = createRandomTenantClusterVariable(numValue);
    final ClusterVariableDbModel variableWithFixedName =
        randomizedVariable.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .name(varName)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, variableWithFixedName);

    // and a gte value filter
    final Operation<String> operation = Operation.gte("42000");

    // when we search for it, we should find one
    searchAndAssertClusterVariableValueFilter(
        rdbmsService, variableWithFixedName, varName, tenantId, operation);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithNameAndLtValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final String negValue = "-42000";
    final ClusterVariableDbModel randomizedVariable = createRandomTenantClusterVariable(negValue);
    final ClusterVariableDbModel variableWithFixedName =
        randomizedVariable.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .name(varName)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, variableWithFixedName);

    // and a lt value filter
    final Operation<String> operation = Operation.lt("-40000");

    // when we search for it, we should find one
    searchAndAssertClusterVariableValueFilter(
        rdbmsService, variableWithFixedName, varName, tenantId, operation);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithNameAndLteValue(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final String negValue = "-42000";
    final ClusterVariableDbModel randomizedVariable = createRandomTenantClusterVariable(negValue);
    final ClusterVariableDbModel variableWithFixedName =
        randomizedVariable.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .name(varName)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, variableWithFixedName);

    // and a lte value filter
    final Operation<String> operation = Operation.lte("-42000");

    // when we search for it, we should find one
    searchAndAssertClusterVariableValueFilter(
        rdbmsService, variableWithFixedName, varName, tenantId, operation);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithNameAndLtValueDouble(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final String doubleValue = "-4200.1234";
    final ClusterVariableDbModel randomizedVariable =
        createRandomTenantClusterVariable(doubleValue);
    final ClusterVariableDbModel variableWithFixedName =
        randomizedVariable.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .name(varName)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, variableWithFixedName);

    // and a lt value filter
    final Operation<String> operation = Operation.lt("-4200.123");

    // when we search for it, we should find one
    searchAndAssertClusterVariableValueFilter(
        rdbmsService, variableWithFixedName, varName, tenantId, operation);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithMultipleNamesFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final ClusterVariableDbModel randomizedVariable =
        createRandomTenantClusterVariable(generateRandomString(50));
    final ClusterVariableDbModel variableWithFixedName =
        randomizedVariable.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .name(varName)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, variableWithFixedName);

    // and two name filters
    final var clusterVariableFilter =
        new ClusterVariableFilter.Builder()
            .scopes(ClusterVariableScope.TENANT.name())
            .tenantIds(tenantId)
            .names(varName, "not there")
            .build();

    // when we search for it, we should find one
    final var searchResult =
        rdbmsService
            .getClusterVariableReader()
            .search(
                new ClusterVariableQuery(
                    clusterVariableFilter,
                    ClusterVariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().name()).isEqualTo(varName);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithMultipleNamesAndValuesFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "unique-name" + generateRandomString(10);
    final String varValue = generateRandomString(50);
    final ClusterVariableDbModel randomizedVariable = createRandomTenantClusterVariable(varValue);
    final ClusterVariableDbModel variableWithFixedName =
        randomizedVariable.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .name(varName)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, variableWithFixedName);

    // and two name with value filters
    final var clusterVariableFilter =
        new ClusterVariableFilter.Builder()
            .scopes(ClusterVariableScope.TENANT.name())
            .tenantIds(tenantId)
            .names(varName, "not there")
            .values(varValue)
            .build();

    // when we search for it, we should find one
    final var searchResult =
        rdbmsService
            .getClusterVariableReader()
            .search(
                new ClusterVariableQuery(
                    clusterVariableFilter,
                    ClusterVariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().name()).isEqualTo(varName);
    assertThat(searchResult.items().getFirst().value()).isEqualTo(varValue);
  }

  @TestTemplate
  public void shouldTransformAnyWildcard(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var tenantId = nextStringId();
    final var variableValue = "transformAnyValue";
    final ClusterVariableDbModel var1 =
        createRandomTenantClusterVariable(variableValue + "%wildcards1");
    final ClusterVariableDbModel var1Fixed =
        var1.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, var1Fixed);

    final ClusterVariableDbModel var2 =
        createRandomTenantClusterVariable(variableValue + "%wildcards2");
    final ClusterVariableDbModel var2Fixed =
        var2.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, var2Fixed);

    // when
    final var actual =
        rdbmsService
            .getClusterVariableReader()
            .search(
                ClusterVariableQuery.of(
                    b ->
                        b.filter(
                            f ->
                                f.scopes(ClusterVariableScope.TENANT.name())
                                    .tenantIds(tenantId)
                                    .valueOperations(Operation.like(variableValue + "*")))));

    // then
    assertThat(actual.total()).isEqualTo(2);
    assertThat(actual.items()).hasSize(2);
    for (final ClusterVariableEntity item : actual.items()) {
      assertThat(item.value()).startsWith(variableValue);
    }
  }

  @TestTemplate
  public void shouldTransformSingleWildcard(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var tenantId = nextStringId();
    final var variableValue = "transformSingleValue";
    final ClusterVariableDbModel var1 = createRandomTenantClusterVariable(variableValue + "X");
    final ClusterVariableDbModel var1Fixed =
        var1.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, var1Fixed);

    final ClusterVariableDbModel var2 = createRandomTenantClusterVariable(variableValue + "Y");
    final ClusterVariableDbModel var2Fixed =
        var2.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, var2Fixed);

    // when
    final var actual =
        rdbmsService
            .getClusterVariableReader()
            .search(
                ClusterVariableQuery.of(
                    b ->
                        b.filter(
                            f ->
                                f.scopes(ClusterVariableScope.TENANT.name())
                                    .tenantIds(tenantId)
                                    .valueOperations(Operation.like(variableValue + "?")))));

    // then
    assertThat(actual.total()).isEqualTo(2);
    assertThat(actual.items()).hasSize(2);
    for (final ClusterVariableEntity item : actual.items()) {
      assertThat(item.value()).startsWith(variableValue);
    }
  }

  @TestTemplate
  public void shouldIgnoreEscapedSQLAnyWildcard(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var tenantId = nextStringId();
    final var variableValue = "ignoreAnyValue%X";
    final ClusterVariableDbModel var1 = createRandomTenantClusterVariable(variableValue);
    final ClusterVariableDbModel var1Fixed =
        var1.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, var1Fixed);

    final ClusterVariableDbModel var2 = createRandomTenantClusterVariable("ignoreAnyValueXX");
    final ClusterVariableDbModel var2Fixed =
        var2.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, var2Fixed);

    // when
    final var actual =
        rdbmsService
            .getClusterVariableReader()
            .search(
                ClusterVariableQuery.of(
                    b ->
                        b.filter(
                            f ->
                                f.scopes(ClusterVariableScope.TENANT.name())
                                    .tenantIds(tenantId)
                                    .valueOperations(Operation.like("ignoreAnyValue\\%X")))));

    // then
    assertThat(actual.total()).isEqualTo(1);
    assertThat(actual.items()).hasSize(1);
    assertThat(actual.items().getFirst().value()).isEqualTo(variableValue);
  }

  @TestTemplate
  public void shouldIgnoreEscapedSQLSingleWildcard(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var tenantId = nextStringId();
    final var variableValue = "ignoreSingleValue_X";
    final ClusterVariableDbModel var1 = createRandomTenantClusterVariable(variableValue);
    final ClusterVariableDbModel var1Fixed =
        var1.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, var1Fixed);

    final ClusterVariableDbModel var2 = createRandomTenantClusterVariable("ignoreSingleValueXX");
    final ClusterVariableDbModel var2Fixed =
        var2.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, var2Fixed);

    // when
    final var actual =
        rdbmsService
            .getClusterVariableReader()
            .search(
                ClusterVariableQuery.of(
                    b ->
                        b.filter(
                            f ->
                                f.scopes(ClusterVariableScope.TENANT.name())
                                    .tenantIds(tenantId)
                                    .valueOperations(Operation.like("ignoreSingleValue\\_X")))));

    // then
    assertThat(actual.total()).isEqualTo(1);
    assertThat(actual.items()).hasSize(1);
    assertThat(actual.items().getFirst().value()).isEqualTo(variableValue);
  }

  @TestTemplate
  public void shouldUnescapeESAnyWildcard(final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var tenantId = nextStringId();
    final String varValue = "value*any%wildcards1";
    final ClusterVariableDbModel var1 = createRandomTenantClusterVariable(varValue);
    final ClusterVariableDbModel var1Fixed =
        var1.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, var1Fixed);

    final String varValue2 = "value*any%wildcards2";
    final ClusterVariableDbModel var2 = createRandomTenantClusterVariable(varValue2);
    final ClusterVariableDbModel var2Fixed =
        var2.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, var2Fixed);

    // when
    final var actual =
        rdbmsService
            .getClusterVariableReader()
            .search(
                ClusterVariableQuery.of(
                    b ->
                        b.filter(
                            f ->
                                f.scopes(ClusterVariableScope.TENANT.name())
                                    .tenantIds(tenantId)
                                    .valueOperations(Operation.like("value\\*any\\%*")))));

    // then
    assertThat(actual.total()).isEqualTo(2);
    assertThat(actual.items()).hasSize(2);
    assertThat(actual.items()).extracting("value").containsExactlyInAnyOrder(varValue, varValue2);
  }

  @TestTemplate
  public void shouldUnescapeESSingleWildcard(final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var tenantId = nextStringId();
    final String varValue = "value?single_wildcards1";
    final ClusterVariableDbModel var1 = createRandomTenantClusterVariable(varValue);
    final ClusterVariableDbModel var1Fixed =
        var1.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, var1Fixed);

    final String varValue2 = "value?single_wildcards2";
    final ClusterVariableDbModel var2 = createRandomTenantClusterVariable(varValue2);
    final ClusterVariableDbModel var2Fixed =
        var2.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT));
    ClusterVariableFixtures.createAndSaveVariables(rdbmsService, var2Fixed);

    // when
    final var actual =
        rdbmsService
            .getClusterVariableReader()
            .search(
                ClusterVariableQuery.of(
                    b ->
                        b.filter(
                            f ->
                                f.scopes(ClusterVariableScope.TENANT.name())
                                    .tenantIds(tenantId)
                                    .valueOperations(
                                        Operation.like("value\\?single\\_wildcards?")))));

    // then
    assertThat(actual.total()).isEqualTo(2);
    assertThat(actual.items()).hasSize(2);
    assertThat(actual.items()).extracting("value").containsExactlyInAnyOrder(varValue, varValue2);
  }

  private static void searchAndAssertClusterVariableValueFilter(
      final RdbmsService rdbmsService,
      final ClusterVariableDbModel clusterVariableDbModel,
      final String variableName,
      final String tenantId,
      final Operation<String> operation) {
    searchAndAssertClusterVariableValueFilters(
        rdbmsService, clusterVariableDbModel, variableName, tenantId, List.of(operation));
  }

  private static void searchAndAssertClusterVariableValueFilters(
      final RdbmsService rdbmsService,
      final ClusterVariableDbModel clusterVariableDbModel,
      final String variableName,
      final String tenantId,
      final List<Operation<String>> operations) {

    final var builder =
        new ClusterVariableFilter.Builder()
            .scopes(ClusterVariableScope.TENANT.name())
            .tenantIds(tenantId)
            .names(variableName);
    if (operations != null) {
      builder.valueOperations(operations).build();
    }

    final var searchResult =
        rdbmsService
            .getClusterVariableReader()
            .search(
                new ClusterVariableQuery(
                    builder.build(),
                    ClusterVariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().name()).isEqualTo(clusterVariableDbModel.name());
  }
}
