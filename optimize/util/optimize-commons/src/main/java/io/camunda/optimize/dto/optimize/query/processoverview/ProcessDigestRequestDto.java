/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.processoverview;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class ProcessDigestRequestDto {

  @JsonProperty("enabled")
  private boolean enabled;

  public ProcessDigestRequestDto(final boolean enabled) {
    this.enabled = enabled;
  }

  public ProcessDigestRequestDto() {}

  public boolean isEnabled() {
    return enabled;
  }

  @JsonProperty("enabled")
  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessDigestRequestDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessDigestRequestDto that = (ProcessDigestRequestDto) o;
    return enabled == that.enabled;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(enabled);
  }

  @Override
  public String toString() {
    return "ProcessDigestRequestDto(enabled=" + isEnabled() + ")";
  }
}
