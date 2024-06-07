/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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
