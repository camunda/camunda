/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DefinitionExceptionResponseDto extends ErrorResponseDto {
  private Set<DefinitionExceptionItemDto> definitions;

  protected DefinitionExceptionResponseDto() {
    this(null, null, null, Collections.emptySet());
  }

  public DefinitionExceptionResponseDto(final String errorCode, final String errorMessage,
                                        final String detailedErrorMessage,
                                        final Set<DefinitionExceptionItemDto> definitions) {
    super(errorCode, errorMessage, detailedErrorMessage);
    this.definitions = definitions;
  }
}
