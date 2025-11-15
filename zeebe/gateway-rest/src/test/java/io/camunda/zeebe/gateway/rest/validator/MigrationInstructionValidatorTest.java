/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.MigrationInstructionValidator.AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.protocol.rest.MigrateProcessInstanceMappingInstruction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MigrationInstructionValidator Tests")
class MigrationInstructionValidatorTest {

  @Test
  @DisplayName("Should accept valid mapping instructions without #innerInstance")
  void shouldAcceptValidMappingInstructionsWithoutInnerInstance() {
    // given
    final var mappingInstructions =
        List.of(
            createMappingInstruction("task-1", "task-2"),
            createMappingInstruction("subprocess-1", "subprocess-2"));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Should accept valid mapping instructions with #innerInstance and base element")
  void shouldAcceptValidMappingInstructionsWithInnerInstance() {
    // given
    final var mappingInstructions =
        List.of(
            createMappingInstruction("adhoc-1", "adhoc-2"),
            createMappingInstruction("adhoc-1#innerInstance", "adhoc-2#innerInstance"));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Should accept multiple ad hoc subprocess mappings with #innerInstance")
  void shouldAcceptMultipleAdHocSubProcessMappings() {
    // given
    final var mappingInstructions =
        List.of(
            createMappingInstruction("adhoc-1", "adhoc-2"),
            createMappingInstruction("adhoc-1#innerInstance", "adhoc-2#innerInstance"),
            createMappingInstruction("adhoc-3", "adhoc-4"),
            createMappingInstruction("adhoc-3#innerInstance", "adhoc-4#innerInstance"));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Should reject when #innerInstance in source but not in target")
  void shouldRejectWhenInnerInstanceInSourceButNotInTarget() {
    // given
    final var mappingInstructions =
        List.of(
            createMappingInstruction("adhoc-1", "adhoc-2"),
            createMappingInstruction("adhoc-1#innerInstance", "adhoc-2"));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst())
        .contains("sourceElementId")
        .contains("targetElementId")
        .contains("must consistently use or not use")
        .contains(AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX);
  }

  @Test
  @DisplayName("Should reject when #innerInstance in target but not in source")
  void shouldRejectWhenInnerInstanceInTargetButNotInSource() {
    // given
    final var mappingInstructions =
        List.of(
            createMappingInstruction("adhoc-1", "adhoc-2"),
            createMappingInstruction("adhoc-1", "adhoc-2#innerInstance"));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst())
        .contains("sourceElementId")
        .contains("targetElementId")
        .contains("must consistently use or not use")
        .contains(AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX);
  }

  @Test
  @DisplayName("Should reject when #innerInstance used without base element mapping in source")
  void shouldRejectWhenInnerInstanceUsedWithoutBaseElementMappingInSource() {
    // given
    final var mappingInstructions =
        List.of(
            createMappingInstruction("adhoc-3", "adhoc-2"),
            createMappingInstruction("adhoc-1#innerInstance", "adhoc-2#innerInstance"));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst())
        .contains("sourceElementId")
        .contains(AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX)
        .contains("ad-hoc sub-process element id is also required in mappingInstructions");
  }

  @Test
  @DisplayName("Should reject when #innerInstance used without base element mapping in target")
  void shouldRejectWhenInnerInstanceUsedWithoutBaseElementMappingInTarget() {
    // given
    final var mappingInstructions =
        List.of(
            createMappingInstruction("adhoc-1", "adhoc-4"),
            createMappingInstruction("adhoc-1#innerInstance", "adhoc-2#innerInstance"));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst())
        .contains("targetElementId")
        .contains(AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX)
        .contains("ad-hoc sub-process element id is also required in mappingInstructions");
  }

  @Test
  @DisplayName("Should reject empty mapping instructions")
  void shouldRejectEmptyMappingInstructions() {
    // given
    final List<MigrateProcessInstanceMappingInstruction> mappingInstructions = new ArrayList<>();

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst()).contains("No mappingInstructions provided");
  }

  @Test
  @DisplayName("Should reject null mapping instructions")
  void shouldRejectNullMappingInstructions() {
    // when
    final List<String> violations = MigrationInstructionValidator.validate(null);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst()).contains("No mappingInstructions provided");
  }

  @Test
  @DisplayName("Should reject when sourceElementId is null")
  void shouldRejectWhenSourceElementIdIsNull() {
    // given
    final var mappingInstructions = List.of(createMappingInstruction(null, "target-1"));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst())
        .contains("sourceElementId")
        .contains("targetElementId")
        .contains("required");
  }

  @Test
  @DisplayName("Should reject when targetElementId is null")
  void shouldRejectWhenTargetElementIdIsNull() {
    // given
    final var mappingInstructions = List.of(createMappingInstruction("source-1", null));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst())
        .contains("sourceElementId")
        .contains("targetElementId")
        .contains("required");
  }

  @Test
  @DisplayName("Should reject when both elementIds are null")
  void shouldRejectWhenBothElementIdsAreNull() {
    // given
    final var mappingInstructions = List.of(createMappingInstruction(null, null));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst())
        .contains("sourceElementId")
        .contains("targetElementId")
        .contains("required");
  }

  @Test
  @DisplayName("Should reject when sourceElementId is empty")
  void shouldRejectWhenSourceElementIdIsEmpty() {
    // given
    final var mappingInstructions = List.of(createMappingInstruction("", "target-1"));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst())
        .contains("sourceElementId")
        .contains("targetElementId")
        .contains("required");
  }

  @Test
  @DisplayName("Should reject when targetElementId is empty")
  void shouldRejectWhenTargetElementIdIsEmpty() {
    // given
    final var mappingInstructions = List.of(createMappingInstruction("source-1", ""));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst())
        .contains("sourceElementId")
        .contains("targetElementId")
        .contains("required");
  }

  @Test
  @DisplayName("Should reject when both elementIds are empty")
  void shouldRejectWhenBothElementIdIsEmpty() {
    // given
    final var mappingInstructions = List.of(createMappingInstruction("", ""));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst())
        .contains("sourceElementId")
        .contains("targetElementId")
        .contains("required");
  }

  @Test
  @DisplayName("Should report error only once when multiple instructions have missing fields")
  void shouldReportErrorOnlyOnceWhenMultipleInstructionsHaveMissingFields() {
    // given - multiple instructions with missing fields
    final var mappingInstructions =
        List.of(
            createMappingInstruction(null, "target-1"),
            createMappingInstruction("source-2", null),
            createMappingInstruction("", "target-3"));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then - should only get ONE error message, not three
    assertThat(violations).hasSize(1);
    assertThat(violations.getFirst())
        .contains("sourceElementId")
        .contains("targetElementId")
        .contains("required");
  }

  @Test
  @DisplayName("Should handle mixed valid and invalid instructions")
  void shouldHandleMixedValidAndInvalidInstructions() {
    // given
    final var mappingInstructions =
        List.of(
            createMappingInstruction("task-1", "task-2"),
            createMappingInstruction("adhoc-1#innerInstance", "adhoc-2"),
            createMappingInstruction("adhoc-3", "adhoc-4"),
            createMappingInstruction("adhoc-3#innerInstance", "adhoc-4#innerInstance"));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).hasSize(2);
    // Check for inconsistent suffix violation
    assertThat(violations)
        .anySatisfy(v -> assertThat(v).contains("must consistently use or not use"));
    // Check for missing base element violations
    assertThat(violations)
        .anySatisfy(
            v ->
                assertThat(v)
                    .contains(
                        "ad-hoc sub-process element id is also required in mappingInstructions"));
  }

  @Test
  @DisplayName("Should validate complex scenario with multiple ad hoc subprocesses")
  void shouldValidateComplexScenarioWithMultipleAdHocSubprocesses() {
    // given
    final var mappingInstructions =
        List.of(
            // Regular task mapping
            createMappingInstruction("task-1", "task-2"),
            // First ad hoc subprocess with base and inner instance
            createMappingInstruction("adhoc-1", "adhoc-2"),
            createMappingInstruction("adhoc-1#innerInstance", "adhoc-2#innerInstance"),
            // Second ad hoc subprocess with base and inner instance
            createMappingInstruction("adhoc-3", "adhoc-4"),
            createMappingInstruction("adhoc-3#innerInstance", "adhoc-4#innerInstance"),
            // Another regular task
            createMappingInstruction("task-3", "task-4"));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Should reject when only inner instance is provided")
  void shouldRejectWhenOnlyInnerInstanceIsProvided() {
    // given
    final var mappingInstructions =
        List.of(createMappingInstruction("#innerInstance", "#innerInstance"));

    // when
    final List<String> violations = MigrationInstructionValidator.validate(mappingInstructions);

    // then
    assertThat(violations).hasSize(2);
    assertThat(violations)
        .anySatisfy(
            v ->
                assertThat(v)
                    .contains(
                        "A format like <adHocSubProcessId>"
                            + AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX
                            + " is expected"));
  }

  private MigrateProcessInstanceMappingInstruction createMappingInstruction(
      final String sourceElementId, final String targetElementId) {
    final var instruction = new MigrateProcessInstanceMappingInstruction();
    instruction.setSourceElementId(sourceElementId);
    instruction.setTargetElementId(targetElementId);
    return instruction;
  }
}
