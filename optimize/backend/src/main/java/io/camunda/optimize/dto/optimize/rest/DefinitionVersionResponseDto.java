/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class DefinitionVersionResponseDto {

  private String version;
  private String versionTag;

  public DefinitionVersionResponseDto(String version, String versionTag) {
    this.version = version;
    this.versionTag = versionTag;
  }

  protected DefinitionVersionResponseDto() {}
}
