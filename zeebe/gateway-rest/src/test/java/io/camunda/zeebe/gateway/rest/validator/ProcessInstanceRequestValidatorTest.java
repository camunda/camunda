/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCreationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceMigrationBatchOperationPlan;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ProblemDetail;

@DisplayName("ProcessInstanceRequestValidator Tests")
class ProcessInstanceRequestValidatorTest {

  @Test
  @DisplayName("Should accept valid processDefinitionKey format")
  void shouldAcceptValidProcessDefinitionKey() {
    final var request = new ProcessInstanceCreationInstruction();
    request.setProcessDefinitionKey("123456789");

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "12.34", "12abc", "", " "})
  @DisplayName("Should reject invalid processDefinitionKey formats")
  void shouldRejectInvalidProcessDefinitionKey(String invalidKey) {
    final var request = new ProcessInstanceCreationInstruction();
    request.setProcessDefinitionKey(invalidKey);

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(problem.getDetail()).contains("processDefinitionKey");
    assertThat(problem.getDetail())
        .contains(
            "is not a valid key. Expected a numeric value. Did you pass an entity id instead of an entity key?");
  }

  @Test
  @DisplayName("Should accept null processDefinitionKey when processDefinitionId is provided")
  void shouldAcceptNullProcessDefinitionKeyWhenIdProvided() {
    final var request = new ProcessInstanceCreationInstruction();
    request.setProcessDefinitionKey(null);
    request.setProcessDefinitionId("process-id");

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should reject when both processDefinitionKey and processDefinitionId are null")
  void shouldRejectWhenBothKeyAndIdAreNull() {
    final var request = new ProcessInstanceCreationInstruction();
    request.setProcessDefinitionKey(null);
    request.setProcessDefinitionId(null);

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getDetail())
        .contains("At least one of [processDefinitionId, processDefinitionKey] is required");
  }

  @Test
  @DisplayName("Should reject when both processDefinitionKey and processDefinitionId are provided")
  void shouldRejectWhenBothKeyAndIdProvided() {
    final var request = new ProcessInstanceCreationInstruction();
    request.setProcessDefinitionKey("123456789");
    request.setProcessDefinitionId("process-id");

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getDetail())
        .contains("Only one of [processDefinitionId, processDefinitionKey] is allowed");
  }

  @Test
  @DisplayName("Should accept valid targetProcessDefinitionKey format in migration request")
  void shouldAcceptValidTargetProcessDefinitionKey() {
    final var request = new ProcessInstanceMigrationBatchOperationPlan();
    request.setTargetProcessDefinitionKey("987654321");
    request.setMappingInstructions(java.util.List.of());

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateMigrateProcessInstanceBatchOperationRequest(
            request);

    // Should have validation error for empty mappingInstructions, but not for key format
    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getDetail()).contains("mappingInstructions");
    assertThat(problem.getDetail())
        .doesNotContain("targetProcessDefinitionKey must be a valid Long");
  }

  @ParameterizedTest
  @ValueSource(strings = {"xyz", "99.99", "99xyz", "", " "})
  @DisplayName("Should reject invalid targetProcessDefinitionKey formats in migration request")
  void shouldRejectInvalidTargetProcessDefinitionKey(String invalidKey) {
    final var request = new ProcessInstanceMigrationBatchOperationPlan();
    request.setTargetProcessDefinitionKey(invalidKey);
    request.setMappingInstructions(java.util.List.of());

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateMigrateProcessInstanceBatchOperationRequest(
            request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(problem.getDetail()).contains("targetProcessDefinitionKey");
    assertThat(problem.getDetail())
        .contains(
            "is not a valid key. Expected a numeric value. Did you pass an entity id instead of an entity key?");
  }

  @Test
  @DisplayName("Should handle edge case Long values")
  void shouldHandleEdgeCaseLongValues() {
    final var request = new ProcessInstanceCreationInstruction();

    // Test with maximum Long value
    request.setProcessDefinitionKey(String.valueOf(Long.MAX_VALUE));

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should reject Long values that are too large")
  void shouldRejectLongValuesTooLarge() {
    final var request = new ProcessInstanceCreationInstruction();
    // Create a number larger than Long.MAX_VALUE
    request.setProcessDefinitionKey("99999999999999999999999999999");

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(problem.getDetail()).contains("processDefinitionKey");
    assertThat(problem.getDetail())
        .contains(
            "is not a valid key. Expected a numeric value. Did you pass an entity id instead of an entity key?");
  }

  @Test
  @DisplayName("Should accept zero as valid Long value")
  void shouldAcceptZeroAsValidLong() {
    final var request = new ProcessInstanceCreationInstruction();
    request.setProcessDefinitionKey("0");

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept negative Long values")
  void shouldAcceptNegativeLongValues() {
    final var request = new ProcessInstanceCreationInstruction();
    request.setProcessDefinitionKey("-123456789");

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }
}
