/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.processoverview;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
@AllArgsConstructor
@NoArgsConstructor
public class ProcessDigestDto extends ProcessDigestResponseDto {

  /** Needed to inherit field name constants from {@link ProcessDigestResponseDto} */
  public static class Fields extends ProcessDigestResponseDto.Fields {}

  // This is the baseline results, or in other words the results that were included in the
  // previously sent digest
  private Map<String, String> kpiReportResults;

  public ProcessDigestDto(final Boolean enabled, final Map<String, String> kpiReportResults) {
    super(enabled);
    this.kpiReportResults = kpiReportResults;
  }
}
