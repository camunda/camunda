/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.operation;

import io.camunda.operate.webapp.rest.dto.RequestValidator;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

@Schema(description = "Migration plan for process instance migration operation")
public class MigrationPlanDto implements RequestValidator {

  private String targetProcessDefinitionKey;
  private List<MappingInstruction> mappingInstructions;

  public String getTargetProcessDefinitionKey() {
    return targetProcessDefinitionKey;
  }

  public MigrationPlanDto setTargetProcessDefinitionKey(String targetProcessDefinitionKey) {
    this.targetProcessDefinitionKey = targetProcessDefinitionKey;
    return this;
  }

  public List<MappingInstruction> getMappingInstructions() {
    return mappingInstructions;
  }

  public MigrationPlanDto setMappingInstructions(List<MappingInstruction> mappingInstructions) {
    this.mappingInstructions = mappingInstructions;
    return this;
  }

  @Override
  public void validate() {
    Long processDefinitionKey = null;
    try {
      final long key = Long.parseLong(targetProcessDefinitionKey);
      if (key > 0) {
        processDefinitionKey = key;
      }
    } catch (Exception ex) {
      // noop
    }
    if (processDefinitionKey == null) {
      throw new InvalidRequestException("Target process definition key must be a positive number.");
    }
    if (mappingInstructions == null || mappingInstructions.isEmpty()) {
      throw new InvalidRequestException("Mapping instructions are missing.");
    }
    final boolean hasNullMappings = mappingInstructions.stream().anyMatch(Objects::isNull);
    if (hasNullMappings) {
      throw new InvalidRequestException("Mapping instructions cannot be null.");
    }
    final boolean hasEmptyElements =
        mappingInstructions.stream()
            .anyMatch(
                x ->
                    StringUtils.isEmpty(x.getSourceElementId())
                        || StringUtils.isEmpty(x.getTargetElementId()));
    if (hasEmptyElements) {
      throw new InvalidRequestException("Mapping source and target elements cannot be empty.");
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(targetProcessDefinitionKey, mappingInstructions);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MigrationPlanDto that = (MigrationPlanDto) o;
    return Objects.equals(targetProcessDefinitionKey, that.targetProcessDefinitionKey)
        && Objects.equals(mappingInstructions, that.mappingInstructions);
  }

  /** MappingInstruction */
  public static class MappingInstruction {

    private String sourceElementId;
    private String targetElementId;

    public String getSourceElementId() {
      return sourceElementId;
    }

    public MappingInstruction setSourceElementId(String sourceElementId) {
      this.sourceElementId = sourceElementId;
      return this;
    }

    public String getTargetElementId() {
      return targetElementId;
    }

    public MappingInstruction setTargetElementId(String targetElementId) {
      this.targetElementId = targetElementId;
      return this;
    }

    @Override
    public int hashCode() {
      return Objects.hash(sourceElementId, targetElementId);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final MappingInstruction that = (MappingInstruction) o;
      return Objects.equals(sourceElementId, that.sourceElementId)
          && Objects.equals(targetElementId, that.targetElementId);
    }
  }
}
