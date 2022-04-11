/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.IdentityType;

import java.util.HashMap;
import java.util.Map;

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
