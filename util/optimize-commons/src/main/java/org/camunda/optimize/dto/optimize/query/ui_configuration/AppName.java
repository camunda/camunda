/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.ui_configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public enum AppName {
  @JsonProperty("console")
  CONSOLE,
  @JsonProperty("operate")
  OPERATE,
  @JsonProperty("optimize")
  OPTIMIZE,
  @JsonProperty("modeler")
  MODELER,
  @JsonProperty("tasklist")
  TASKLIST,
  @JsonProperty("accounts")
  ACCOUNTS,
  @JsonProperty("zeebe")
  ZEEBE;

  @Override
  public String toString() {
    return super.toString().toLowerCase();
  }
}
