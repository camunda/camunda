/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.protocol.model.GroupClientResult;
import io.camunda.gateway.protocol.model.GroupUserResult;
import io.camunda.gateway.protocol.model.RoleClientResult;
import io.camunda.gateway.protocol.model.RoleGroupResult;
import io.camunda.gateway.protocol.model.RoleUserResult;
import io.camunda.gateway.protocol.model.TenantClientResult;
import io.camunda.gateway.protocol.model.TenantGroupResult;
import io.camunda.gateway.protocol.model.TenantUserResult;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.entities.TenantMemberEntity;
import java.util.List;

/** Contract adaptation for membership entities (single-field wrappers). */
public final class MemberContractAdapter {

  private MemberContractAdapter() {}

  // Group members

  public static List<GroupUserResult> toGroupUsers(final List<GroupMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toGroupUser).toList();
  }

  public static GroupUserResult toGroupUser(final GroupMemberEntity entity) {
    return new GroupUserResult().username(entity.id());
  }

  public static List<GroupClientResult> toGroupClients(final List<GroupMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toGroupClient).toList();
  }

  public static GroupClientResult toGroupClient(final GroupMemberEntity entity) {
    return new GroupClientResult().clientId(entity.id());
  }

  // Tenant members

  public static List<TenantUserResult> toTenantUsers(final List<TenantMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toTenantUser).toList();
  }

  public static TenantUserResult toTenantUser(final TenantMemberEntity entity) {
    return new TenantUserResult().username(entity.id());
  }

  public static List<TenantClientResult> toTenantClients(final List<TenantMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toTenantClient).toList();
  }

  public static TenantClientResult toTenantClient(final TenantMemberEntity entity) {
    return new TenantClientResult().clientId(entity.id());
  }

  public static List<TenantGroupResult> toTenantGroups(final List<TenantMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toTenantGroup).toList();
  }

  public static TenantGroupResult toTenantGroup(final TenantMemberEntity entity) {
    return new TenantGroupResult().groupId(entity.id());
  }

  // Role members

  public static List<RoleGroupResult> toRoleGroups(final List<RoleMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toRoleGroup).toList();
  }

  public static RoleGroupResult toRoleGroup(final RoleMemberEntity entity) {
    return new RoleGroupResult().groupId(entity.id());
  }

  public static List<RoleUserResult> toRoleUsers(final List<RoleMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toRoleUser).toList();
  }

  public static RoleUserResult toRoleUser(final RoleMemberEntity entity) {
    return new RoleUserResult().username(entity.id());
  }

  public static List<RoleClientResult> toRoleClients(final List<RoleMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toRoleClient).toList();
  }

  public static RoleClientResult toRoleClient(final RoleMemberEntity entity) {
    return new RoleClientResult().clientId(entity.id());
  }
}
