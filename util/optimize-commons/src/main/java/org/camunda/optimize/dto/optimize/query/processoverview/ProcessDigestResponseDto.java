/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.processoverview;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.alert.AlertIntervalUnit;

@Data
@FieldNameConstants
@AllArgsConstructor
public class ProcessDigestResponseDto {

  // needed to allow inheritance of field name constants
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class Fields {
  }

  protected AlertInterval checkInterval;
  protected boolean enabled;

  public ProcessDigestResponseDto() {
    this.checkInterval = new AlertInterval(1, AlertIntervalUnit.WEEKS);
    this.enabled = false;
  }

}
