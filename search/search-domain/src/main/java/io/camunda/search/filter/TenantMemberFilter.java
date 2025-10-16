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
import java.util.function.Function;

public record TenantMemberFilter(String tenantId, EntityType entityType) implements FilterBase {

  public static TenantMemberFilter of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public Builder toBuilder() {
    return new Builder().tenantId(tenantId).memberType(entityType);
  }

  public static final class Builder implements ObjectBuilder<TenantMemberFilter> {

    private String tenantId;
    private EntityType entityType;

    public Builder tenantId(final String value) {
      tenantId = value;
      return this;
    }

    public Builder memberType(final EntityType value) {
      entityType = value;
      return this;
    }

    @Override
    public TenantMemberFilter build() {
      return new TenantMemberFilter(tenantId, entityType);
    }
  }
}
