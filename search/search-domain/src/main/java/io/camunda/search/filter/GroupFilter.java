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

public record GroupFilter(
    List<Operation<String>> groupIdOperations,
    List<Operation<String>> nameOperations,
    String description,
    Set<String> memberIds,
    String tenantId,
    EntityType childMemberType,
    String roleId,
    Map<EntityType, Set<String>> memberIdsByType,
    List<GroupFilter> orFilters)
    implements FilterBase {

  public static GroupFilter of(
      final Function<GroupFilter.Builder, GroupFilter.Builder> builderFunction) {
    return builderFunction.apply(new GroupFilter.Builder()).build();
  }

  public Builder toBuilder() {
    return new Builder()
        .groupIdOperations(groupIdOperations)
        .nameOperations(nameOperations)
        .description(description)
        .memberIds(memberIds)
        .tenantId(tenantId)
        .childMemberType(childMemberType)
        .roleId(roleId)
        .memberIdsByType(memberIdsByType)
        .orFilters(orFilters);
  }

  public static final class Builder implements ObjectBuilder<GroupFilter> {
    private List<Operation<String>> groupIdOperations;
    private List<Operation<String>> nameOperations;
    private String description;
    private Set<String> memberIds;
    private String tenantId;
    private EntityType childMemberType;
    private String roleId;
    private Map<EntityType, Set<String>> memberIdsByType;
    private List<GroupFilter> orFilters;

    public Builder groupIdOperations(final List<Operation<String>> operations) {
      if (operations != null) {
        groupIdOperations = addValuesToList(groupIdOperations, operations);
      }
      return this;
    }

    public Builder groupIds(final Set<String> value) {
      final var vals = FilterUtil.mapDefaultToOperation(new ArrayList<>(value));
      if (vals != null) {
        return groupIdOperations(vals);
      }
      return this;
    }

    public Builder groupIds(final String value, final String... values) {
      final var vals = FilterUtil.mapDefaultToOperation(value, values);
      if (vals != null) {
        return groupIdOperations(vals);
      }
      return this;
    }

    @SafeVarargs
    public final Builder groupIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return groupIdOperations(collectValues(operation, operations));
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

    public Builder name(final String value, final String... values) {
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

    public Builder memberId(final String value) {
      return memberIds(Set.of(value));
    }

    public Builder memberIds(final Set<String> value) {
      memberIds = value;
      return this;
    }

    public Builder tenantId(final String value) {
      tenantId = value;
      return this;
    }

    public Builder childMemberType(final EntityType value) {
      childMemberType = value;
      return this;
    }

    public Builder roleId(final String value) {
      roleId = value;
      return this;
    }

    public Builder memberIdsByType(final Map<EntityType, Set<String>> value) {
      memberIdsByType = value;
      return this;
    }

    public Builder addOrOperation(final GroupFilter orOperation) {
      if (orFilters == null) {
        orFilters = new ArrayList<>();
      }
      orFilters.add(orOperation);
      return this;
    }

    public Builder orFilters(final List<GroupFilter> orFilters) {
      this.orFilters = orFilters;
      return this;
    }

    @Override
    public GroupFilter build() {
      return new GroupFilter(
          groupIdOperations,
          nameOperations,
          description,
          memberIds,
          tenantId,
          childMemberType,
          roleId,
          memberIdsByType,
          orFilters);
    }
  }
}
