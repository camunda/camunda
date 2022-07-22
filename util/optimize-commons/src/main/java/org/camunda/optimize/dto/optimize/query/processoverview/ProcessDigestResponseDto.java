/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.processoverview;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;

@Data
@FieldNameConstants
@AllArgsConstructor
@NoArgsConstructor
public class ProcessDigestResponseDto implements OptimizeDto {

  // needed to allow inheritance of field name constants
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class Fields {
  }

  @JsonProperty("enabled")
  protected boolean enabled;
}
