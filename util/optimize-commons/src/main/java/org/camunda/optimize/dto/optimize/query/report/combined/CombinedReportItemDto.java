/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.combined;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import static org.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_CONFIGURATION_COLOR;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@FieldNameConstants
public class CombinedReportItemDto {

  private String id;
  @Builder.Default
  private String color = DEFAULT_CONFIGURATION_COLOR;

  public CombinedReportItemDto(String id) {
    this.id = id;
  }
}
