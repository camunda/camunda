/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DefinitionExceptionResponseDto extends ErrorResponseDto {

  private Set<DefinitionExceptionItemDto> definitions;

  protected DefinitionExceptionResponseDto() {
    this(null, null, null, Collections.emptySet());
  }

  public DefinitionExceptionResponseDto(
      final String errorCode,
      final String errorMessage,
      final String detailedErrorMessage,
      final Set<DefinitionExceptionItemDto> definitions) {
    super(errorCode, errorMessage, detailedErrorMessage);
    this.definitions = definitions;
  }

  public Set<DefinitionExceptionItemDto> getDefinitions() {
    return definitions;
  }

  public void setDefinitions(final Set<DefinitionExceptionItemDto> definitions) {
    this.definitions = definitions;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DefinitionExceptionResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $definitions = getDefinitions();
    result = result * PRIME + ($definitions == null ? 43 : $definitions.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DefinitionExceptionResponseDto)) {
      return false;
    }
    final DefinitionExceptionResponseDto other = (DefinitionExceptionResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$definitions = getDefinitions();
    final Object other$definitions = other.getDefinitions();
    if (this$definitions == null
        ? other$definitions != null
        : !this$definitions.equals(other$definitions)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DefinitionExceptionResponseDto(definitions=" + getDefinitions() + ")";
  }
}
