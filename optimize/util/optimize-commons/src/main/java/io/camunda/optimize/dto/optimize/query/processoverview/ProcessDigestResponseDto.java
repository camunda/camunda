/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.processoverview;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.dto.optimize.OptimizeDto;

public class ProcessDigestResponseDto implements OptimizeDto {

  @JsonProperty("enabled")
  protected boolean enabled;

  public ProcessDigestResponseDto(final boolean enabled) {
    this.enabled = enabled;
  }

  public ProcessDigestResponseDto() {}

  public boolean isEnabled() {
    return enabled;
  }

  @JsonProperty("enabled")
  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessDigestResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isEnabled() ? 79 : 97);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessDigestResponseDto)) {
      return false;
    }
    final ProcessDigestResponseDto other = (ProcessDigestResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isEnabled() != other.isEnabled()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ProcessDigestResponseDto(enabled=" + isEnabled() + ")";
  }

  // needed to allow inheritance of field name constants
  public static class Fields {

    public static final String enabled = "enabled";

    protected Fields() {}
  }
}
