/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import java.util.HashSet;
import java.util.Set;

public class RoleMemberEntity extends AbstractExporterEntity<RoleMemberEntity> {
  private Long roleKey;
  private final Set<Long> addedMemberKeys = new HashSet<>();
  private final Set<Long> removedMemberKeys = new HashSet<>();

  public Long getRoleKey() {
    return roleKey;
  }

  public RoleMemberEntity setRoleKey(final long roleKey) {
    this.roleKey = roleKey;
    return this;
  }

  public RoleMemberEntity addMemberKey(final long memberKey) {
    addedMemberKeys.add(memberKey);
    removedMemberKeys.remove(memberKey);
    return this;
  }

  public RoleMemberEntity removeMemberKey(final long memberKey) {
    removedMemberKeys.add(memberKey);
    addedMemberKeys.remove(memberKey);
    return this;
  }

  public Set<Long> getAddedMemberKeys() {
    return addedMemberKeys;
  }

  public Set<Long> getRemovedMemberKeys() {
    return removedMemberKeys;
  }
}
