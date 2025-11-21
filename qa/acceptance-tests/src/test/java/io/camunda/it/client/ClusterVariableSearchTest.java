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
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class ClusterVariableSearchTest {

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

  @BeforeAll
  static void setupClusterVariables() {
    // Setup global scoped variables
    globalVarName1 = "globalVarSearch1_" + UUID.randomUUID();
    globalVarValue1 = "testValue1_" + UUID.randomUUID();
    globalVarName2 = "globalVarSearch2_" + UUID.randomUUID();
    globalVarValue2 = "testValue2_" + UUID.randomUUID();

    camundaClient
        .newClusterVariableCreateRequest()
        .atGlobalScoped()
        .create(globalVarName1, globalVarValue1)
        .send()
        .join();

    camundaClient
        .newClusterVariableCreateRequest()
        .atGlobalScoped()
        .create(globalVarName2, globalVarValue2)
        .send()
        .join();

    // Setup tenant scoped variables
    tenantVarName1 = "tenantVarSearch1_" + UUID.randomUUID();
    tenantVarValue1 = "testValue1_" + UUID.randomUUID();
    tenantVarName2 = "tenantVarSearch2_" + UUID.randomUUID();
    tenantVarValue2 = "testValue2_" + UUID.randomUUID();
    tenantId = "tenant_" + UUID.randomUUID();

    camundaClient
        .newClusterVariableCreateRequest()
        .atTenantScoped(tenantId)
        .create(tenantVarName1, tenantVarValue1)
        .send()
        .join();

    camundaClient
        .newClusterVariableCreateRequest()
        .atTenantScoped(tenantId)
        .create(tenantVarName2, tenantVarValue2)
        .send()
        .join();

    // Setup mixed scope variables
    globalVarNameMixed = "globalVarSearchOnly_" + UUID.randomUUID();
    globalVarValueMixed = "globalValue_" + UUID.randomUUID();
    tenantVarNameMixed = "tenantVarSearchOnly_" + UUID.randomUUID();
    tenantVarValueMixed = "tenantValue_" + UUID.randomUUID();
    tenantIdMixed = "tenant_" + UUID.randomUUID();

    camundaClient
        .newClusterVariableCreateRequest()
        .atGlobalScoped()
        .create(globalVarNameMixed, globalVarValueMixed)
        .send()
        .join();

    camundaClient
        .newClusterVariableCreateRequest()
        .atTenantScoped(tenantIdMixed)
        .create(tenantVarNameMixed, tenantVarValueMixed)
        .send()
        .join();

    // Setup tenant-only variables
    globalVarNameTenantOnly = "globalVarTenantSearch_" + UUID.randomUUID();
    globalVarValueTenantOnly = "globalValue_" + UUID.randomUUID();
    tenantVarNameTenantOnly = "tenantVarTenantSearch_" + UUID.randomUUID();
    tenantVarValueTenantOnly = "tenantValue_" + UUID.randomUUID();
    tenantIdTenantOnly = "tenant_" + UUID.randomUUID();

    camundaClient
        .newClusterVariableCreateRequest()
        .atGlobalScoped()
        .create(globalVarNameTenantOnly, globalVarValueTenantOnly)
        .send()
        .join();

    camundaClient
        .newClusterVariableCreateRequest()
        .atTenantScoped(tenantIdTenantOnly)
        .create(tenantVarNameTenantOnly, tenantVarValueTenantOnly)
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
            tenantVarNameTenantOnly, tenantVarValueTenantOnly));
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
}
