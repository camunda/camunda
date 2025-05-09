/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;

public class RoleEntity extends AbstractExporterEntity<RoleEntity> {

  private Long key;
  private String roleId;
  private String name;
  private String description;
  private EntityJoinRelation<String> join;

  public Long getKey() {
    return key;
  }

  public RoleEntity setKey(final Long key) {
    this.key = key;
    return this;
  }

  public String getName() {
    return name;
  }

  public RoleEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getRoleId() {
    return roleId;
  }

  public RoleEntity setRoleId(final String roleId) {
    this.roleId = roleId;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public RoleEntity setDescription(final String description) {
    this.description = description;
    return this;
  }

  public EntityJoinRelation<String> getJoin() {
    return join;
  }

  public RoleEntity setJoin(final EntityJoinRelation<String> join) {
    this.join = join;
    return this;
  }

  public static String getChildKey(final String roleId, final String memberId) {
    return "%s-%s".formatted(roleId, memberId);
  }
}
