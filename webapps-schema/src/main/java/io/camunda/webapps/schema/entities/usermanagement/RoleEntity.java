/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import java.util.Set;

public class RoleEntity extends AbstractExporterEntity<RoleEntity> {

  private Long roleKey;
  private String name;
  private Set<Long> assignedMemberKeys;

  public Long getRoleKey() {
    return roleKey;
  }

  public RoleEntity setRoleKey(final Long roleKey) {
    this.roleKey = roleKey;
    return this;
  }

  public String getName() {
    return name;
  }

  public RoleEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public Set<Long> getAssignedMemberKeys() {
    return assignedMemberKeys;
  }

  public RoleEntity setAssignedMemberKeys(final Set<Long> entityKey) {
    assignedMemberKeys = entityKey;
    return this;
  }
}
