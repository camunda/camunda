/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.generated.GroupClientContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GroupUserContract;
import io.camunda.gateway.mapping.http.search.contract.generated.RoleClientContract;
import io.camunda.gateway.mapping.http.search.contract.generated.RoleGroupContract;
import io.camunda.gateway.mapping.http.search.contract.generated.RoleUserContract;
import io.camunda.gateway.mapping.http.search.contract.generated.TenantClientContract;
import io.camunda.gateway.mapping.http.search.contract.generated.TenantGroupContract;
import io.camunda.gateway.mapping.http.search.contract.generated.TenantUserContract;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.entities.TenantMemberEntity;
import java.util.List;

/** Contract adaptation for membership entities (single-field wrappers). */
public final class MemberContractAdapter {

  private MemberContractAdapter() {}

  // Group members

  public static List<GroupUserContract> toGroupUsers(final List<GroupMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toGroupUser).toList();
  }

  public static GroupUserContract toGroupUser(final GroupMemberEntity entity) {
    return new GroupUserContract(entity.id());
  }

  public static List<GroupClientContract> toGroupClients(final List<GroupMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toGroupClient).toList();
  }

  public static GroupClientContract toGroupClient(final GroupMemberEntity entity) {
    return new GroupClientContract(entity.id());
  }

  // Tenant members

  public static List<TenantUserContract> toTenantUsers(final List<TenantMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toTenantUser).toList();
  }

  public static TenantUserContract toTenantUser(final TenantMemberEntity entity) {
    return new TenantUserContract(entity.id());
  }

  public static List<TenantClientContract> toTenantClients(
      final List<TenantMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toTenantClient).toList();
  }

  public static TenantClientContract toTenantClient(final TenantMemberEntity entity) {
    return new TenantClientContract(entity.id());
  }

  public static List<TenantGroupContract> toTenantGroups(final List<TenantMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toTenantGroup).toList();
  }

  public static TenantGroupContract toTenantGroup(final TenantMemberEntity entity) {
    return new TenantGroupContract(entity.id());
  }

  // Role members

  public static List<RoleGroupContract> toRoleGroups(final List<RoleMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toRoleGroup).toList();
  }

  public static RoleGroupContract toRoleGroup(final RoleMemberEntity entity) {
    return new RoleGroupContract(entity.id());
  }

  public static List<RoleUserContract> toRoleUsers(final List<RoleMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toRoleUser).toList();
  }

  public static RoleUserContract toRoleUser(final RoleMemberEntity entity) {
    return new RoleUserContract(entity.id());
  }

  public static List<RoleClientContract> toRoleClients(final List<RoleMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toRoleClient).toList();
  }

  public static RoleClientContract toRoleClient(final RoleMemberEntity entity) {
    return new RoleClientContract(entity.id());
  }
}
