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
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class ClusterVariableCommandTest {

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
            .newClusterVariableCreateRequest()
            .atGlobalScoped()
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
    final var tenantId = "tenant_" + UUID.randomUUID();

    // when
    final var response =
        camundaClient
            .newClusterVariableCreateRequest()
            .atTenantScoped(tenantId)
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
                    .newClusterVariableCreateRequest()
                    .atGlobalScoped()
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
                    .newClusterVariableCreateRequest()
                    .atGlobalScoped()
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
                    .newClusterVariableCreateRequest()
                    .atTenantScoped("")
                    .create("variableName", "value")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Invalid cluster variable scope. Tenant-scoped variables must have a non-blank tenant ID.");
  }

  @Test
  void shouldRejectCreationIfDuplicateGlobalVariable() {
    // given
    final var variableName = "duplicateGlobalVar_" + UUID.randomUUID();
    final var variableValue = "testValue_" + UUID.randomUUID();

    camundaClient
        .newClusterVariableCreateRequest()
        .atGlobalScoped()
        .create(variableName, variableValue)
        .send()
        .join();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newClusterVariableCreateRequest()
                    .atGlobalScoped()
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
    final var tenantId = "tenant_" + UUID.randomUUID();

    camundaClient
        .newClusterVariableCreateRequest()
        .atTenantScoped(tenantId)
        .create(variableName, variableValue)
        .send()
        .join();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newClusterVariableCreateRequest()
                    .atTenantScoped(tenantId)
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
    final var tenantId = "tenant_" + UUID.randomUUID();

    // when - create in global scope
    final var response1 =
        camundaClient
            .newClusterVariableCreateRequest()
            .atGlobalScoped()
            .create(variableName, globalValue)
            .send()
            .join();

    // then - should be able to create same name in tenant scope
    final var response2 =
        camundaClient
            .newClusterVariableCreateRequest()
            .atTenantScoped(tenantId)
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
        .newClusterVariableCreateRequest()
        .atGlobalScoped()
        .create(variableName, variableValue)
        .send()
        .join();

    // when
    camundaClient
        .newClusterVariableDeleteRequest()
        .atGlobalScoped()
        .delete(variableName)
        .send()
        .join();

    // then - verify deletion by attempting to recreate
    assertThatCode(
            () ->
                camundaClient
                    .newClusterVariableCreateRequest()
                    .atGlobalScoped()
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
    final var tenantId = "tenant_" + UUID.randomUUID();

    camundaClient
        .newClusterVariableCreateRequest()
        .atTenantScoped(tenantId)
        .create(variableName, variableValue)
        .send()
        .join();

    // when
    assertThatCode(
            () ->
                camundaClient
                    .newClusterVariableDeleteRequest()
                    .atTenantScoped(tenantId)
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
                    .newClusterVariableDeleteRequest()
                    .atGlobalScoped()
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
                    .newClusterVariableDeleteRequest()
                    .atGlobalScoped()
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
                    .newClusterVariableDeleteRequest()
                    .atGlobalScoped()
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
                    .newClusterVariableDeleteRequest()
                    .atTenantScoped("tenant_" + UUID.randomUUID())
                    .delete("nonExistentVar_" + UUID.randomUUID())
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
        .newClusterVariableCreateRequest()
        .atGlobalScoped()
        .create(variableName, variableValue)
        .send()
        .join();

    final var tenantId = "tenant_" + UUID.randomUUID();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newClusterVariableDeleteRequest()
                    .atTenantScoped(tenantId)
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
    final var tenantId = "tenant_" + UUID.randomUUID();

    camundaClient
        .newClusterVariableCreateRequest()
        .atTenantScoped(tenantId)
        .create(variableName, variableValue)
        .send()
        .join();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newClusterVariableDeleteRequest()
                    .atGlobalScoped()
                    .delete(variableName)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}
