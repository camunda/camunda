/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

public class IncludeVariable {

  @Schema(description = "The name of the variable.")
  private String name;

  @Schema(description = "Always return the full value of the variable?", defaultValue = "false")
  private boolean alwaysReturnFullValue = false;

  public String getName() {
    return name;
  }

  public IncludeVariable setName(String name) {
    this.name = name;
    return this;
  }

  public boolean isAlwaysReturnFullValue() {
    return alwaysReturnFullValue;
  }

  public IncludeVariable setAlwaysReturnFullValue(boolean alwaysReturnFullValue) {
    this.alwaysReturnFullValue = alwaysReturnFullValue;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IncludeVariable that = (IncludeVariable) o;
    return alwaysReturnFullValue == that.alwaysReturnFullValue && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(alwaysReturnFullValue, name);
  }
}
