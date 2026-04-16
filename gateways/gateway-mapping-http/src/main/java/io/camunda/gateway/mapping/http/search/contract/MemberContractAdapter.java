/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.protocol.model.GroupClient;
import io.camunda.gateway.protocol.model.GroupUser;
import io.camunda.gateway.protocol.model.RoleClient;
import io.camunda.gateway.protocol.model.RoleGroup;
import io.camunda.gateway.protocol.model.RoleUser;
import io.camunda.gateway.protocol.model.TenantClient;
import io.camunda.gateway.protocol.model.TenantGroup;
import io.camunda.gateway.protocol.model.TenantUser;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.entities.TenantMemberEntity;
import java.util.List;

/** Contract adaptation for membership entities (single-field wrappers). */
public final class MemberContractAdapter {

  private MemberContractAdapter() {}

  // Group members

  public static List<GroupUser> toGroupUsers(final List<GroupMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toGroupUser).toList();
  }

  public static GroupUser toGroupUser(final GroupMemberEntity entity) {
    return new GroupUser().username(entity.id());
  }

  public static List<GroupClient> toGroupClients(final List<GroupMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toGroupClient).toList();
  }

  public static GroupClient toGroupClient(final GroupMemberEntity entity) {
    return new GroupClient().clientId(entity.id());
  }

  // Tenant members

  public static List<TenantUser> toTenantUsers(final List<TenantMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toTenantUser).toList();
  }

  public static TenantUser toTenantUser(final TenantMemberEntity entity) {
    return new TenantUser().username(entity.id());
  }

  public static List<TenantClient> toTenantClients(final List<TenantMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toTenantClient).toList();
  }

  public static TenantClient toTenantClient(final TenantMemberEntity entity) {
    return new TenantClient().clientId(entity.id());
  }

  public static List<TenantGroup> toTenantGroups(final List<TenantMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toTenantGroup).toList();
  }

  public static TenantGroup toTenantGroup(final TenantMemberEntity entity) {
    return new TenantGroup().groupId(entity.id());
  }

  // Role members

  public static List<RoleGroup> toRoleGroups(final List<RoleMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toRoleGroup).toList();
  }

  public static RoleGroup toRoleGroup(final RoleMemberEntity entity) {
    return new RoleGroup().groupId(entity.id());
  }

  public static List<RoleUser> toRoleUsers(final List<RoleMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toRoleUser).toList();
  }

  public static RoleUser toRoleUser(final RoleMemberEntity entity) {
    return new RoleUser().username(entity.id());
  }

  public static List<RoleClient> toRoleClients(final List<RoleMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toRoleClient).toList();
  }

  public static RoleClient toRoleClient(final RoleMemberEntity entity) {
    return new RoleClient().clientId(entity.id());
  }
}
