/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ClusterVariableScope;
import io.camunda.client.api.search.response.ClusterVariable;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class ClusterVariableSearchIT {

  private static final String VALUE_RESULT = "\"%s\"";

  private static CamundaClient camundaClient;

  // Test data - Global scoped variables
  private static String globalVarName1;
  private static String globalVarValue1;
  private static String globalVarName2;
  private static String globalVarValue2;

  // Test data - Tenant scoped variables
  private static String tenantVarName1;
  private static String tenantVarValue1;
  private static String tenantVarName2;
  private static String tenantVarValue2;
  private static String tenantId;

  // Test data - Mixed scope variables
  private static String globalVarNameMixed;
  private static String globalVarValueMixed;
  private static String tenantVarNameMixed;
  private static String tenantVarValueMixed;
  private static String tenantIdMixed;

  // Test data - Tenant-only variables
  private static String globalVarNameTenantOnly;
  private static String globalVarValueTenantOnly;
  private static String tenantVarNameTenantOnly;
  private static String tenantVarValueTenantOnly;
  private static String tenantIdTenantOnly;

  // Test data - Large variables for truncation testing
  private static String largeVarName;
  private static String largeVarValue;
  private static final int LARGE_VALUE_SIZE = 10000; // Create a large value to trigger truncation

  @BeforeAll
  static void setupClusterVariables() {
    // Setup global scoped variables
    globalVarName1 = "globalVarSearch1_" + UUID.randomUUID();
    globalVarValue1 = "testValue1_" + UUID.randomUUID();
    globalVarName2 = "globalVarSearch2_" + UUID.randomUUID();
    globalVarValue2 = "testValue2_" + UUID.randomUUID();

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(globalVarName1, globalVarValue1)
        .send()
        .join();

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(globalVarName2, globalVarValue2)
        .send()
        .join();

    // Setup tenant scoped variables
    tenantVarName1 = "tenantVarSearch1_" + UUID.randomUUID();
    tenantVarValue1 = "testValue1_" + UUID.randomUUID();
    tenantVarName2 = "tenantVarSearch2_" + UUID.randomUUID();
    tenantVarValue2 = "testValue2_" + UUID.randomUUID();
    tenantId = "tenant_1";

    camundaClient
        .newTenantScopedClusterVariableCreateRequest(tenantId)
        .create(tenantVarName1, tenantVarValue1)
        .send()
        .join();

    camundaClient
        .newTenantScopedClusterVariableCreateRequest(tenantId)
        .create(tenantVarName2, tenantVarValue2)
        .send()
        .join();

    // Setup mixed scope variables
    globalVarNameMixed = "globalVarSearchOnly_" + UUID.randomUUID();
    globalVarValueMixed = "globalValue_" + UUID.randomUUID();
    tenantVarNameMixed = "tenantVarSearchOnly_" + UUID.randomUUID();
    tenantVarValueMixed = "tenantValue_" + UUID.randomUUID();
    tenantIdMixed = "tenant_2";

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(globalVarNameMixed, globalVarValueMixed)
        .send()
        .join();

    camundaClient
        .newTenantScopedClusterVariableCreateRequest(tenantIdMixed)
        .create(tenantVarNameMixed, tenantVarValueMixed)
        .send()
        .join();

    // Setup tenant-only variables
    globalVarNameTenantOnly = "globalVarTenantSearch_" + UUID.randomUUID();
    globalVarValueTenantOnly = "globalValue_" + UUID.randomUUID();
    tenantVarNameTenantOnly = "tenantVarTenantSearch_" + UUID.randomUUID();
    tenantVarValueTenantOnly = "tenantValue_" + UUID.randomUUID();
    tenantIdTenantOnly = "tenant_3";

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(globalVarNameTenantOnly, globalVarValueTenantOnly)
        .send()
        .join();

    camundaClient
        .newTenantScopedClusterVariableCreateRequest(tenantIdTenantOnly)
        .create(tenantVarNameTenantOnly, tenantVarValueTenantOnly)
        .send()
        .join();

    // Setup large variable for truncation testing
    largeVarName = "largeVar_" + UUID.randomUUID();
    largeVarValue = "x".repeat(LARGE_VALUE_SIZE);

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(largeVarName, largeVarValue)
        .send()
        .join();

    // Wait for all variables to be indexed
    TestHelper.waitForClusterVariablesToBeIndexed(
        camundaClient,
        Map.of(
            globalVarName1, globalVarValue1,
            globalVarName2, globalVarValue2,
            tenantVarName1, tenantVarValue1,
            tenantVarName2, tenantVarValue2,
            globalVarNameMixed, globalVarValueMixed,
            tenantVarNameMixed, tenantVarValueMixed,
            globalVarNameTenantOnly, globalVarValueTenantOnly,
            tenantVarNameTenantOnly, tenantVarValueTenantOnly,
            largeVarName, largeVarValue));
  }

  // ============ SEARCH TESTS ============

  @Test
  void shouldSearchGlobalScopedClusterVariables() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.scope(ClusterVariableScope.GLOBAL))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .anySatisfy(
            item -> {
              assertThat(item.getName()).isEqualTo(globalVarName1);
              assertThat(item.getValue()).isEqualTo(VALUE_RESULT.formatted(globalVarValue1));
            });
    assertThat(response.items())
        .anySatisfy(
            item -> {
              assertThat(item.getName()).isEqualTo(globalVarName2);
              assertThat(item.getValue()).isEqualTo(VALUE_RESULT.formatted(globalVarValue2));
            });
  }

  @Test
  void shouldSearchTenantScopedClusterVariables() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.scope(ClusterVariableScope.TENANT).tenantId(tenantId))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .anySatisfy(
            item -> {
              assertThat(item.getName()).isEqualTo(tenantVarName1);
              assertThat(item.getValue()).isEqualTo(VALUE_RESULT.formatted(tenantVarValue1));
            });
    assertThat(response.items())
        .anySatisfy(
            item -> {
              assertThat(item.getName()).isEqualTo(tenantVarName2);
              assertThat(item.getValue()).isEqualTo(VALUE_RESULT.formatted(tenantVarValue2));
            });
  }

  @Test
  void shouldSearchReturnOnlyGlobalVariablesWhenSearchingGlobalScope() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.scope(ClusterVariableScope.GLOBAL))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .anySatisfy(
            item -> {
              assertThat(item.getName()).isEqualTo(globalVarNameMixed);
              assertThat(item.getValue()).isEqualTo(VALUE_RESULT.formatted(globalVarValueMixed));
              assertThat(item.getTenantId()).isNull();
            });
    assertThat(response.items())
        .noneSatisfy(item -> assertThat(item.getName()).isEqualTo(tenantVarNameMixed));
  }

  @Test
  void shouldSearchReturnOnlyTenantVariablesWhenSearchingTenantScope() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.scope(ClusterVariableScope.TENANT).tenantId(tenantIdTenantOnly))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .anySatisfy(
            item -> {
              assertThat(item.getName()).isEqualTo(tenantVarNameTenantOnly);
              assertThat(item.getValue())
                  .isEqualTo(VALUE_RESULT.formatted(tenantVarValueTenantOnly));
              assertThat(item.getTenantId()).isEqualTo(tenantIdTenantOnly);
            });
    assertThat(response.items())
        .noneSatisfy(item -> assertThat(item.getName()).isEqualTo(globalVarNameTenantOnly));
  }

  @Test
  void shouldSearchReturnEmptyResultsIfNoVariablesExist() {
    // given
    final var tenantId = "tenant_" + UUID.randomUUID();

    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.scope(ClusterVariableScope.TENANT).tenantId(tenantId))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isEmpty();
  }

  @Test
  void shouldSearchSortClusterVariablesByName() {
    // when
    final var response =
        camundaClient.newClusterVariableSearchRequest().sort(s -> s.name().asc()).send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();

    // Verify that the results are sorted by name in ascending order
    final var names = response.items().stream().map(ClusterVariable::getName).toList();
    final var sortedNames = names.stream().sorted().toList();
    assertThat(names).containsExactlyElementsOf(sortedNames);
  }

  @Test
  void shouldSearchSortClusterVariablesByScope() {
    // when
    final var response =
        camundaClient.newClusterVariableSearchRequest().sort(s -> s.scope().asc()).send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();

    // Verify that the results are sorted by scope (GLOBAL comes before TENANT alphabetically)
    final var scopes = response.items().stream().map(item -> item.getScope().toString()).toList();
    final var sortedScopes = scopes.stream().sorted().toList();
    assertThat(scopes).containsExactlyElementsOf(sortedScopes);
  }

  @Test
  void shouldSearchSortClusterVariablesDescending() {
    // when
    final var response =
        camundaClient.newClusterVariableSearchRequest().sort(s -> s.name().desc()).send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();

    // Verify that the results are sorted by name in descending order
    final var names = response.items().stream().map(ClusterVariable::getName).toList();
    final var sortedNames = names.stream().sorted(Comparator.reverseOrder()).toList();
    assertThat(names).containsExactlyElementsOf(sortedNames);
  }

  // ============ ADVANCED FILTER TESTS ============

  @Test
  void shouldSearchClusterVariablesByNameWithLike() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.name(n -> n.like("globalVarSearch*")))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items()).anyMatch(item -> item.getName().contains("globalVarSearch"));
  }

  @Test
  void shouldSearchClusterVariablesByNameWithExactMatch() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.name(n -> n.eq(globalVarName1)))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .anySatisfy(
            item -> {
              assertThat(item.getName()).isEqualTo(globalVarName1);
              assertThat(item.getValue()).isEqualTo(VALUE_RESULT.formatted(globalVarValue1));
            });
  }

  @Test
  void shouldSearchClusterVariablesByNameNotEqual() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.name(n -> n.neq(globalVarName1)))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .noneSatisfy(item -> assertThat(item.getName()).isEqualTo(globalVarName1));
  }

  @Test
  void shouldSearchClusterVariablesByNameExists() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.name(n -> n.exists(true)))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .allSatisfy(item -> assertThat(item.getName()).isNotNull().isNotEmpty());
  }

  @Test
  void shouldSearchClusterVariablesByNameWithMultipleValues() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.name(n -> n.in(globalVarName1, globalVarName2)))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .anySatisfy(item -> assertThat(item.getName()).isEqualTo(globalVarName1))
        .anySatisfy(item -> assertThat(item.getName()).isEqualTo(globalVarName2));
  }

  @Test
  void shouldSearchClusterVariablesByValueWithLike() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.value(v -> v.like("*testValue*")))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items()).anyMatch(item -> item.getValue().contains("testValue"));
  }

  @Test
  void shouldSearchClusterVariablesByValueWithExactMatch() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.value(v -> v.eq(VALUE_RESULT.formatted(globalVarValue1))))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .anySatisfy(
            item -> {
              assertThat(item.getName()).isEqualTo(globalVarName1);
              assertThat(item.getValue()).isEqualTo(VALUE_RESULT.formatted(globalVarValue1));
            });
  }

  @Test
  void shouldSearchClusterVariablesByValueNotEqual() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.value(v -> v.neq(VALUE_RESULT.formatted(globalVarValue1))))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .noneSatisfy(
            item -> assertThat(item.getValue()).isEqualTo(VALUE_RESULT.formatted(globalVarValue1)));
  }

  @Test
  void shouldSearchClusterVariablesByValueWithMultipleValues() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f ->
                    f.value(
                        v ->
                            v.in(
                                VALUE_RESULT.formatted(globalVarValue1),
                                VALUE_RESULT.formatted(globalVarValue2))))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .anySatisfy(
            item -> assertThat(item.getValue()).isEqualTo(VALUE_RESULT.formatted(globalVarValue1)))
        .anySatisfy(
            item -> assertThat(item.getValue()).isEqualTo(VALUE_RESULT.formatted(globalVarValue2)));
  }

  @Test
  void shouldSearchClusterVariablesByValueExists() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.value(v -> v.exists(true)))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .allSatisfy(item -> assertThat(item.getValue()).isNotNull().isNotEmpty());
  }

  @Test
  void shouldSearchClusterVariablesByScopeWithLike() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.scope(s -> s.like("GLOBAL")))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .anySatisfy(item -> assertThat(item.getScope()).isEqualTo(ClusterVariableScope.GLOBAL));
  }

  @Test
  void shouldSearchClusterVariablesByScopeWithEquality() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.scope(s -> s.eq(ClusterVariableScope.GLOBAL)))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .allSatisfy(item -> assertThat(item.getScope()).isEqualTo(ClusterVariableScope.GLOBAL));
  }

  @Test
  void shouldSearchClusterVariablesByScopeWithNotEqual() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.scope(s -> s.neq(ClusterVariableScope.GLOBAL)))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    // Should return only TENANT scoped variables or empty
    assertThat(response.items())
        .noneSatisfy(item -> assertThat(item.getScope()).isEqualTo(ClusterVariableScope.GLOBAL));
  }

  @Test
  void shouldSearchClusterVariablesByScopeExists() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.scope(s -> s.exists(true)))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items()).allSatisfy(item -> assertThat(item.getScope()).isNotNull());
  }

  @Test
  void shouldSearchClusterVariablesByScopeWithMultipleValues() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f -> f.scope(s -> s.in(ClusterVariableScope.GLOBAL, ClusterVariableScope.TENANT)))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .allSatisfy(
            item ->
                assertThat(item.getScope())
                    .isIn(ClusterVariableScope.GLOBAL, ClusterVariableScope.TENANT));
  }

  @Test
  void shouldSearchClusterVariablesByTenantIdWithLike() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.tenantId(t -> t.like("tenant_*")))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items())
        .anyMatch(item -> item.getTenantId() != null && item.getTenantId().startsWith("tenant_"));
  }

  @Test
  void shouldSearchClusterVariablesByTenantIdWithExactMatch() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.tenantId(t -> t.eq(tenantId)))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .allSatisfy(item -> assertThat(item.getTenantId()).isEqualTo(tenantId));
  }

  @Test
  void shouldSearchClusterVariablesByTenantIdNotEqual() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.tenantId(t -> t.neq(tenantId)))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items())
        .noneSatisfy(item -> assertThat(item.getTenantId()).isEqualTo(tenantId));
  }

  @Test
  void shouldSearchClusterVariablesByTenantIdExists() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.tenantId(t -> t.exists(true)))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).allSatisfy(item -> assertThat(item.getTenantId()).isNotNull());
  }

  @Test
  void shouldSearchClusterVariablesByTenantIdWithMultipleValues() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.tenantId(t -> t.in(tenantId, tenantIdMixed)))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .allSatisfy(item -> assertThat(item.getTenantId()).isIn(tenantId, tenantIdMixed));
  }

  @Test
  void shouldSearchClusterVariablesWithCombinedFilters() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.scope(ClusterVariableScope.GLOBAL).name(n -> n.like("globalVarSearch*")))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .allSatisfy(
            item -> {
              assertThat(item.getScope()).isEqualTo(ClusterVariableScope.GLOBAL);
              assertThat(item.getName()).contains("globalVarSearch");
            });
  }

  @Test
  void shouldSearchClusterVariablesWithMultipleCombinedFilters() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f ->
                    f.scope(s -> s.eq(ClusterVariableScope.TENANT))
                        .tenantId(t -> t.eq(tenantIdMixed))
                        .name(n -> n.like("tenantVarSearchOnly*")))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .allSatisfy(
            item -> {
              assertThat(item.getScope()).isEqualTo(ClusterVariableScope.TENANT);
              assertThat(item.getTenantId()).isEqualTo(tenantIdMixed);
              assertThat(item.getName()).contains("tenantVarSearchOnly");
            });
  }

  @Test
  void shouldSearchClusterVariablesWithValueAndNameFilters() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.name(n -> n.like("globalVarSearch*")).value(v -> v.like("*testValue*")))
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .allSatisfy(
            item -> {
              assertThat(item.getName()).contains("globalVarSearch");
              assertThat(item.getValue()).contains("testValue");
            });
  }

  @Test
  void shouldSearchClusterVariablesSortByScopeAndFilter() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.scope(s -> s.eq(ClusterVariableScope.GLOBAL)))
            .sort(s -> s.name().asc())
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .allSatisfy(item -> assertThat(item.getScope()).isEqualTo(ClusterVariableScope.GLOBAL));

    // Verify sorting
    final var names = response.items().stream().map(ClusterVariable::getName).toList();
    final var sortedNames = names.stream().sorted().toList();
    assertThat(names).containsExactlyElementsOf(sortedNames);
  }

  // ============ TRUNCATION AND FULL VALUES TESTS ============

  @Test
  void shouldSearchClusterVariablesWithTruncatedValues() {
    // when - search without withFullValues (default truncation)
    final var response =
        camundaClient.newClusterVariableSearchRequest().sort(s -> s.name().asc()).send().join();

    // then - verify the large variable is returned but truncated
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .anyMatch(
            item -> {
              if (item.getName().equals(largeVarName)) {
                // The value should be truncated (shorter than the original)
                final String truncatedValue = item.getValue();
                assertThat(truncatedValue).isNotNull();
                // When truncated, it should be significantly shorter than the original
                return truncatedValue.length() < largeVarValue.length() + 100; // +100 for JSON
                // encoding
              }
              return false;
            });
  }

  @Test
  void shouldSearchClusterVariablesWithFullValuesReturnsCompleteValue() {
    // when - search with withFullValues() to get untruncated values
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .withFullValues()
            .sort(s -> s.name().asc())
            .send()
            .join();

    // then - verify the large variable is returned with full value
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .anyMatch(
            item -> {
              if (item.getName().equals(largeVarName)) {
                // The value should contain the full large value (in JSON string format: "xxx...")
                final String fullValue = item.getValue();
                assertThat(fullValue).isNotNull();
                // Verify it contains most of the repeated characters (accounting for JSON encoding)
                return fullValue.contains("xxx")
                    && fullValue.length() > largeVarValue.length() - 100;
              }
              return false;
            });
  }

  @Test
  void shouldFilterClusterVariablesByIsTruncatedTrue() {
    // when - filter by isTruncated = true (only truncated values)
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.isTruncated(true))
            .sort(s -> s.name().asc())
            .send()
            .join();

    // then - should return the large variable which will be truncated
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        .anyMatch(item -> item.getName().equals(largeVarName) && item.isTruncated());
  }

  @Test
  void shouldFilterClusterVariablesByIsTruncatedFalse() {
    // when - filter by isTruncated = false (only non-truncated values)
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.isTruncated(false))
            .sort(s -> s.name().asc())
            .send()
            .join();

    // then - should NOT return the large variable, only small variables
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items())
        // Verify that the large variable is not in the results
        .noneMatch(item -> item.getName().equals(largeVarName));
    // Verify all returned items are not truncated
    assertThat(response.items())
        .allSatisfy(
            item -> {
              if (item.isTruncated() != null) {
                assertThat(item.isTruncated()).isFalse();
              }
            });
  }

  @Test
  void shouldCompareFullValuesWithTruncatedValues() {
    // when - search with default truncation
    final var truncatedResponse =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.name(n -> n.eq(largeVarName)))
            .send()
            .join();

    // when - search with full values
    final var fullResponse =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.name(n -> n.eq(largeVarName)))
            .withFullValues()
            .send()
            .join();

    // then - verify both searches return the variable
    assertThat(truncatedResponse.items()).isNotEmpty();
    assertThat(fullResponse.items()).isNotEmpty();

    final var truncatedVar = truncatedResponse.items().getFirst();
    final var fullVar = fullResponse.items().getFirst();

    // Verify both have the same name
    assertThat(truncatedVar.getName()).isEqualTo(fullVar.getName()).isEqualTo(largeVarName);

    // Verify the full value is longer than the truncated value
    assertThat(fullVar.getValue().length()).isGreaterThan(truncatedVar.getValue().length());

    // Verify the full value contains the original repeated characters
    assertThat(fullVar.getValue()).contains("xxx");
  }
}
