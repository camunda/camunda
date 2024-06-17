/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.entity;

import io.camunda.optimize.dto.optimize.IdentityType;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class EntityData {

  private Map<EntityType, Long> subEntityCounts = new HashMap<>();
  private Map<IdentityType, Long> roleCounts = new HashMap<>();

  public EntityData(final Map<EntityType, Long> subEntityCounts) {
    this.subEntityCounts = subEntityCounts;
  }
}
