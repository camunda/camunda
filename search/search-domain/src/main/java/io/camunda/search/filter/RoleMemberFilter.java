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

public record RoleMemberFilter(String roleId, EntityType memberType) implements FilterBase {

  public static RoleMemberFilter of(
      final Function<RoleMemberFilter.Builder, RoleMemberFilter.Builder> builderFunction) {
    return builderFunction.apply(new RoleMemberFilter.Builder()).build();
  }

  public Builder toBuilder() {
    return new Builder().roleId(roleId).memberType(memberType);
  }

  public static final class Builder implements ObjectBuilder<RoleMemberFilter> {
    private String roleId;
    private EntityType memberType;

    public Builder roleId(final String value) {
      roleId = value;
      return this;
    }

    public Builder memberType(final EntityType value) {
      memberType = value;
      return this;
    }

    @Override
    public RoleMemberFilter build() {
      return new RoleMemberFilter(roleId, memberType);
    }
  }
}
