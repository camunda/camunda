/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_AHSP_VALUE_INCONSISTENT;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_ALL_REQUIRED_FIELD;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_INVALID_AHSP_REQUIRED_FIELD;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_INVALID_AHSP_VALUE;

import io.camunda.zeebe.gateway.protocol.rest.MigrateProcessInstanceMappingInstruction;
import java.util.ArrayList;
import java.util.List;

public class MigrationInstructionValidator {

  public static final String AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX = "#innerInstance";

  public static List<String> validate(
      final List<MigrateProcessInstanceMappingInstruction> mappingInstructions) {
    final List<String> violations = new ArrayList<>();

    // Check that mapping instructions are provided
    if (mappingInstructions == null || mappingInstructions.isEmpty()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("mappingInstructions"));
    } else {
      // Check if any mapping instruction has missing fields in a single pass
      if (hasMissingFields(mappingInstructions)) {
        violations.add(
            ERROR_MESSAGE_ALL_REQUIRED_FIELD.formatted(
                List.of("sourceElementId", "targetElementId")));
      } else {
        // Verify ad hoc sub process related constraints only if all fields are present
        validateAdHocSubProcessConstraints(mappingInstructions, violations);
      }
    }
    return violations;
  }

  /**
   * Validates ad hoc subprocess constraints for mapping instructions.
   *
   * <p>This validation should never trigger when requests originate from the UI. However,
   * experienced API users might create requests with mapping instructions containing element IDs
   * with the {@code #innerInstance} suffix.
   *
   * <p>When this occurs, the following constraints must be validated:
   *
   * <ol>
   *   <li>Both source and target must consistently use or not use the {@code #innerInstance} suffix
   *   <li>If using {@code adHocSubProcessId#innerInstance}, ensure the base element ID {@code
   *       adHocSubProcessId} also exists in the mapping instructions
   * </ol>
   *
   * @param mappingInstructions the list of mapping instructions to validate
   * @param violations the list to collect validation violations
   */
  private static void validateAdHocSubProcessConstraints(
      final List<MigrateProcessInstanceMappingInstruction> mappingInstructions,
      final List<String> violations) {

    mappingInstructions.forEach(
        instruction -> {
          final String sourceElementId = instruction.getSourceElementId();
          final String targetElementId = instruction.getTargetElementId();

          final boolean sourceHasInnerInstance = hasInnerInstanceSuffix(sourceElementId);
          final boolean targetHasInnerInstance = hasInnerInstanceSuffix(targetElementId);

          // Constraint 1: Verify consistent usage of #innerInstance suffix
          validateConsistentSuffixUsage(sourceHasInnerInstance, targetHasInnerInstance, violations);

          // Constraint 2: Validate source element with #innerInstance suffix
          validateElementWithInnerInstance(
              sourceElementId, sourceHasInnerInstance, mappingInstructions, violations, true);

          // Constraint 3: Validate target element with #innerInstance suffix
          validateElementWithInnerInstance(
              targetElementId, targetHasInnerInstance, mappingInstructions, violations, false);
        });
  }

  /**
   * Validates that both source and target element IDs consistently use or don't use the
   * #innerInstance suffix.
   */
  private static void validateConsistentSuffixUsage(
      final boolean sourceHasInnerInstance,
      final boolean targetHasInnerInstance,
      final List<String> violations) {
    if (sourceHasInnerInstance != targetHasInnerInstance) {
      violations.add(
          ERROR_MESSAGE_AHSP_VALUE_INCONSISTENT.formatted("sourceElementId", "targetElementId"));
    }
  }

  /**
   * Validates an element ID that uses the #innerInstance suffix by ensuring: 1. The base element ID
   * (part before #innerInstance) is not blank 2. A mapping instruction exists for the base element
   * ID
   *
   * @param elementId the element ID to validate
   * @param hasInnerInstance whether the element ID has the #innerInstance suffix
   * @param mappingInstructions all mapping instructions to search for base element
   * @param violations the list to collect validation violations
   * @param isSource true if validating sourceElementId, false for targetElementId
   */
  private static void validateElementWithInnerInstance(
      final String elementId,
      final boolean hasInnerInstance,
      final List<MigrateProcessInstanceMappingInstruction> mappingInstructions,
      final List<String> violations,
      final boolean isSource) {
    if (!hasInnerInstance) {
      return;
    }

    final String fieldName = isSource ? "sourceElementId" : "targetElementId";
    final String baseElementId = extractBaseElementId(elementId);

    if (baseElementId.isBlank()) {
      violations.add(ERROR_MESSAGE_INVALID_AHSP_VALUE.formatted(fieldName, elementId));
      return;
    }

    if (!hasMappingForAdHocSubProcessElement(mappingInstructions, baseElementId, isSource)) {
      violations.add(
          ERROR_MESSAGE_INVALID_AHSP_REQUIRED_FIELD.formatted(fieldName, elementId, baseElementId));
    }
  }

  /** Checks if an element ID ends with the #innerInstance suffix. */
  private static boolean hasInnerInstanceSuffix(final String elementId) {
    return elementId.endsWith(AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX);
  }

  private static boolean hasMissingFields(
      final List<MigrateProcessInstanceMappingInstruction> mappingInstructions) {
    return mappingInstructions.stream()
        .anyMatch(
            instruction ->
                instruction.getSourceElementId() == null
                    || instruction.getSourceElementId().isEmpty()
                    || instruction.getTargetElementId() == null
                    || instruction.getTargetElementId().isEmpty());
  }

  private static String extractBaseElementId(final String elementId) {
    final int suffixIndex = elementId.indexOf(AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX);
    return suffixIndex >= 0 ? elementId.substring(0, suffixIndex) : elementId;
  }

  private static boolean hasMappingForAdHocSubProcessElement(
      final List<MigrateProcessInstanceMappingInstruction> instructions,
      final String baseElementId,
      final boolean checkSource) {
    return instructions.stream()
        .anyMatch(
            instruction -> {
              final String elementId =
                  checkSource ? instruction.getSourceElementId() : instruction.getTargetElementId();
              return elementId != null && elementId.equals(baseElementId);
            });
  }
}
