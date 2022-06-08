/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.processoverview;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;

import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
@AllArgsConstructor
public class ProcessDigestDto extends ProcessDigestResponseDto {

  /**
   * Needed to inherit field name constants from {@link ProcessDigestResponseDto}
   */
  public static class Fields extends ProcessDigestResponseDto.Fields {
  }

  private Map<String, Double> kpiReportResults;

  public ProcessDigestDto() {
    super();
    this.kpiReportResults = new HashMap<>();
  }

  public ProcessDigestDto(final AlertInterval checkInterval, final boolean enabled,
                          final Map<String, Double> kpiReportResults) {
    super(checkInterval, enabled);
    this.kpiReportResults = kpiReportResults;
  }

}
