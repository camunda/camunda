/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;

import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record MappingRuleFilter(
    List<Operation<String>> mappingRuleIdOperations,
    String claimName,
    List<String> claimNames,
    String claimValue,
    List<Operation<String>> nameOperations,
    List<Claim> claims,
    String tenantId,
    Set<String> mappingRuleIds,
    String groupId,
    String roleId,
    List<MappingRuleFilter> orFilters)
    implements FilterBase {

  public MappingRuleFilter.Builder toBuilder() {
    return new Builder()
        .mappingRuleIdOperations(mappingRuleIdOperations)
        .claimName(claimName)
        .claimNames(claimNames)
        .claimValue(claimValue)
        .nameOperations(nameOperations)
        .claims(claims)
        .tenantId(tenantId)
        .mappingRuleIds(mappingRuleIds)
        .groupId(groupId)
        .roleId(roleId)
        .orFilters(orFilters);
  }

  public static final class Builder implements ObjectBuilder<MappingRuleFilter> {
    private List<Operation<String>> mappingRuleIdOperations;
    private Set<String> mappingRuleIds;
    private String claimName;
    private List<String> claimNames;
    private String claimValue;
    private List<Operation<String>> nameOperations;
    private List<Claim> claims;
    private String tenantId;
    private String groupId;
    private String roleId;
    private List<MappingRuleFilter> orFilters;

    public Builder mappingRuleIdOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        mappingRuleIdOperations = addValuesToList(mappingRuleIdOperations, operations);
      }
      return this;
    }

    public Builder mappingRuleId(final String value, final String... values) {
      final var vals = FilterUtil.mapDefaultToOperation(value, values);
      if (vals != null) {
        return mappingRuleIdOperations(vals);
      }
      return this;
    }

    @SafeVarargs
    public final Builder mappingRuleIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return mappingRuleIdOperations(collectValues(operation, operations));
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

    public Builder nameOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        nameOperations = addValuesToList(nameOperations, operations);
      }
      return this;
    }

    public Builder names(final Set<String> value) {
      final var vals = FilterUtil.mapDefaultToOperation(new ArrayList<>(value));
      if (vals != null) {
        return nameOperations(vals);
      }
      return this;
    }

    public Builder names(final String value, final String... values) {
      final var vals = FilterUtil.mapDefaultToOperation(value, values);
      if (vals != null) {
        return nameOperations(vals);
      }
      return this;
    }

    @SafeVarargs
    public final Builder nameOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return nameOperations(collectValues(operation, operations));
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

    public Builder addOrOperation(final MappingRuleFilter orOperation) {
      if (orFilters == null) {
        orFilters = new ArrayList<>();
      }
      orFilters.add(orOperation);
      return this;
    }

    public Builder orFilters(final List<MappingRuleFilter> orFilters) {
      this.orFilters = orFilters;
      return this;
    }

    @Override
    public MappingRuleFilter build() {
      return new MappingRuleFilter(
          mappingRuleIdOperations,
          claimName,
          claimNames,
          claimValue,
          nameOperations,
          claims,
          tenantId,
          mappingRuleIds,
          groupId,
          roleId,
          orFilters);
    }
  }

  public record Claim(String name, String value) {}
}
