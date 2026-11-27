/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.dto.optimize.access.RoleDto;
import io.camunda.optimize.dto.optimize.access.RoleMemberDto;
import io.camunda.security.api.model.authz.EntityType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Roles and their members in the {@code role} index, stored as join parents and children. Writes
 * refresh the index, so a change is visible to the next access check.
 */
public interface RoleRepository {

  Optional<RoleDto> get(String roleId);

  List<RoleDto> getAll();

  /** Inserts the role, or replaces it when one with the same id exists; members are kept. */
  void upsert(RoleDto role);

  /** Deletes the role and all its members; a no-op when the role does not exist. */
  void delete(String roleId);

  List<RoleMemberDto> getMembers(String roleId);

  /** Adds the member to the role; a no-op when it is already a member. */
  void addMember(String roleId, RoleMemberDto member);

  /** Removes the member from the role; a no-op when it is not a member. */
  void removeMember(String roleId, RoleMemberDto member);

  /** Returns the ids of the roles that any of the given members belongs to. */
  Set<String> getRoleIdsOfMembers(EntityType memberType, Collection<String> memberIds);
}
