/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.ui_configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;

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
    return super.toString().toLowerCase(Locale.ENGLISH);
  }
}
