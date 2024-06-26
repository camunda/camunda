/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.rolemanagement.model;

import io.camunda.identity.permissions.PermissionEnum;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role {

  @Id
  @NotBlank(message = "role.notValid")
  @Column(name = "authority")
  private String name;

  private String description;

  @NotNull(message = "role.notValid")
  @ElementCollection(targetClass = PermissionEnum.class, fetch = FetchType.EAGER)
  @JoinTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_authority"))
  @Column(name = "permission", nullable = false)
  @Enumerated(EnumType.STRING)
  private Set<PermissionEnum> permissions = new HashSet<>();

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public Set<PermissionEnum> getPermissions() {
    return permissions;
  }

  public void setPermissions(final Set<PermissionEnum> permissions) {
    this.permissions = permissions;
  }
}
