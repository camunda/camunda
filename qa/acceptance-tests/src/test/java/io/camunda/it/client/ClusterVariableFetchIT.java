/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class ClusterVariableFetchIT {

  private static final String VALUE_RESULT = "\"%s\"";

  private static CamundaClient camundaClient;

  // ============ GET TESTS ============
  @Test
  void shouldGetGlobalScopedClusterVariable() {
    // given
    final var variableName = "globalVarGet_" + UUID.randomUUID();
    final var variableValue = "testValue_" + UUID.randomUUID();

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, variableValue)
        .send()
        .join();

    // wait for data to be indexed
    TestHelper.waitForClusterVariableToBeIndexed(camundaClient, variableName, variableValue);

    // when
    final var response =
        camundaClient
            .newGloballyScopedClusterVariableGetRequest()
            .withName(variableName)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getName()).isEqualTo(variableName);
    assertThat(response.getValue()).isEqualTo(VALUE_RESULT.formatted(variableValue));
  }

  @Test
  void shouldGetTenantScopedClusterVariable() {
    // given
    final var variableName = "tenantVarGet_" + UUID.randomUUID();
    final var variableValue = "testValue_" + UUID.randomUUID();
    final var tenantId = "tenant_1";

    camundaClient
        .newTenantScopedClusterVariableCreateRequest(tenantId)
        .create(variableName, variableValue)
        .send()
        .join();

    // wait for data to be indexed
    TestHelper.waitForClusterVariableToBeIndexed(
        camundaClient, variableName, tenantId, variableValue);

    // when
    final var response =
        camundaClient
            .newTenantScopedClusterVariableGetRequest(tenantId)
            .withName(variableName)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getName()).isEqualTo(variableName);
    assertThat(response.getValue()).isEqualTo(VALUE_RESULT.formatted(variableValue));
    assertThat(response.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void shouldRejectGetIfVariableNameIsNull() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableGetRequest()
                    .withName(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRejectGetIfVariableNameIsEmpty() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableGetRequest()
                    .withName("")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be empty");
  }

  @Test
  void shouldRejectGetIfGlobalVariableDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableGetRequest()
                    .withName("nonExistentVar_" + UUID.randomUUID())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldRejectGetIfTenantVariableDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newTenantScopedClusterVariableGetRequest("tenant_1")
                    .withName("nonExistentVar_" + UUID.randomUUID())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldNotAllowGettingGlobalVariableWithTenantScope() {
    // given
    final var variableName = "globalVarNoTenantGet_" + UUID.randomUUID();
    final var variableValue = "testValue_" + UUID.randomUUID();

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, variableValue)
        .send()
        .join();

    final var tenantId = "tenant_1";

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newTenantScopedClusterVariableGetRequest(tenantId)
                    .withName(variableName)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldNotAllowGettingTenantVariableWithGlobalScope() {
    // given
    final var variableName = "tenantVarNoGlobalGet_" + UUID.randomUUID();
    final var variableValue = "testValue_" + UUID.randomUUID();
    final var tenantId = "tenant_1";

    camundaClient
        .newTenantScopedClusterVariableCreateRequest(tenantId)
        .create(variableName, variableValue)
        .send()
        .join();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableGetRequest()
                    .withName(variableName)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}
