/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.status;

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
