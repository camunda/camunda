/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.status;

import lombok.Getter;

@Getter
public class EngineStatusDto {

  protected Boolean isConnected;

  protected Boolean isImporting;

  public void setIsConnected(final boolean isConnected) {
    this.isConnected = isConnected;
  }

  public void setIsImporting(final boolean isImporting) {
    this.isImporting = isImporting;
  }
}
