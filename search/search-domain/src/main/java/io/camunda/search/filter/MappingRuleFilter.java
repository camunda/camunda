/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Set;

public record MappingRuleFilter(
    String mappingRuleId,
    Long mappingRuleKey,
    String claimName,
    List<String> claimNames,
    String claimValue,
    String name,
    List<Claim> claims,
    String tenantId,
    Set<String> mappingRuleIds,
    String groupId,
    String roleId)
    implements FilterBase {

  public MappingRuleFilter.Builder toBuilder() {
    return new Builder()
        .mappingRuleId(mappingRuleId)
        .mappingRuleKey(mappingRuleKey)
        .claimName(claimName)
        .claimNames(claimNames)
        .claimValue(claimValue)
        .name(name)
        .claims(claims)
        .tenantId(tenantId)
        .mappingRuleIds(mappingRuleIds)
        .groupId(groupId)
        .roleId(roleId);
  }

  public static final class Builder implements ObjectBuilder<MappingRuleFilter> {
    private String mappingRuleId;
    private Set<String> mappingRuleIds;
    private Long mappingRuleKey;
    private String claimName;
    private List<String> claimNames;
    private String claimValue;
    private String name;
    private List<Claim> claims;
    private String tenantId;
    private String groupId;
    private String roleId;

    public Builder mappingRuleId(final String value) {
      mappingRuleId = value;
      return this;
    }

    public Builder mappingRuleKey(final Long value) {
      mappingRuleKey = value;
      return this;
    }

    public Builder claimName(final String value) {
      claimName = value;
      return this;
    }

    public Builder claimNames(final List<String> values) {
      claimNames = addValuesToList(claimNames, values);
      return this;
    }

    public Builder claimValue(final String value) {
      claimValue = value;
      return this;
    }

    public Builder name(final String value) {
      name = value;
      return this;
    }

    public Builder claims(final List<Claim> claims) {
      this.claims = claims;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder mappingRuleIds(final Set<String> mappingRuleIds) {
      this.mappingRuleIds = mappingRuleIds;
      return this;
    }

    public Builder groupId(final String groupId) {
      this.groupId = groupId;
      return this;
    }

    public Builder roleId(final String roleId) {
      this.roleId = roleId;
      return this;
    }

    @Override
    public MappingRuleFilter build() {
      return new MappingRuleFilter(
          mappingRuleId,
          mappingRuleKey,
          claimName,
          claimNames,
          claimValue,
          name,
          claims,
          tenantId,
          mappingRuleIds,
          groupId,
          roleId);
    }
  }

  public record Claim(String name, String value) {}
}
