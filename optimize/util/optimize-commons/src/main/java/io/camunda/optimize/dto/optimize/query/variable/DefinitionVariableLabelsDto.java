/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class DefinitionVariableLabelsDto implements OptimizeDto {

  @NotBlank private String definitionKey;

  @Valid private List<LabelDto> labels;

  public DefinitionVariableLabelsDto(
      @NotBlank final String definitionKey, @Valid final List<LabelDto> labels) {
    this.definitionKey = definitionKey;
    this.labels = labels;
  }

  public DefinitionVariableLabelsDto() {}

  public @NotBlank String getDefinitionKey() {
    return definitionKey;
  }

  public void setDefinitionKey(@NotBlank final String definitionKey) {
    this.definitionKey = definitionKey;
  }

  public @Valid List<LabelDto> getLabels() {
    return labels;
  }

  public void setLabels(@Valid final List<LabelDto> labels) {
    this.labels = labels;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DefinitionVariableLabelsDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $definitionKey = getDefinitionKey();
    result = result * PRIME + ($definitionKey == null ? 43 : $definitionKey.hashCode());
    final Object $labels = getLabels();
    result = result * PRIME + ($labels == null ? 43 : $labels.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DefinitionVariableLabelsDto)) {
      return false;
    }
    final DefinitionVariableLabelsDto other = (DefinitionVariableLabelsDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$definitionKey = getDefinitionKey();
    final Object other$definitionKey = other.getDefinitionKey();
    if (this$definitionKey == null
        ? other$definitionKey != null
        : !this$definitionKey.equals(other$definitionKey)) {
      return false;
    }
    final Object this$labels = getLabels();
    final Object other$labels = other.getLabels();
    if (this$labels == null ? other$labels != null : !this$labels.equals(other$labels)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DefinitionVariableLabelsDto(definitionKey="
        + getDefinitionKey()
        + ", labels="
        + getLabels()
        + ")";
  }

  public static final class Fields {

    public static final String definitionKey = "definitionKey";
    public static final String labels = "labels";
  }
}
