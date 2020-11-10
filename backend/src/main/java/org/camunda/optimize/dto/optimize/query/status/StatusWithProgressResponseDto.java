/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.status;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class StatusWithProgressResponseDto {
  protected ConnectionStatusDto connectionStatus;
  protected Map<String, Boolean> isImporting;

  public void setIsImporting(final Map<String, Boolean> isImporting) {
    this.isImporting = new ConcurrentHashMap<>(isImporting);
  }
}
