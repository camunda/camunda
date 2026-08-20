/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

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
    final var request =
        ProcessInstanceCreationInstructionByKey.Builder.create()
            .processDefinitionKey("123456789")
            .build();

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept valid tags")
  void shouldAcceptValidTags() {
    final var request =
        ProcessInstanceCreationInstructionByKey.Builder.create()
            .processDefinitionKey("123456789")
            .build();
    request.setTags(Set.of("valid-tag", "another-tag"));

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should reject invalid tags")
  void shouldRejectInvalidTags() {
    final var request =
        ProcessInstanceCreationInstructionByKey.Builder.create()
            .processDefinitionKey("123456789")
            .build();
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
    final var request =
        ProcessInstanceCreationInstructionByKey.Builder.create()
            .processDefinitionKey("123456789")
            .build();
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
    final var request =
        ProcessInstanceCreationInstructionByKey.Builder.create()
            .processDefinitionKey(invalidKey)
            .build();

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
    final var request =
        ProcessInstanceCreationInstructionById.Builder.create()
            .processDefinitionId("process-id")
            .build();

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept valid targetProcessDefinitionKey format in migration request")
  void shouldAcceptValidTargetProcessDefinitionKey() {
    final var migrationPlan =
        ProcessInstanceMigrationBatchOperationPlan.Builder.create()
            .targetProcessDefinitionKey("987654321")
            .mappingInstructions(java.util.List.of())
            .build();
    final var request =
        ProcessInstanceMigrationBatchOperationRequest.Builder.create()
            .filter(ProcessInstanceFilter.Builder.create().build())
            .migrationPlan(migrationPlan)
            .build();

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
    final var migrationPlan =
        ProcessInstanceMigrationBatchOperationPlan.Builder.create()
            .targetProcessDefinitionKey(invalidKey)
            .mappingInstructions(java.util.List.of())
            .build();
    final var request =
        ProcessInstanceMigrationBatchOperationRequest.Builder.create()
            .filter(ProcessInstanceFilter.Builder.create().build())
            .migrationPlan(migrationPlan)
            .build();

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
    // Test with maximum Long value
    final var request =
        ProcessInstanceCreationInstructionByKey.Builder.create()
            .processDefinitionKey(String.valueOf(Long.MAX_VALUE))
            .build();

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should reject Long values that are too large")
  void shouldRejectLongValuesTooLarge() {
    // Create a number larger than Long.MAX_VALUE
    final var request =
        ProcessInstanceCreationInstructionByKey.Builder.create()
            .processDefinitionKey("99999999999999999999999999999")
            .build();

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
    final var request =
        ProcessInstanceCreationInstructionByKey.Builder.create().processDefinitionKey("0").build();

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept negative Long values")
  void shouldAcceptNegativeLongValues() {
    final var request =
        ProcessInstanceCreationInstructionByKey.Builder.create()
            .processDefinitionKey("-123456789")
            .build();

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should reject businessId exceeding max length when creating by key")
  void shouldRejectBusinessIdExceedingMaxLengthByKey() {
    final var request =
        ProcessInstanceCreationInstructionByKey.Builder.create()
            .processDefinitionKey("123456789")
            .build();
    request.setBusinessId("a".repeat(257));

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(problem.getDetail()).contains("businessId").contains("256");
  }

  @Test
  @DisplayName("Should reject businessId exceeding max length when creating by id")
  void shouldRejectBusinessIdExceedingMaxLengthById() {
    final var request =
        ProcessInstanceCreationInstructionById.Builder.create()
            .processDefinitionId("process-id")
            .build();
    request.setBusinessId("a".repeat(257));

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(problem.getDetail()).contains("businessId").contains("256");
  }

  @Test
  @DisplayName("Should reject businessId exceeding max length in simple API")
  void shouldRejectBusinessIdExceedingMaxLengthSimpleApi() {
    final var request =
        new io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationInstruction();
    request.setProcessDefinitionId("process-id");
    request.setBusinessId("a".repeat(257));

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateSimpleCreateProcessInstanceRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(problem.getDetail()).contains("businessId").contains("256");
  }

  @Test
  @DisplayName("Should accept businessId with exactly max length")
  void shouldAcceptBusinessIdWithMaxLength() {
    final var request =
        ProcessInstanceCreationInstructionByKey.Builder.create()
            .processDefinitionKey("123456789")
            .build();
    request.setBusinessId("a".repeat(256));

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept null businessId")
  void shouldAcceptNullBusinessId() {
    final var request =
        ProcessInstanceCreationInstructionByKey.Builder.create()
            .processDefinitionKey("123456789")
            .build();
    request.setBusinessId(null);

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept empty businessId")
  void shouldAcceptEmptyBusinessId() {
    final var request =
        ProcessInstanceCreationInstructionByKey.Builder.create()
            .processDefinitionKey("123456789")
            .build();
    request.setBusinessId("");

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }
}
