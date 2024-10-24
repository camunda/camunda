/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.ObjectBuilder;

public record RoleFilter(Long roleKey, String name) implements FilterBase {
  public static final class Builder implements ObjectBuilder<RoleFilter> {
    private Long roleKey;
    private String name;

    public Builder roleKey(final Long value) {
      roleKey = value;
      return this;
    }

    public Builder name(final String value) {
      name = value;
      return this;
    }

    @Override
    public RoleFilter build() {
      return new RoleFilter(roleKey, name);
    }
  }
}