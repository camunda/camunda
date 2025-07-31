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

import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public record TenantFilter(
    Long key,
    List<String> tenantIds,
    String name,
    String joinParentId,
    EntityType entityType,
    Set<String> memberIds,
    EntityType childMemberType,
    Map<EntityType, Set<String>> memberIdsByType)
    implements FilterBase {

  public static TenantFilter of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public Builder toBuilder() {
    return new Builder()
        .key(key)
        .tenantIds(tenantIds)
        .name(name)
        .joinParentId(joinParentId)
        .memberType(entityType)
        .memberIds(memberIds)
        .childMemberType(childMemberType)
        .memberIdsByType(memberIdsByType);
  }

  public static final class Builder implements ObjectBuilder<TenantFilter> {

    private Long key;
    private List<String> tenantIds;
    private String name;
    private String joinParentId;
    private EntityType entityType;
    private Set<String> memberIds;
    private EntityType childMemberType;
    private Map<EntityType, Set<String>> memberIdsByType;

    public Builder key(final Long value) {
      key = value;
      return this;
    }

    public Builder tenantId(final String value, final String... values) {
      return tenantIds(collectValues(value, values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder name(final String value) {
      name = value;
      return this;
    }

    public Builder joinParentId(final String value) {
      joinParentId = value;
      return this;
    }

    public Builder memberType(final EntityType value) {
      entityType = value;
      return this;
    }

    public Builder memberIds(final Set<String> value) {
      memberIds = value;
      return this;
    }

    public Builder childMemberType(final EntityType value) {
      childMemberType = value;
      return this;
    }

    public Builder memberIdsByType(final Map<EntityType, Set<String>> value) {
      memberIdsByType = value;
      return this;
    }

    @Override
    public TenantFilter build() {
      return new TenantFilter(
          key,
          tenantIds,
          name,
          joinParentId,
          entityType,
          memberIds,
          childMemberType,
          memberIdsByType);
    }
  }
}
