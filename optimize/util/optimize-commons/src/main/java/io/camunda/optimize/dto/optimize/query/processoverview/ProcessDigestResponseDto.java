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
import lombok.Data;

@Data
public class ProcessDigestResponseDto implements OptimizeDto {

  @JsonProperty("enabled")
  protected boolean enabled;

  public ProcessDigestResponseDto(boolean enabled) {
    this.enabled = enabled;
  }

  public ProcessDigestResponseDto() {}

  // needed to allow inheritance of field name constants
  public static class Fields {

    public static final String enabled = "enabled";

    protected Fields() {}
  }
}
