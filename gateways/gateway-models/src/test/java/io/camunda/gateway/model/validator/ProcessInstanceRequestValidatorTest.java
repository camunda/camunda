/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.model.validator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionById;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionByKey;
import io.camunda.gateway.protocol.model.ProcessInstanceFilter;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationBatchOperationPlan;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationBatchOperationRequest;
import java.util.Optional;
import java.util.Set;
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
    final var request = new ProcessInstanceCreationInstructionByKey();
    request.setProcessDefinitionKey("123456789");

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept valid tags")
  void shouldAcceptValidTags() {
    final var request = new ProcessInstanceCreationInstructionByKey();
    request.setProcessDefinitionKey("123456789");
    request.setTags(Set.of("valid-tag", "another-tag"));

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should reject invalid tags")
  void shouldRejectInvalidTags() {
    final var request = new ProcessInstanceCreationInstructionByKey();
    request.setProcessDefinitionKey("123456789");
    request.setTags(Set.of("1 invalid-tag", "another-tag"));

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(problem.getDetail()).contains("is not valid. Tags must start with a letter");
  }

  @Test
  @DisplayName("Should reject too many tags")
  void shouldRejectTooManyTags() {
    final var request = new ProcessInstanceCreationInstructionByKey();
    request.setProcessDefinitionKey("123456789");
    request.setTags(Set.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"));

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(problem.getDetail()).contains("The provided number of tags");
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "12.34", "12abc", "", " "})
  @DisplayName("Should reject invalid processDefinitionKey formats")
  void shouldRejectInvalidProcessDefinitionKey(final String invalidKey) {
    final var request = new ProcessInstanceCreationInstructionByKey();
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
    final var request = new ProcessInstanceCreationInstructionById();
    request.setProcessDefinitionId("process-id");

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept valid targetProcessDefinitionKey format in migration request")
  void shouldAcceptValidTargetProcessDefinitionKey() {

    final var migrationPlan = new ProcessInstanceMigrationBatchOperationPlan();
    migrationPlan.setTargetProcessDefinitionKey("987654321");
    migrationPlan.setMappingInstructions(java.util.List.of());

    final var request = new ProcessInstanceMigrationBatchOperationRequest();
    request.setFilter(new ProcessInstanceFilter());
    request.setMigrationPlan(migrationPlan);

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
  void shouldRejectInvalidTargetProcessDefinitionKey(final String invalidKey) {
    final var migrationPlan = new ProcessInstanceMigrationBatchOperationPlan();
    migrationPlan.setTargetProcessDefinitionKey(invalidKey);
    migrationPlan.setMappingInstructions(java.util.List.of());

    final var request = new ProcessInstanceMigrationBatchOperationRequest();
    request.setFilter(new ProcessInstanceFilter());
    request.setMigrationPlan(migrationPlan);

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
    final var request = new ProcessInstanceCreationInstructionByKey();

    // Test with maximum Long value
    request.setProcessDefinitionKey(String.valueOf(Long.MAX_VALUE));

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should reject Long values that are too large")
  void shouldRejectLongValuesTooLarge() {
    final var request = new ProcessInstanceCreationInstructionByKey();
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
    final var request = new ProcessInstanceCreationInstructionByKey();
    request.setProcessDefinitionKey("0");

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept negative Long values")
  void shouldAcceptNegativeLongValues() {
    final var request = new ProcessInstanceCreationInstructionByKey();
    request.setProcessDefinitionKey("-123456789");

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }
}
