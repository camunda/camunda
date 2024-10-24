/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.alert;

import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;

public class AlertJobResult {

  private boolean statusChanged;

  private AlertDefinitionDto alert;

  public AlertJobResult(final AlertDefinitionDto alert) {
    this.alert = alert;
  }

  public AlertDefinitionDto getAlert() {
    return alert;
  }

  public void setAlert(final AlertDefinitionDto alert) {
    this.alert = alert;
  }

  public boolean isStatusChanged() {
    return statusChanged;
  }

  public void setStatusChanged(final boolean statusChanged) {
    this.statusChanged = statusChanged;
  }
}
