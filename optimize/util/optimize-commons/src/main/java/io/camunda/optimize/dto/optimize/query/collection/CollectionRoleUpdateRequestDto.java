/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import io.camunda.optimize.dto.optimize.RoleType;
import lombok.Data;

@Data
public class CollectionRoleUpdateRequestDto {

  private RoleType role;

  public CollectionRoleUpdateRequestDto(RoleType role) {
    this.role = role;
  }

  protected CollectionRoleUpdateRequestDto() {}

  public enum Fields {
    role
  }
}
