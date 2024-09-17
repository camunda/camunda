/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TenantDto implements OptimizeDto {

  @EqualsAndHashCode.Include private String id;
  @EqualsAndHashCode.Include private String name;
  private String engine;

  public TenantDto(String id, String name, String engine) {
    this.id = id;
    this.name = name;
    this.engine = engine;
  }

  protected TenantDto() {}

  public enum Fields {
    id,
    name,
    engine
  }
}
