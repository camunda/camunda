/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupClientStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupUserStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleClientStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleGroupStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleUserStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantClientStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantGroupStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantUserStrictContract;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.entities.TenantMemberEntity;
import java.util.List;

/** Contract adaptation for membership entities (single-field wrappers). */
public final class MemberContractAdapter {

  private MemberContractAdapter() {}

  // Group members

  public static List<GeneratedGroupUserStrictContract> toGroupUsers(
      final List<GroupMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toGroupUser).toList();
  }

  public static GeneratedGroupUserStrictContract toGroupUser(final GroupMemberEntity entity) {
    return new GeneratedGroupUserStrictContract(entity.id());
  }

  public static List<GeneratedGroupClientStrictContract> toGroupClients(
      final List<GroupMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toGroupClient).toList();
  }

  public static GeneratedGroupClientStrictContract toGroupClient(final GroupMemberEntity entity) {
    return new GeneratedGroupClientStrictContract(entity.id());
  }

  // Tenant members

  public static List<GeneratedTenantUserStrictContract> toTenantUsers(
      final List<TenantMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toTenantUser).toList();
  }

  public static GeneratedTenantUserStrictContract toTenantUser(final TenantMemberEntity entity) {
    return new GeneratedTenantUserStrictContract(entity.id());
  }

  public static List<GeneratedTenantClientStrictContract> toTenantClients(
      final List<TenantMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toTenantClient).toList();
  }

  public static GeneratedTenantClientStrictContract toTenantClient(
      final TenantMemberEntity entity) {
    return new GeneratedTenantClientStrictContract(entity.id());
  }

  public static List<GeneratedTenantGroupStrictContract> toTenantGroups(
      final List<TenantMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toTenantGroup).toList();
  }

  public static GeneratedTenantGroupStrictContract toTenantGroup(final TenantMemberEntity entity) {
    return new GeneratedTenantGroupStrictContract(entity.id());
  }

  // Role members

  public static List<GeneratedRoleGroupStrictContract> toRoleGroups(
      final List<RoleMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toRoleGroup).toList();
  }

  public static GeneratedRoleGroupStrictContract toRoleGroup(final RoleMemberEntity entity) {
    return new GeneratedRoleGroupStrictContract(entity.id());
  }

  public static List<GeneratedRoleUserStrictContract> toRoleUsers(
      final List<RoleMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toRoleUser).toList();
  }

  public static GeneratedRoleUserStrictContract toRoleUser(final RoleMemberEntity entity) {
    return new GeneratedRoleUserStrictContract(entity.id());
  }

  public static List<GeneratedRoleClientStrictContract> toRoleClients(
      final List<RoleMemberEntity> entities) {
    return entities.stream().map(MemberContractAdapter::toRoleClient).toList();
  }

  public static GeneratedRoleClientStrictContract toRoleClient(final RoleMemberEntity entity) {
    return new GeneratedRoleClientStrictContract(entity.id());
  }
}
