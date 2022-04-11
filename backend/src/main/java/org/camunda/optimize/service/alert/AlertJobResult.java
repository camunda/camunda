/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;


public class AlertJobResult {

  private boolean statusChanged;
  private AlertDefinitionDto alert;

  public AlertJobResult(AlertDefinitionDto alert) {
    this.alert = alert;
  }

  public AlertDefinitionDto getAlert() {
    return alert;
  }

  public void setAlert(AlertDefinitionDto alert) {
    this.alert = alert;
  }

  public boolean isStatusChanged() {
    return statusChanged;
  }

  public void setStatusChanged(boolean statusChanged) {
    this.statusChanged = statusChanged;
  }
}
