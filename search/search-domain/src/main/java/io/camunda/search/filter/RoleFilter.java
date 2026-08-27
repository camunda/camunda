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

import io.camunda.security.api.model.authz.EntityType;
import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public record RoleFilter(
    List<Operation<String>> roleIdOperations,
    List<Operation<String>> nameOperations,
    String description,
    Set<String> memberIds,
    Set<String> roleIds,
    EntityType childMemberType,
    String tenantId,
    Map<EntityType, Set<String>> memberIdsByType,
    List<RoleFilter> orFilters)
    implements FilterBase {

  public static RoleFilter of(
      final Function<RoleFilter.Builder, RoleFilter.Builder> builderFunction) {
    return builderFunction.apply(new RoleFilter.Builder()).build();
  }

  public Builder toBuilder() {
    return new Builder()
        .roleIdOperations(roleIdOperations)
        .nameOperations(nameOperations)
        .description(description)
        .memberIds(memberIds)
        .roleIds(roleIds)
        .childMemberType(childMemberType)
        .tenantId(tenantId)
        .memberIdsByType(memberIdsByType)
        .orFilters(orFilters);
  }

  public static final class Builder implements ObjectBuilder<RoleFilter> {
    private List<Operation<String>> roleIdOperations;
    private List<Operation<String>> nameOperations;
    private String description;
    private Set<String> memberIds;
    private Set<String> roleIds;
    private EntityType childMemberType;
    private String tenantId;
    private Map<EntityType, Set<String>> memberIdsByType;
    private List<RoleFilter> orFilters;

    public Builder roleIdOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        roleIdOperations = addValuesToList(roleIdOperations, operations);
      }
      return this;
    }

    public Builder roleId(final String value, final String... values) {
      final var vals = FilterUtil.mapDefaultToOperation(value, values);
      if (vals != null) {
        return roleIdOperations(vals);
      }
      return this;
    }

    @SafeVarargs
    public final Builder roleIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return roleIdOperations(collectValues(operation, operations));
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

    public Builder description(final String value) {
      description = value;
      return this;
    }

    public Builder memberIds(final Set<String> value) {
      memberIds = value;
      return this;
    }

    public Builder memberId(final String... values) {
      return memberIds(Set.of(values));
    }

    public Builder roleIds(final Set<String> value) {
      roleIds = value;
      return this;
    }

    public Builder childMemberType(final EntityType value) {
      childMemberType = value;
      return this;
    }

    public Builder tenantId(final String value) {
      tenantId = value;
      return this;
    }

    public Builder memberIdsByType(final Map<EntityType, Set<String>> value) {
      memberIdsByType = value;
      return this;
    }

    public Builder addOrOperation(final RoleFilter orOperation) {
      if (orFilters == null) {
        orFilters = new ArrayList<>();
      }
      orFilters.add(orOperation);
      return this;
    }

    public Builder orFilters(final List<RoleFilter> orFilters) {
      this.orFilters = orFilters;
      return this;
    }

    @Override
    public RoleFilter build() {
      if (memberIds != null && childMemberType == null) {
        throw new IllegalArgumentException("If memberIds is set, childMemberType must be set too");
      }
      return new RoleFilter(
          roleIdOperations,
          nameOperations,
          description,
          memberIds,
          roleIds,
          childMemberType,
          tenantId,
          memberIdsByType,
          orFilters);
    }
  }
}
