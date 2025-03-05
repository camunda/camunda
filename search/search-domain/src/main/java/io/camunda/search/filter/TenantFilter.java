/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Set;
import java.util.function.Function;

public record TenantFilter(
    Long key,
    String tenantId,
    String name,
    String joinParentId,
    EntityType entityType,
    Set<String> memberIds)
    implements FilterBase {

  public static TenantFilter of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<TenantFilter> {

    private Long key;
    private String tenantId;
    private String name;
    private String joinParentId;
    private EntityType entityType;
    private Set<String> memberIds;

    public Builder key(final Long value) {
      key = value;
      return this;
    }

    public Builder tenantId(final String value) {
      tenantId = value;
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

    public Builder entityType(final EntityType value) {
      entityType = value;
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

    @Override
    public TenantFilter build() {
      return new TenantFilter(key, tenantId, name, joinParentId, entityType, memberIds);
    }
  }
}
