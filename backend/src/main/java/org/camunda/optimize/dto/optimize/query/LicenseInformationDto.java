/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;
import java.time.OffsetDateTime;

public class LicenseInformationDto implements OptimizeDto, Serializable {

  private String customerId;
  private OffsetDateTime validUntil;
  private boolean isUnlimited;

  public String getCustomerId() {
    return customerId;
  }

  public void setCustomerId(String customerId) {
    this.customerId = customerId;
  }

  public OffsetDateTime getValidUntil() {
    return validUntil;
  }

  public void setValidUntil(OffsetDateTime validUntil) {
    this.validUntil = validUntil;
  }

  public boolean isUnlimited() {
    return isUnlimited;
  }

  public void setUnlimited(boolean unlimited) {
    isUnlimited = unlimited;
  }
}
