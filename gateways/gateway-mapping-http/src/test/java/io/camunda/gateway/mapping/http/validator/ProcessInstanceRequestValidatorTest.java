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
import io.camunda.search.filter.Operation;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

  private static io.camunda.search.filter.ProcessInstanceFilter.Builder searchFilter() {
    return new io.camunda.search.filter.ProcessInstanceFilter.Builder();
  }

  @Test
  void shouldRejectTopLevelInvalidStateEqForSuspend() {
    // given
    final var filter = searchFilter().states("COMPLETED").build();

    // when
    final var result =
        ProcessInstanceRequestValidator.validateSuspendProcessInstanceBatchOperationFilter(filter);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(result.get().getStatus()).isEqualTo(400);
    assertThat(result.get().getDetail())
        .isEqualTo("The value for state is 'COMPLETED' but must be one of [ACTIVE].");
  }

  @Test
  void shouldRejectTopLevelInvalidStateInForSuspend() {
    // given
    final var filter = searchFilter().stateOperations(Operation.in("ACTIVE", "SUSPENDED")).build();

    // when
    final var result =
        ProcessInstanceRequestValidator.validateSuspendProcessInstanceBatchOperationFilter(filter);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail())
        .isEqualTo("The value for state is 'SUSPENDED' but must be one of [ACTIVE].");
  }

  @Test
  void shouldRejectTopLevelInvalidStateEqForResume() {
    // given
    final var filter = searchFilter().states("ACTIVE").build();

    // when
    final var result =
        ProcessInstanceRequestValidator.validateResumeProcessInstanceBatchOperationFilter(filter);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(result.get().getDetail())
        .isEqualTo("The value for state is 'ACTIVE' but must be one of [SUSPENDED].");
  }

  @Test
  void shouldRejectTopLevelInvalidStateInForResume() {
    // given
    final var filter =
        searchFilter().stateOperations(Operation.in("SUSPENDED", "COMPLETED")).build();

    // when
    final var result =
        ProcessInstanceRequestValidator.validateResumeProcessInstanceBatchOperationFilter(filter);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail())
        .isEqualTo("The value for state is 'COMPLETED' but must be one of [SUSPENDED].");
  }

  @Test
  void shouldRejectTopLevelInvalidStateEqForCancel() {
    // given
    final var filter = searchFilter().states("COMPLETED").build();

    // when
    final var result =
        ProcessInstanceRequestValidator.validateCancelProcessInstanceBatchOperationFilter(filter);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail())
        .isEqualTo("The value for state is 'COMPLETED' but must be one of [ACTIVE, SUSPENDED].");
  }

  @Test
  void shouldRejectTopLevelInvalidStateInForCancel() {
    // given
    final var filter = searchFilter().stateOperations(Operation.in("ACTIVE", "COMPLETED")).build();

    // when
    final var result =
        ProcessInstanceRequestValidator.validateCancelProcessInstanceBatchOperationFilter(filter);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail())
        .isEqualTo("The value for state is 'COMPLETED' but must be one of [ACTIVE, SUSPENDED].");
  }

  @Test
  void shouldReportCanceledAsTerminatedForCancel() {
    // given
    final var filter = searchFilter().states("CANCELED").build();

    // when
    final var result =
        ProcessInstanceRequestValidator.validateCancelProcessInstanceBatchOperationFilter(filter);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail())
        .isEqualTo("The value for state is 'TERMINATED' but must be one of [ACTIVE, SUSPENDED].");
  }

  @Test
  void shouldReportEachDistinctInvalidStateForCancel() {
    // given
    final var filter =
        searchFilter()
            .stateOperations(Operation.in("COMPLETED", "CANCELED", "COMPLETED", "ACTIVE"))
            .build();

    // when
    final var result =
        ProcessInstanceRequestValidator.validateCancelProcessInstanceBatchOperationFilter(filter);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail())
        .contains("The value for state is 'COMPLETED' but must be one of [ACTIVE, SUSPENDED]")
        .contains("The value for state is 'TERMINATED' but must be one of [ACTIVE, SUSPENDED]");
  }

  @Test
  void shouldAcceptValidStatesForSuspend() {
    // when / then
    assertThat(
            ProcessInstanceRequestValidator.validateSuspendProcessInstanceBatchOperationFilter(
                searchFilter().states("ACTIVE").build()))
        .isEmpty();
    assertThat(
            ProcessInstanceRequestValidator.validateSuspendProcessInstanceBatchOperationFilter(
                searchFilter().build()))
        .isEmpty();
  }

  @Test
  void shouldAcceptValidStatesForResume() {
    // when / then
    assertThat(
            ProcessInstanceRequestValidator.validateResumeProcessInstanceBatchOperationFilter(
                searchFilter().states("SUSPENDED").build()))
        .isEmpty();
    assertThat(
            ProcessInstanceRequestValidator.validateResumeProcessInstanceBatchOperationFilter(
                searchFilter().build()))
        .isEmpty();
  }

  @Test
  void shouldAcceptValidStatesForCancel() {
    // when / then
    assertThat(
            ProcessInstanceRequestValidator.validateCancelProcessInstanceBatchOperationFilter(
                searchFilter().stateOperations(Operation.in("ACTIVE", "SUSPENDED")).build()))
        .isEmpty();
    assertThat(
            ProcessInstanceRequestValidator.validateCancelProcessInstanceBatchOperationFilter(
                searchFilter().states("ACTIVE").build()))
        .isEmpty();
    assertThat(
            ProcessInstanceRequestValidator.validateCancelProcessInstanceBatchOperationFilter(
                searchFilter().build()))
        .isEmpty();
  }

  @Test
  void shouldNotValidateStatesInsideOrFilters() {
    // given
    final var filter =
        searchFilter()
            .addOrOperation(searchFilter().states("COMPLETED").build())
            .addOrOperation(searchFilter().processInstanceKeys(42L).build())
            .build();

    // when / then
    assertThat(
            ProcessInstanceRequestValidator.validateSuspendProcessInstanceBatchOperationFilter(
                filter))
        .isEmpty();
    assertThat(
            ProcessInstanceRequestValidator.validateResumeProcessInstanceBatchOperationFilter(
                filter))
        .isEmpty();
    assertThat(
            ProcessInstanceRequestValidator.validateCancelProcessInstanceBatchOperationFilter(
                filter))
        .isEmpty();
  }

  static Stream<Operation<String>> nonRestrictingStateOperations() {
    return Stream.of(
        Operation.neq("COMPLETED"),
        Operation.exists(true),
        Operation.exists(false),
        Operation.like("COMP*"));
  }

  @ParameterizedTest
  @MethodSource("nonRestrictingStateOperations")
  void shouldNotValidateOtherStateOperators(final Operation<String> stateOperation) {
    // given
    final var filter = searchFilter().stateOperations(stateOperation).build();

    // when / then
    assertThat(
            ProcessInstanceRequestValidator.validateSuspendProcessInstanceBatchOperationFilter(
                filter))
        .isEmpty();
    assertThat(
            ProcessInstanceRequestValidator.validateResumeProcessInstanceBatchOperationFilter(
                filter))
        .isEmpty();
    assertThat(
            ProcessInstanceRequestValidator.validateCancelProcessInstanceBatchOperationFilter(
                filter))
        .isEmpty();
  }

  @Test
  void shouldNeverRejectParentProcessInstanceKeyFilter() {
    // given
    final var filter = searchFilter().parentProcessInstanceKeys(12345L).build();

    // when / then
    assertThat(
            ProcessInstanceRequestValidator.validateSuspendProcessInstanceBatchOperationFilter(
                filter))
        .isEmpty();
    assertThat(
            ProcessInstanceRequestValidator.validateResumeProcessInstanceBatchOperationFilter(
                filter))
        .isEmpty();
    assertThat(
            ProcessInstanceRequestValidator.validateCancelProcessInstanceBatchOperationFilter(
                filter))
        .isEmpty();
  }
}
