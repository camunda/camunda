/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

public class SharingEnabledDto {
  private boolean enabled;

  public SharingEnabledDto(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isEnabled() {

    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
