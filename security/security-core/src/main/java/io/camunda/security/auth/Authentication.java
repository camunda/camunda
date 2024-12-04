/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static java.util.Collections.unmodifiableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public record Authentication(
    Long authenticatedUserKey,
    List<Long> authenticatedGroupKeys,
    List<String> authenticatedTenantIds,
    Map<String, Object> claims) {

  public static Authentication of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public static final class Builder {

    private Long userKey;
    private final List<Long> groupKeys = new ArrayList<>();
    private final List<String> tenants = new ArrayList<>();
    private Map<String, Object> claims;

    public Builder user(final Long value) {
      userKey = value;
      return this;
    }

    public Builder group(final Long value) {
      return groupKeys(List.of(value));
    }

    public Builder groupKeys(final List<Long> values) {
      if (values != null) {
        groupKeys.addAll(values);
      }
      return this;
    }

    public Builder tenant(final String tenant) {
      return tenants(List.of(tenant));
    }

    public Builder tenants(final List<String> values) {
      if (values != null) {
        tenants.addAll(values);
      }
      return this;
    }

    public Builder claims(final Map<String, Object> value) {
      claims = value;
      return this;
    }

    public Authentication build() {
      return new Authentication(
          userKey, unmodifiableList(groupKeys), unmodifiableList(tenants), claims);
    }
  }
}
