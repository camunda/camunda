/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecordV1;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecordV1;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecordV1;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.IdentitySetupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class IdentitySetupRecordV1 extends UnifiedRecordValue
    implements IdentitySetupRecordValue {

  private final ArrayProperty<RoleRecordV1> rolesProp =
      new ArrayProperty<>("roles", RoleRecordV1::new);
  private final ArrayProperty<RoleRecordV1> roleMembersProp =
      new ArrayProperty<>("roleMembers", RoleRecordV1::new);
  private final ArrayProperty<UserRecordV1> usersProp =
      new ArrayProperty<>("users", UserRecordV1::new);
  private final ObjectProperty<TenantRecordV1> defaultTenantProp =
      new ObjectProperty<>("defaultTenant", new TenantRecordV1());
  private final ArrayProperty<TenantRecordV1> tenantsProp =
      new ArrayProperty<>("tenants", TenantRecordV1::new);
  private final ArrayProperty<TenantRecordV1> tenantMembersProp =
      new ArrayProperty<>("tenantMembers", TenantRecordV1::new);
  private final ArrayProperty<MappingRuleRecordV1> mappingRulesProp =
      new ArrayProperty<>("mappingRules", MappingRuleRecordV1::new);
  private final ArrayProperty<AuthorizationRecordV1> authorizationsProp =
      new ArrayProperty<>("authorizations", AuthorizationRecordV1::new);
  private final ArrayProperty<GroupRecordV1> groupsProp =
      new ArrayProperty<>("groups", GroupRecordV1::new);
  private final ArrayProperty<GroupRecordV1> groupMembersProp =
      new ArrayProperty<>("groupMembers", GroupRecordV1::new);

  public IdentitySetupRecordV1() {
    super(10);
    declareProperty(rolesProp)
        .declareProperty(roleMembersProp)
        .declareProperty(usersProp)
        .declareProperty(defaultTenantProp)
        .declareProperty(tenantsProp)
        .declareProperty(tenantMembersProp)
        .declareProperty(mappingRulesProp)
        .declareProperty(authorizationsProp)
        .declareProperty(groupsProp)
        .declareProperty(groupMembersProp);
  }

  @Override
  public Collection<RoleRecordValue> getRoles() {
    return rolesProp.stream().map(RoleRecordValue.class::cast).collect(Collectors.toList());
  }

  @Override
  public Collection<RoleRecordValue> getRoleMembers() {
    return roleMembersProp.stream().map(RoleRecordValue.class::cast).collect(Collectors.toList());
  }

  @Override
  public List<UserRecordValue> getUsers() {
    return usersProp.stream().map(UserRecordValue.class::cast).toList();
  }

  @Override
  public TenantRecordV1 getDefaultTenant() {
    return defaultTenantProp.getValue();
  }

  @Override
  public List<TenantRecordValue> getTenants() {
    return tenantsProp.stream().map(TenantRecordValue.class::cast).toList();
  }

  @Override
  public Collection<TenantRecordValue> getTenantMembers() {
    return tenantMembersProp.stream()
        .map(TenantRecordValue.class::cast)
        .collect(Collectors.toList());
  }

  @Override
  public List<MappingRuleRecordValue> getMappingRules() {
    return mappingRulesProp.stream().map(MappingRuleRecordValue.class::cast).toList();
  }

  @Override
  public Collection<AuthorizationRecordValue> getAuthorizations() {
    return authorizationsProp.stream()
        .map(AuthorizationRecordValue.class::cast)
        .collect(Collectors.toList());
  }

  @Override
  public Collection<GroupRecordValue> getGroups() {
    return groupsProp.stream().map(GroupRecordValue.class::cast).collect(Collectors.toList());
  }

  @Override
  public Collection<GroupRecordValue> getGroupMembers() {
    return groupMembersProp.stream().map(GroupRecordValue.class::cast).collect(Collectors.toList());
  }

  public IdentitySetupRecordV1 setDefaultTenant(final TenantRecordV1 tenant) {
    defaultTenantProp.getValue().copyFrom(tenant);
    return this;
  }

  public IdentitySetupRecordV1 addTenant(final TenantRecordV1 tenant) {
    tenantsProp.add().copyFrom(tenant);
    return this;
  }

  public IdentitySetupRecordV1 addTenantMember(final TenantRecordV1 tenant) {
    tenantMembersProp.add().copyFrom(tenant);
    return this;
  }

  public IdentitySetupRecordV1 addRole(final RoleRecordV1 role) {
    rolesProp.add().copyFrom(role);
    return this;
  }

  public IdentitySetupRecordV1 addRoleMember(final RoleRecordV1 role) {
    roleMembersProp.add().copyFrom(role);
    return this;
  }

  public IdentitySetupRecordV1 addUser(final UserRecordV1 user) {
    usersProp.add().copyFrom(user);
    return this;
  }

  public IdentitySetupRecordV1 addMappingRule(final MappingRuleRecordV1 mappingRule) {
    mappingRulesProp.add().copyFrom(mappingRule);
    return this;
  }

  public IdentitySetupRecordV1 addAuthorization(final AuthorizationRecordV1 authorization) {
    authorizationsProp.add().copyFrom(authorization);
    return this;
  }

  public IdentitySetupRecordV1 addGroup(final GroupRecordV1 group) {
    groupsProp.add().copyFrom(group);
    return this;
  }

  public IdentitySetupRecordV1 addGroupMember(final GroupRecordV1 groupMember) {
    groupMembersProp.add().copyFrom(groupMember);
    return this;
  }
}
