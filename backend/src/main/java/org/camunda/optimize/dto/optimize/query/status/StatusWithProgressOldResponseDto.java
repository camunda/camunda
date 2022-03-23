/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.status;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class StatusWithProgressOldResponseDto {
  protected ConnectionOldStatusDto connectionStatus;
  protected Map<String, Boolean> isImporting;

  public void setIsImporting(final Map<String, Boolean> isImporting) {
    this.isImporting = new ConcurrentHashMap<>(isImporting);

  }
}
