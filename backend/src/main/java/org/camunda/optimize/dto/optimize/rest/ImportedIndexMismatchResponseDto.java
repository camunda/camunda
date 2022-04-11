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
public class ImportedIndexMismatchResponseDto extends ErrorResponseDto {
  private Set<ImportIndexMismatchDto> mismatchingIndices;

  protected ImportedIndexMismatchResponseDto() {
    this(null, null, null, Collections.emptySet());
  }

  public ImportedIndexMismatchResponseDto(final String errorCode, final String errorMessage,
                                          final String detailedErrorMessage,
                                          final Set<ImportIndexMismatchDto> mismatchingIndices) {
    super(errorCode, errorMessage, detailedErrorMessage);
    this.mismatchingIndices = mismatchingIndices;
  }
}
