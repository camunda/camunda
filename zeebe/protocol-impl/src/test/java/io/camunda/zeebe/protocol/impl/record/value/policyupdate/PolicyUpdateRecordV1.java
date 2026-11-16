/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.policyupdate;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecordV1;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecordV1;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecordV1;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecordV1;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecordV1;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PolicyAuthorizationDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A policy update. On an {@code APPEND_CHUNK} command it carries one chunk: each section is an
 * array property that holds the entries of that section, and the sections are the upserts and
 * deletions of the Hub change set, in the apply order defined by the change set contract. The other
 * intents leave the sections empty and carry only the update's identifiers.
 */
/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class PolicyUpdateRecordV1 extends UnifiedRecordValue
    implements PolicyUpdateRecordValue {

  private final StringProperty policyIdProp = new StringProperty("policyId", "");
  private final LongProperty policyVersionProp = new LongProperty("policyVersion", 0);
  private final LongProperty basePolicyVersionProp = new LongProperty("basePolicyVersion", 0);
  private final LongProperty seqNoProp = new LongProperty("seqNo", 0);
  private final StringProperty updateIdProp = new StringProperty("updateId", "");
  private final IntegerProperty chunkIndexProp = new IntegerProperty("chunkIndex", -1);
  private final IntegerProperty chunkCountProp = new IntegerProperty("chunkCount", -1);

  private final ArrayProperty<TenantRecordV1> tenantsProp =
      new ArrayProperty<>("tenants", TenantRecordV1::new);
  private final ArrayProperty<RoleRecordV1> rolesProp =
      new ArrayProperty<>("roles", RoleRecordV1::new);
  private final ArrayProperty<GroupRecordV1> groupsProp =
      new ArrayProperty<>("groups", GroupRecordV1::new);
  private final ArrayProperty<MappingRuleRecordV1> mappingRulesProp =
      new ArrayProperty<>("mappingRules", MappingRuleRecordV1::new);
  private final ArrayProperty<PolicyMembershipEntry> membershipsProp =
      new ArrayProperty<>("memberships", PolicyMembershipEntry::new);
  private final ArrayProperty<AuthorizationRecordV1> authorizationsProp =
      new ArrayProperty<>("authorizations", AuthorizationRecordV1::new);
  private final ArrayProperty<PolicyDeleteEntry> deletesProp =
      new ArrayProperty<>("deletes", PolicyDeleteEntry::new);
  private final ArrayProperty<PolicyMembershipEntry> membershipDeletesProp =
      new ArrayProperty<>("membershipDeletes", PolicyMembershipEntry::new);
  private final ArrayProperty<PolicyAuthorizationDeleteEntry> authorizationDeletesProp =
      new ArrayProperty<>("authorizationDeletes", PolicyAuthorizationDeleteEntry::new);

  private final StringProperty errorMessageProp = new StringProperty("errorMessage", "");

  public PolicyUpdateRecordV1() {
    super(17);
    declareProperty(policyIdProp)
        .declareProperty(policyVersionProp)
        .declareProperty(basePolicyVersionProp)
        .declareProperty(seqNoProp)
        .declareProperty(updateIdProp)
        .declareProperty(chunkIndexProp)
        .declareProperty(chunkCountProp)
        .declareProperty(tenantsProp)
        .declareProperty(rolesProp)
        .declareProperty(groupsProp)
        .declareProperty(mappingRulesProp)
        .declareProperty(membershipsProp)
        .declareProperty(authorizationsProp)
        .declareProperty(deletesProp)
        .declareProperty(membershipDeletesProp)
        .declareProperty(authorizationDeletesProp)
        .declareProperty(errorMessageProp);
  }

  @Override
  public String getPolicyId() {
    return BufferUtil.bufferAsString(policyIdProp.getValue());
  }

  public PolicyUpdateRecordV1 setPolicyId(final String policyId) {
    policyIdProp.setValue(policyId);
    return this;
  }

  @Override
  public long getPolicyVersion() {
    return policyVersionProp.getValue();
  }

  public PolicyUpdateRecordV1 setPolicyVersion(final long policyVersion) {
    policyVersionProp.setValue(policyVersion);
    return this;
  }

  @Override
  public long getBasePolicyVersion() {
    return basePolicyVersionProp.getValue();
  }

  public PolicyUpdateRecordV1 setBasePolicyVersion(final long basePolicyVersion) {
    basePolicyVersionProp.setValue(basePolicyVersion);
    return this;
  }

  @Override
  public long getSeqNo() {
    return seqNoProp.getValue();
  }

  public PolicyUpdateRecordV1 setSeqNo(final long seqNo) {
    seqNoProp.setValue(seqNo);
    return this;
  }

  @Override
  public String getUpdateId() {
    return BufferUtil.bufferAsString(updateIdProp.getValue());
  }

  public PolicyUpdateRecordV1 setUpdateId(final String updateId) {
    updateIdProp.setValue(updateId);
    return this;
  }

  @Override
  public int getChunkIndex() {
    return chunkIndexProp.getValue();
  }

  public PolicyUpdateRecordV1 setChunkIndex(final int chunkIndex) {
    chunkIndexProp.setValue(chunkIndex);
    return this;
  }

  @Override
  public int getChunkCount() {
    return chunkCountProp.getValue();
  }

  public PolicyUpdateRecordV1 setChunkCount(final int chunkCount) {
    chunkCountProp.setValue(chunkCount);
    return this;
  }

  @Override
  public List<TenantRecordValue> getTenants() {
    return tenantsProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecordV1 setTenants(final Collection<TenantRecordValue> tenants) {
    tenantsProp.reset();
    tenants.forEach(tenant -> copyTenant(tenantsProp.add(), tenant));
    return this;
  }

  @Override
  public List<RoleRecordValue> getRoles() {
    return rolesProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecordV1 setRoles(final Collection<RoleRecordValue> roles) {
    rolesProp.reset();
    roles.forEach(role -> copyRole(rolesProp.add(), role));
    return this;
  }

  @Override
  public List<GroupRecordValue> getGroups() {
    return groupsProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecordV1 setGroups(final Collection<GroupRecordValue> groups) {
    groupsProp.reset();
    groups.forEach(group -> copyGroup(groupsProp.add(), group));
    return this;
  }

  @Override
  public List<MappingRuleRecordValue> getMappingRules() {
    return mappingRulesProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecordV1 setMappingRules(
      final Collection<MappingRuleRecordValue> mappingRules) {
    mappingRulesProp.reset();
    mappingRules.forEach(rule -> copyMappingRule(mappingRulesProp.add(), rule));
    return this;
  }

  @Override
  public List<PolicyMembershipEntryValue> getMemberships() {
    return membershipsProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecordV1 setMemberships(final Collection<PolicyMembershipEntryValue> entries) {
    membershipsProp.reset();
    entries.forEach(entry -> copyMembership(membershipsProp.add(), entry));
    return this;
  }

  @Override
  public List<AuthorizationRecordValue> getAuthorizations() {
    return authorizationsProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecordV1 setAuthorizations(
      final Collection<AuthorizationRecordValue> authorizations) {
    authorizationsProp.reset();
    authorizations.forEach(auth -> copyAuthorization(authorizationsProp.add(), auth));
    return this;
  }

  @Override
  public List<PolicyDeleteEntryValue> getDeletes() {
    return deletesProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecordV1 setDeletes(final Collection<PolicyDeleteEntryValue> deletes) {
    deletesProp.reset();
    deletes.forEach(
        delete -> deletesProp.add().setEntityType(delete.getEntityType()).setId(delete.getId()));
    return this;
  }

  @Override
  public List<PolicyMembershipEntryValue> getMembershipDeletes() {
    return membershipDeletesProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecordV1 setMembershipDeletes(
      final Collection<PolicyMembershipEntryValue> entries) {
    membershipDeletesProp.reset();
    entries.forEach(entry -> copyMembership(membershipDeletesProp.add(), entry));
    return this;
  }

  @Override
  public List<PolicyAuthorizationDeleteEntryValue> getAuthorizationDeletes() {
    return authorizationDeletesProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecordV1 setAuthorizationDeletes(
      final Collection<PolicyAuthorizationDeleteEntryValue> deletes) {
    authorizationDeletesProp.reset();
    deletes.forEach(delete -> copyAuthorizationDelete(authorizationDeletesProp.add(), delete));
    return this;
  }

  @Override
  public String getErrorMessage() {
    return BufferUtil.bufferAsString(errorMessageProp.getValue());
  }

  public PolicyUpdateRecordV1 setErrorMessage(final String errorMessage) {
    errorMessageProp.setValue(errorMessage);
    return this;
  }

  private static void copyTenant(final TenantRecordV1 target, final TenantRecordValue source) {
    target
        .setTenantKey(source.getTenantKey())
        .setTenantId(source.getTenantId())
        .setName(source.getName())
        .setDescription(source.getDescription())
        .setEntityId(source.getEntityId())
        .setEntityType(source.getEntityType());
  }

  private static void copyRole(final RoleRecordV1 target, final RoleRecordValue source) {
    target
        .setRoleKey(source.getRoleKey())
        .setRoleId(source.getRoleId())
        .setName(source.getName())
        .setDescription(source.getDescription())
        .setEntityId(source.getEntityId())
        .setEntityType(source.getEntityType());
  }

  private static void copyGroup(final GroupRecordV1 target, final GroupRecordValue source) {
    target
        .setGroupKey(source.getGroupKey())
        .setGroupId(source.getGroupId())
        .setName(source.getName())
        .setDescription(source.getDescription())
        .setEntityId(source.getEntityId())
        .setEntityType(source.getEntityType());
  }

  private static void copyMappingRule(
      final MappingRuleRecordV1 target, final MappingRuleRecordValue source) {
    target
        .setMappingRuleKey(source.getMappingRuleKey())
        .setMappingRuleId(source.getMappingRuleId())
        .setName(source.getName())
        .setClaimName(source.getClaimName())
        .setClaimValue(source.getClaimValue());
  }

  private static void copyMembership(
      final PolicyMembershipEntry target, final PolicyMembershipEntryValue source) {
    target
        .setTargetType(source.getTargetType())
        .setTargetId(source.getTargetId())
        .setMemberType(source.getMemberType())
        .setMemberId(source.getMemberId());
  }

  private static void copyAuthorizationDelete(
      final PolicyAuthorizationDeleteEntry target,
      final PolicyAuthorizationDeleteEntryValue source) {
    target
        .setOwnerType(source.getOwnerType())
        .setOwnerId(source.getOwnerId())
        .setResourceType(source.getResourceType())
        .setResourceMatcher(source.getResourceMatcher())
        .setResourceId(source.getResourceId())
        .setResourcePropertyName(source.getResourcePropertyName());
  }

  private static void copyAuthorization(
      final AuthorizationRecordV1 target, final AuthorizationRecordValue source) {
    target
        .setAuthorizationKey(source.getAuthorizationKey())
        .setOwnerId(source.getOwnerId())
        .setOwnerType(source.getOwnerType())
        .setResourceMatcher(source.getResourceMatcher())
        .setResourceId(source.getResourceId())
        .setResourcePropertyName(source.getResourcePropertyName())
        .setResourceType(source.getResourceType())
        .setPermissionTypes(source.getPermissionTypes());
  }
}
