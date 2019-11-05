/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@FieldNameConstants(asEnum = true)
public abstract class DefinitionOptimizeDto implements Serializable, OptimizeDto {
  private String id;
  private String key;
  private String version;
  private String versionTag;
  private String name;
  private String engine;
  private String tenantId;

  public DefinitionOptimizeDto(final String id, final String engine) {
    this.id = id;
    this.engine = engine;
  }
}
