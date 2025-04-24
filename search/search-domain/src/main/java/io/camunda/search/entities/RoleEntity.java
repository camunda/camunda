/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RoleEntity(Long roleKey, String roleId, String name, Set<String> assignedMemberIds)
    implements Serializable {
  public RoleEntity(final Long roleKey, final String roleId, final String name) {
    this(roleKey, roleId, name, Set.of());
  }
}
