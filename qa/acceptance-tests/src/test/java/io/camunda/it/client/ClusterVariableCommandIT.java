/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class ClusterVariableCommandIT {

  private static final String VALUE_RESULT = "\"%s\"";

  private static CamundaClient camundaClient;

  // ============ CREATE TESTS ============

  @Test
  void shouldCreateGlobalScopedClusterVariable() {
    // given
    final var variableName = "globalVar_" + UUID.randomUUID();
    final var variableValue = "testValue_" + UUID.randomUUID();

    // when
    final var response =
        camundaClient
            .newGloballyScopedClusterVariableCreateRequest()
            .create(variableName, variableValue)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getName()).isEqualTo(variableName);
    assertThat(response.getValue()).isEqualTo(VALUE_RESULT.formatted(variableValue));
  }

  @Test
  void shouldCreateTenantScopedClusterVariable() {
    // given
    final var variableName = "tenantVar_" + UUID.randomUUID();
    final var variableValue = "testValue_" + UUID.randomUUID();
    final var tenantId = "tenant_1";

    // when
    final var response =
        camundaClient
            .newTenantScopedClusterVariableCreateRequest(tenantId)
            .create(variableName, variableValue)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getName()).isEqualTo(variableName);
    assertThat(response.getValue()).isEqualTo(VALUE_RESULT.formatted(variableValue));
  }

  @Test
  void shouldRejectCreationIfVariableNameIsNull() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableCreateRequest()
                    .create(null, "value")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRejectCreationIfVariableNameIsEmpty() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableCreateRequest()
                    .create("", "value")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be empty");
  }

  @Test
  void shouldRejectCreationIfTenantIdIsEmpty() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newTenantScopedClusterVariableCreateRequest("")
                    .create("variableName", "value")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }

  @Test
  void shouldRejectCreationIfDuplicateGlobalVariable() {
    // given
    final var variableName = "duplicateGlobalVar_" + UUID.randomUUID();
    final var variableValue = "testValue_" + UUID.randomUUID();

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, variableValue)
        .send()
        .join();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableCreateRequest()
                    .create(variableName, "newValue")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'");
  }

  @Test
  void shouldRejectCreationIfDuplicateTenantVariable() {
    // given
    final var variableName = "duplicateTenantVar_" + UUID.randomUUID();
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
                    .newTenantScopedClusterVariableCreateRequest(tenantId)
                    .create(variableName, "newValue")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'");
  }

  @Test
  void shouldAllowSameNameInDifferentScopes() {
    // given
    final var variableName = "sameNameVar_" + UUID.randomUUID();
    final var globalValue = "globalValue_" + UUID.randomUUID();
    final var tenantValue = "tenantValue_" + UUID.randomUUID();
    final var tenantId = "tenant_1";

    // when - create in global scope
    final var response1 =
        camundaClient
            .newGloballyScopedClusterVariableCreateRequest()
            .create(variableName, globalValue)
            .send()
            .join();

    // then - should be able to create same name in tenant scope
    final var response2 =
        camundaClient
            .newTenantScopedClusterVariableCreateRequest(tenantId)
            .create(variableName, tenantValue)
            .send()
            .join();

    assertThat(response1).isNotNull();
    assertThat(response1.getName()).isEqualTo(variableName);
    assertThat(response1.getValue()).isEqualTo(VALUE_RESULT.formatted(globalValue));

    assertThat(response2).isNotNull();
    assertThat(response2.getName()).isEqualTo(variableName);
    assertThat(response2.getValue()).isEqualTo(VALUE_RESULT.formatted(tenantValue));
    assertThat(response2.getTenantId()).isEqualTo(tenantId);
  }

  // ============ DELETE TESTS ============
  @Test
  void shouldDeleteGlobalScopedClusterVariable() {
    // given
    final var variableName = "globalVarDelete_" + UUID.randomUUID();
    final var variableValue = "testValue_" + UUID.randomUUID();

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, variableValue)
        .send()
        .join();

    // when
    camundaClient
        .newGloballyScopedClusterVariableDeleteRequest()
        .delete(variableName)
        .send()
        .join();

    // then - verify deletion by attempting to recreate
    assertThatCode(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableCreateRequest()
                    .create(variableName, variableValue)
                    .send()
                    .join())
        .doesNotThrowAnyException();
  }

  @Test
  void shouldDeleteTenantScopedClusterVariable() {
    // given
    final var variableName = "tenantVarDelete_" + UUID.randomUUID();
    final var variableValue = "testValue_" + UUID.randomUUID();
    final var tenantId = "tenant_1";

    camundaClient
        .newTenantScopedClusterVariableCreateRequest(tenantId)
        .create(variableName, variableValue)
        .send()
        .join();

    // when
    assertThatCode(
            () ->
                camundaClient
                    .newTenantScopedClusterVariableDeleteRequest(tenantId)
                    .delete(variableName)
                    .send()
                    .join())
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectDeletionIfVariableNameIsNull() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableDeleteRequest()
                    .delete(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRejectDeletionIfVariableNameIsEmpty() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableDeleteRequest()
                    .delete("")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be empty");
  }

  @Test
  void shouldRejectDeletionIfGlobalVariableDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableDeleteRequest()
                    .delete("nonExistentVar_" + UUID.randomUUID())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldRejectDeletionIfTenantVariableDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newTenantScopedClusterVariableDeleteRequest("tenant_123")
                    .delete("nonExistentVar_657")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldNotAllowDeletingGlobalVariableWithTenantScope() {
    // given
    final var variableName = "globalVarNoTenantDelete_" + UUID.randomUUID();
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
                    .newTenantScopedClusterVariableDeleteRequest(tenantId)
                    .delete(variableName)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldNotAllowDeletingTenantVariableWithGlobalScope() {
    // given
    final var variableName = "tenantVarNoGlobalDelete_" + UUID.randomUUID();
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
                    .newGloballyScopedClusterVariableDeleteRequest()
                    .delete(variableName)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  // ============ UPDATE TESTS ============

  @Test
  void shouldUpdateGlobalScopedClusterVariable() {
    // given
    final var variableName = "globalVarUpdate_" + UUID.randomUUID();
    final var initialValue = "initialValue_" + UUID.randomUUID();
    final var updatedValue = "updatedValue_" + UUID.randomUUID();

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, initialValue)
        .send()
        .join();

    // when
    final var response =
        camundaClient
            .newGloballyScopedClusterVariableUpdateRequest()
            .update(variableName, updatedValue)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getName()).isEqualTo(variableName);
    assertThat(response.getValue()).isEqualTo(VALUE_RESULT.formatted(updatedValue));
  }

  @Test
  void shouldUpdateTenantScopedClusterVariable() {
    // given
    final var variableName = "tenantVarUpdate_" + UUID.randomUUID();
    final var initialValue = "initialValue_" + UUID.randomUUID();
    final var updatedValue = "updatedValue_" + UUID.randomUUID();
    final var tenantId = "tenant_1";

    camundaClient
        .newTenantScopedClusterVariableCreateRequest(tenantId)
        .create(variableName, initialValue)
        .send()
        .join();

    // when
    final var response =
        camundaClient
            .newTenantScopedClusterVariableUpdateRequest(tenantId)
            .update(variableName, updatedValue)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getName()).isEqualTo(variableName);
    assertThat(response.getValue()).isEqualTo(VALUE_RESULT.formatted(updatedValue));
    assertThat(response.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void shouldRejectUpdateIfVariableNameIsNull() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableUpdateRequest()
                    .update(null, "value")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRejectUpdateIfVariableNameIsEmpty() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableUpdateRequest()
                    .update("", "value")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be empty");
  }

  @Test
  void shouldRejectUpdateIfTenantIdIsNull() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newTenantScopedClusterVariableUpdateRequest(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  @Test
  void shouldRejectUpdateIfTenantIdIsEmpty() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newTenantScopedClusterVariableUpdateRequest("")
                    .update("variableName", "value")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }

  @Test
  void shouldRejectUpdateIfGlobalVariableDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableUpdateRequest()
                    .update("nonExistentVar_" + UUID.randomUUID(), "newValue")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldRejectUpdateIfTenantVariableDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newTenantScopedClusterVariableUpdateRequest("tenant_123")
                    .update("nonExistentVar_" + UUID.randomUUID(), "newValue")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldNotAllowUpdatingGlobalVariableWithTenantScope() {
    // given
    final var variableName = "globalVarNoTenantUpdate_" + UUID.randomUUID();
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
                    .newTenantScopedClusterVariableUpdateRequest(tenantId)
                    .update(variableName, "newValue")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldNotAllowUpdatingTenantVariableWithGlobalScope() {
    // given
    final var variableName = "tenantVarNoGlobalUpdate_" + UUID.randomUUID();
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
                    .newGloballyScopedClusterVariableUpdateRequest()
                    .update(variableName, "newValue")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldUpdateVariableMultipleTimes() {
    // given
    final var variableName = "multiUpdateVar_" + UUID.randomUUID();
    final var initialValue = "initialValue_" + UUID.randomUUID();
    final var secondValue = "secondValue_" + UUID.randomUUID();
    final var thirdValue = "thirdValue_" + UUID.randomUUID();

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, initialValue)
        .send()
        .join();

    // when - first update
    final var response1 =
        camundaClient
            .newGloballyScopedClusterVariableUpdateRequest()
            .update(variableName, secondValue)
            .send()
            .join();

    // then
    assertThat(response1.getValue()).isEqualTo(VALUE_RESULT.formatted(secondValue));

    // when - second update
    final var response2 =
        camundaClient
            .newGloballyScopedClusterVariableUpdateRequest()
            .update(variableName, thirdValue)
            .send()
            .join();

    // then
    assertThat(response2.getValue()).isEqualTo(VALUE_RESULT.formatted(thirdValue));
  }
}
