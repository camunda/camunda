/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.engine;

import lombok.Data;

import java.util.Optional;

@Data
public class TenantEngineDto implements TenantSpecificEngineDto {
  private String id;
  private String name;

  @Override
  public Optional<String> getTenantId() {
    return Optional.of(id);
  }
}
